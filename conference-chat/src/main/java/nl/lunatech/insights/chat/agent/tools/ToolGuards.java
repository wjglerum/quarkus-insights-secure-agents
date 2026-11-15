package nl.lunatech.insights.chat.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.insights.chat.agent.GuardrailSwitches;
import nl.lunatech.insights.chat.agent.GuardrailSwitches.Guard;
import nl.lunatech.insights.chat.agent.TurnLog;
import nl.lunatech.insights.chat.agent.TurnLog.Outcome;
import nl.lunatech.insights.chat.agent.TurnLog.ToolCall;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        TurnLog turn = turnLog();
        Map<String, Object> args = parseArgs(request.arguments());

        // tool call guard: a destructive tool needs the user's own ask
        if (switches.on(Guard.TOOL_INTENT)
                && DESTRUCTIVE.containsKey(tool) && !userAskedFor(tool, turn.userPrompt())) {
            LOG.warnf("Tool call guard blocked %s %s", tool, args);
            String reason = refusalText(tool);
            turn.add(new ToolCall(tool, args, Outcome.BLOCKED, reason, null, "tool call guard"));
            return refused(reason);
        }

        ToolExecutionResult result;
        try {
            result = call.get();
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            turn.add(new ToolCall(tool, args, isStepUp(msg) ? Outcome.STEP_UP : Outcome.ERROR, msg, null, null));
            throw e;
        }

        // tool result guard: text addressed to the assistant is removed
        String text = result.resultText();
        // Step-up is only ever an error. A successful result is data: an attendee comment that
        // quotes the step-up error code must not get it past the tool result guard.
        Outcome outcome = !result.isError() ? Outcome.OK : isStepUp(text) ? Outcome.STEP_UP : Outcome.ERROR;
        if (outcome == Outcome.OK && switches.on(Guard.TOOL_OUTPUT)) {
            String cleaned = stripInstructions(text);
            if (!cleaned.equals(text)) {
                LOG.warnf("Tool result guard removed text addressed to the assistant from %s", tool);
                turn.add(new ToolCall(tool, args, outcome, text, cleaned, "tool result guard"));
                return ToolExecutionResult.builder().resultText(cleaned).build();
            }
        }
        turn.add(new ToolCall(tool, args, outcome, text, null, null));
        return result;
    }

    /** True when the user's own message asks for this destructive tool's action. Text in a tool result never counts. */
    static boolean userAskedFor(String tool, String userPrompt) {
        List<String> words = DESTRUCTIVE.getOrDefault(tool, List.of());
        String prompt = userPrompt == null ? "" : userPrompt.toLowerCase(Locale.ROOT);
        return words.stream().anyMatch(prompt::contains);
    }

    /** What the model gets back instead of the tool result when the tool call guard refuses. */
    static String refusalText(String tool) {
        return "Refused by the tool call guard: " + tool + " changes what everyone sees, "
                + "and the user did not ask for it in this message. Do not retry. "
                + "Tell the user what you wanted to do and why, and let them decide.";
    }

    static ToolExecutionResult refused(String reason) {
        return ToolExecutionResult.builder().isError(true).resultText(reason).build();
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

    /**
     * The log of the chat turn this call belongs to. Looked up instead of injected because this
     * bean is application scoped; outside a request (unit tests) a throwaway log with an empty prompt.
     */
    private static TurnLog turnLog() {
        ArcContainer arc = Arc.container();
        return arc != null && arc.requestContext().isActive() ? arc.instance(TurnLog.class).get() : new TurnLog();
    }
}
