package nl.lunatech.insights.chat.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.insights.chat.agent.TurnLog;
import nl.lunatech.insights.chat.agent.TurnLog.Outcome;
import nl.lunatech.insights.chat.agent.TurnLog.ToolCall;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Writes every tool call the agent makes into the {@link TurnLog}, so the chat can show each call
 * as a card and open the step-up popup. Demo scaffolding: it records, it does not guard.
 *
 * <p>The MCP tools have no client-side guards on purpose: conference-mcp checks the token, the
 * roles and step-up, and conference-api checks ownership. The local {@code send_email} tool has
 * no server behind it, so it gets a tool input guardrail instead ({@code OwnAddressOnly}).
 */
@ApplicationScoped
public class ToolCallRecorder {

    @Inject
    ObjectMapper mapper;

    /** Runs an MCP tool call and records its outcome. */
    public ToolExecutionResult run(String tool, ToolExecutionRequest request, Supplier<ToolExecutionResult> call) {
        Map<String, Object> args = parseArgs(request.arguments());
        ToolExecutionResult result;
        try {
            result = call.get();
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            record(new ToolCall(tool, args, isStepUp(msg) ? Outcome.STEP_UP : Outcome.ERROR, msg, null));
            throw e;
        }
        String text = result.resultText();
        // Step-up is only ever an error. A successful result is data, even if it quotes the error code.
        Outcome outcome = !result.isError() ? Outcome.OK : isStepUp(text) ? Outcome.STEP_UP : Outcome.ERROR;
        record(new ToolCall(tool, args, outcome, text, null));
        return result;
    }

    public void record(ToolCall call) {
        turnLog().add(call);
    }

    public Map<String, Object> parseArgs(String json) {
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

    private static boolean isStepUp(String message) {
        return message != null && message.toLowerCase(Locale.ROOT).contains("insufficient_user_authentication");
    }

    /** Looked up per call because this bean is application scoped; outside a request, a throwaway log. */
    private static TurnLog turnLog() {
        ArcContainer arc = Arc.container();
        return arc != null && arc.requestContext().isActive() ? arc.instance(TurnLog.class).get() : new TurnLog();
    }
}
