package nl.lunatech.insights.chat.web;

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
import nl.lunatech.insights.chat.web.ToolDispatcher.ToolResult;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Path("/api/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class ChatResource {

    private static final Logger LOG = Logger.getLogger(ChatResource.class);

    @Inject
    ToolDispatcher dispatcher;

    @Inject
    ScriptedPlanner scripted;

    @Inject
    ConferenceAgent agent;

    @Inject
    GuardrailSwitches switches;

    @Inject
    TurnLog turnLog;

    /** "agent" (default): the {@link ConferenceAgent}. "scripted": never call the model (no wifi). */
    @ConfigProperty(name = "demo.planner", defaultValue = "agent")
    String plannerMode;

    @POST
    @Path("/send")
    public ChatTurn send(ChatRequest req) {
        String prompt = req == null || req.prompt() == null ? "" : req.prompt().trim();
        if (prompt.isEmpty()) {
            return new ChatTurn(prompt, "Type a prompt to get started.", List.of());
        }
        if ("scripted".equalsIgnoreCase(plannerMode)) {
            return scriptedTurn(prompt).orElseGet(() -> new ChatTurn(prompt,
                    "Scripted mode only knows the demo prompts in the sidebar.", List.of()));
        }
        return agentTurn(prompt);
    }

    private ChatTurn agentTurn(String prompt) {
        turnLog.userPrompt(prompt);
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
            // Fall back to the scripted planner only when no tool ran yet: running the prompt
            // again after a tool call could bookmark or cancel twice.
            Optional<ChatTurn> fallback = results.isEmpty() ? scriptedTurn(prompt) : Optional.empty();
            return fallback.orElseGet(() -> new ChatTurn(prompt, "Agent failed: "
                    + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()), results));
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
            case OK -> c.shownToModel() != null
                    ? ToolResult.rewritten(c.tool(), c.args(), dispatcher.parseResult(c.shownToModel()), c.guard())
                    : ToolResult.ok(c.tool(), c.args(), dispatcher.parseResult(c.resultText()));
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

    /** Empty when the prompt is not one of the rehearsed demo prompts. */
    private Optional<ChatTurn> scriptedTurn(String prompt) {
        return scripted.plan(prompt).map(call -> new ChatTurn(prompt,
                "Scripted fallback (LLM unavailable) picked tool " + call.tool() + ".",
                List.of(dispatcher.invoke(call.tool(), call.args()))));
    }

    public record ChatRequest(String prompt) {}

    public record ChatTurn(String prompt, String note, List<ToolResult> results) {}
}
