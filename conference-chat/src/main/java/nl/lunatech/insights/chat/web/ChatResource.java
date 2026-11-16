package nl.lunatech.insights.chat.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.insights.chat.agent.ConferenceAgent;
import nl.lunatech.insights.chat.agent.GuardrailSwitches;
import nl.lunatech.insights.chat.agent.TurnLog;
import nl.lunatech.insights.chat.agent.TurnLog.ToolCall;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/api/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class ChatResource {

    private static final Logger LOG = Logger.getLogger(ChatResource.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    ConferenceAgent agent;

    @Inject
    GuardrailSwitches switches;

    @Inject
    TurnLog turnLog;

    @POST
    @Path("/send")
    public ChatTurn send(ChatRequest req) {
        String prompt = req == null || req.prompt() == null ? "" : req.prompt().trim();
        if (prompt.isEmpty()) {
            return new ChatTurn(prompt, "Type a prompt to get started.", List.of());
        }
        return agentTurn(prompt);
    }

    private ChatTurn agentTurn(String prompt) {
        try {
            String answer = agent.chat(prompt);
            return new ChatTurn(prompt, answer, toResults(turnLog.calls()));
        } catch (RuntimeException e) {
            List<ToolResult> results = new ArrayList<>(toResults(turnLog.calls()));
            Throwable input = causeOfType(e, InputGuardrailException.class);
            if (input != null) {
                results.add(ToolResult.blocked("input guardrail", Map.of("prompt", prompt),
                        message(input), "input guardrail"));
                return new ChatTurn(prompt, "Blocked before the model saw it.", results);
            }
            Throwable output = causeOfType(e, OutputGuardrailException.class);
            if (output != null) {
                results.add(ToolResult.blocked("output guardrail", Map.of(), message(output), "output guardrail"));
                return new ChatTurn(prompt, "The answer was blocked before it reached you.", results);
            }
            if (results.stream().anyMatch(ToolResult::stepUpRequired)) {
                return new ChatTurn(prompt, "This needs a stronger login first.", results);
            }
            LOG.warnf(e, "Agent turn failed");
            return new ChatTurn(prompt, "Agent failed: "
                    + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()), results);
        }
    }

    private List<ToolResult> toResults(List<ToolCall> calls) {
        return calls.stream().map(this::toResult).toList();
    }

    private ToolResult toResult(ToolCall c) {
        return switch (c.outcome()) {
            case BLOCKED -> ToolResult.blocked(c.tool(), c.args(), c.resultText(), c.guard());
            case STEP_UP -> ToolResult.stepUp(c.tool(), c.args());
            case ERROR -> ToolResult.err(c.tool(), c.args(), c.resultText());
            case OK -> ToolResult.ok(c.tool(), c.args(), parseResult(c.resultText()));
        };
    }

    /** Quarkus may wrap the LangChain4j guardrail exception, so walk the causes. */
    private static Throwable causeOfType(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return t;
            }
        }
        return null;
    }

    /** The guard's own message, without the "The guardrail ... failed with this message:" wrapper. */
    private static String message(Throwable t) {
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        int i = msg.indexOf("Guard:");
        if (i > 0) {
            int start = msg.lastIndexOf(' ', i) + 1;
            return msg.substring(start).trim();
        }
        return msg;
    }

    /** Tool results are text; show them as JSON in the chat card when they are JSON. */
    Object parseResult(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String trimmed = raw.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return raw;
        }
        try {
            // The MCP server emits one content block per list element, which the client
            // concatenates; read every JSON value so multi-result tools keep all their items.
            List<Object> values = mapper.readerFor(Object.class).<Object>readValues(trimmed).readAll();
            if (values.isEmpty()) {
                return raw;
            }
            return values.size() == 1 ? values.get(0) : values;
        } catch (Exception e) {
            return raw;
        }
    }

    public record ChatRequest(String prompt) {}

    public record ChatTurn(String prompt, String note, List<ToolResult> results) {}

    /** One card in the chat. {@code guard} names the guardrail that blocked this call, or is null. */
    public record ToolResult(String tool, Map<String, Object> args, Object result,
                             String error, boolean stepUpRequired, String guard) {
        static ToolResult ok(String tool, Map<String, Object> args, Object result) {
            return new ToolResult(tool, args, result, null, false, null);
        }

        static ToolResult err(String tool, Map<String, Object> args, String error) {
            return new ToolResult(tool, args, null, error, false, null);
        }

        static ToolResult stepUp(String tool, Map<String, Object> args) {
            return new ToolResult(tool, args, null,
                    "insufficient_user_authentication: this tool requires step-up MFA", true, null);
        }

        static ToolResult blocked(String tool, Map<String, Object> args, String error, String guard) {
            return new ToolResult(tool, args, null, error, false, guard);
        }
    }
}
