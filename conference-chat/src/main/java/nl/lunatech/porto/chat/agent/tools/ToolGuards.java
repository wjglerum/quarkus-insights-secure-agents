package nl.lunatech.porto.chat.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.porto.chat.agent.GuardrailSwitches;
import nl.lunatech.porto.chat.agent.GuardrailSwitches.Guard;
import nl.lunatech.porto.chat.agent.TurnLog;
import nl.lunatech.porto.chat.agent.TurnLog.Outcome;
import nl.lunatech.porto.chat.agent.TurnLog.ToolCall;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The two guards around every MCP tool call the agent makes.
 *
 * <ul>
 *   <li><b>Tool call guard</b> (OWASP LLM03, excessive agency): a destructive tool only runs when
 *   the user asked for that action in their own message. Text in a tool result can't approve it.
 *   A keyword check is the simplest version of "show the user the exact call and wait for a yes".</li>
 *   <li><b>Tool result guard</b> (OWASP LLM01, indirect injection): text in a tool result that is
 *   addressed to the assistant is removed before the model reads it.</li>
 * </ul>
 *
 * <p>Neither replaces the server: conference-mcp still checks the token, the roles and step-up,
 * and conference-api still checks ownership and writes the audit log.
 */
@ApplicationScoped
public class ToolGuards {

    private static final Logger LOG = Logger.getLogger(ToolGuards.class);

    /** Tools that change what other people see. Each needs the user's own words to run. */
    static final Map<String, List<String>> DESTRUCTIVE = Map.of(
            "cancel_my_session", List.of("cancel", "undo"));

    static final String REMOVED = "[removed by the tool result guard: text addressed to the assistant]";

    private static final Pattern JSON_STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final Pattern ADDRESSED_TO_AGENT = Pattern.compile(
            "(?i)(note to the assistant|to the (ai )?assistant\\b|ignore (all |any )?(previous|prior|earlier) instructions"
                    + "|before you answer|you must (now )?call|\\bcall [a-z]+(_[a-z]+)+\\b)");

    @Inject
    GuardrailSwitches switches;

    @Inject
    ObjectMapper mapper;

    public ToolExecutionResult run(String tool, ToolExecutionRequest request, Supplier<ToolExecutionResult> call) {
        TurnLog log = turnLog();
        Map<String, Object> args = parseArgs(request.arguments());

        if (switches.on(Guard.TOOL_INTENT)) {
            Optional<String> refusal = intentRefusal(tool, log == null ? "" : log.userPrompt());
            if (refusal.isPresent()) {
                LOG.warnf("Tool call guard blocked %s %s", tool, args);
                record(log, new ToolCall(tool, args, Outcome.BLOCKED, refusal.get(), null, "tool call guard"));
                return ToolExecutionResult.builder().isError(true).resultText(refusal.get()).build();
            }
        }

        ToolExecutionResult result;
        try {
            result = call.get();
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            record(log, new ToolCall(tool, args, isStepUp(msg) ? Outcome.STEP_UP : Outcome.ERROR, msg, null, null));
            throw e;
        }

        String text = result.resultText();
        Outcome outcome = isStepUp(text) ? Outcome.STEP_UP : result.isError() ? Outcome.ERROR : Outcome.OK;
        if (outcome == Outcome.OK && switches.on(Guard.TOOL_OUTPUT)) {
            String cleaned = stripInstructions(text);
            if (!cleaned.equals(text)) {
                LOG.warnf("Tool result guard removed text addressed to the assistant from %s", tool);
                record(log, new ToolCall(tool, args, outcome, text, cleaned, "tool result guard"));
                return ToolExecutionResult.builder().resultText(cleaned).build();
            }
        }
        record(log, new ToolCall(tool, args, outcome, text, null, null));
        return result;
    }

    /** Empty when the call may go ahead; otherwise the refusal the model gets back as the tool result. */
    static Optional<String> intentRefusal(String tool, String userPrompt) {
        List<String> words = DESTRUCTIVE.get(tool);
        if (words == null) {
            return Optional.empty();
        }
        String prompt = userPrompt == null ? "" : userPrompt.toLowerCase(Locale.ROOT);
        if (words.stream().anyMatch(prompt::contains)) {
            return Optional.empty();
        }
        return Optional.of("Refused by the tool call guard: " + tool + " changes what everyone sees, "
                + "and the user did not ask for it in this message. Do not retry. "
                + "Tell the user what you wanted to do and why, and let them decide.");
    }

    /** Replaces every JSON string value (or the whole text, if it is not JSON) that is addressed to the assistant. */
    static String stripInstructions(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher m = JSON_STRING.matcher(text);
        StringBuilder out = new StringBuilder();
        boolean anyString = false;
        while (m.find()) {
            anyString = true;
            String value = m.group(1);
            String replacement = ADDRESSED_TO_AGENT.matcher(value).find() ? "\"" + REMOVED + "\"" : m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        if (!anyString && ADDRESSED_TO_AGENT.matcher(text).find()) {
            return REMOVED;
        }
        return out.toString();
    }

    private static boolean isStepUp(String message) {
        return message != null && message.toLowerCase(Locale.ROOT).contains("insufficient_user_authentication");
    }

    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("raw", json);
            return raw;
        }
    }

    private static TurnLog turnLog() {
        if (Arc.container() == null || !Arc.container().requestContext().isActive()) {
            return null;
        }
        InstanceHandle<TurnLog> handle = Arc.container().instance(TurnLog.class);
        return handle.isAvailable() ? handle.get() : null;
    }

    private static void record(TurnLog log, ToolCall call) {
        if (log != null) {
            log.add(call);
        }
    }

    /** For tests: the tools this guard treats as destructive. */
    static Set<String> destructiveTools() {
        return DESTRUCTIVE.keySet();
    }
}
