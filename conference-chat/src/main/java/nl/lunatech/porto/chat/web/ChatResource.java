package nl.lunatech.porto.chat.web;

import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.porto.chat.agent.ConferenceAgent;
import nl.lunatech.porto.chat.agent.GuardrailSwitches;
import nl.lunatech.porto.chat.agent.TurnLog;
import nl.lunatech.porto.chat.agent.TurnLog.ToolCall;
import nl.lunatech.porto.chat.llm.LlmToolPlanner;
import nl.lunatech.porto.chat.llm.ScriptedPlanner;
import nl.lunatech.porto.chat.web.ToolDispatcher.ToolResult;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
    ToolDispatcher dispatcher;

    @Inject
    LlmToolPlanner planner;

    @Inject
    ScriptedPlanner scripted;

    @Inject
    ChatModel chatModel;

    @Inject
    ConferenceAgent agent;

    @Inject
    GuardrailSwitches switches;

    @Inject
    TurnLog turnLog;

    /**
     * "agent" (default): the {@link ConferenceAgent} AI service with guardrails; it may call several
     * tools and reads their results. "llm": the single-tool planner, one tool per prompt, results never go
     * back to the model. "scripted": no model at all. Both model modes fall back to the scripted
     * planner when the model is unreachable.
     */
    @ConfigProperty(name = "demo.planner", defaultValue = "agent")
    String plannerMode;

    @POST
    @Path("/send")
    public ChatTurn send(ChatRequest req) {
        String prompt = req == null || req.prompt() == null ? "" : req.prompt().trim();
        if (prompt.isEmpty()) {
            return ChatTurn.text("Type a prompt to get started.");
        }
        if ("agent".equalsIgnoreCase(plannerMode)) {
            return agentTurn(prompt);
        }
        return plannerTurn(prompt);
    }

    private ChatTurn agentTurn(String prompt) {
        turnLog.userPrompt(prompt);
        try {
            String answer = agent.chat(prompt, switches.hardening());
            return ChatTurn.of(prompt, answer, toResults(turnLog.calls()));
        } catch (RuntimeException e) {
            List<ToolResult> results = new ArrayList<>(toResults(turnLog.calls()));
            Throwable input = causeOfType(e, InputGuardrailException.class);
            if (input != null) {
                results.add(ToolResult.blocked("input guardrail", Map.of("prompt", prompt),
                        message(input), "input guardrail"));
                return ChatTurn.of(prompt, "Blocked before the model saw it.", results);
            }
            Throwable output = causeOfType(e, OutputGuardrailException.class);
            if (output != null) {
                results.add(ToolResult.blocked("output guardrail", Map.of(), message(output), "output guardrail"));
                return ChatTurn.of(prompt, "The answer was blocked before it reached you.", results);
            }
            if (results.stream().anyMatch(ToolResult::stepUpRequired)) {
                return ChatTurn.of(prompt, "This needs a stronger login first.", results);
            }
            LOG.warnf(e, "Agent turn failed, falling back to the scripted planner");
            ChatTurn fallback = plannerTurnScriptedOnly(prompt);
            return fallback != null ? fallback
                    : ChatTurn.of(prompt, "Agent failed: " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : ": " + e.getMessage()), results);
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

    private ChatTurn plannerTurn(String prompt) {
        LlmToolPlanner.Plan plan = "scripted".equalsIgnoreCase(plannerMode)
                ? scripted.plan(prompt).orElse(LlmToolPlanner.Plan.text(
                        "Scripted mode only knows the demo prompts in the sidebar."))
                : planner.plan(chatModel, prompt);
        if (plan.error() != null) {
            LlmToolPlanner.Plan failedLlm = plan;
            plan = scripted.plan(prompt).orElse(null);
            if (plan == null) {
                return new ChatTurn(prompt, failedLlm.error(), null, List.of());
            }
        }
        if (!plan.hasTool()) {
            return new ChatTurn(prompt, plan.note(), null, List.of());
        }
        ToolResult result = dispatcher.invoke(plan.tool(), plan.args());
        return ChatTurn.of(prompt, plan.note(), List.of(result));
    }

    private ChatTurn plannerTurnScriptedOnly(String prompt) {
        return scripted.plan(prompt)
                .map(plan -> ChatTurn.of(prompt, plan.note(), List.of(dispatcher.invoke(plan.tool(), plan.args()))))
                .orElse(null);
    }

    public record ChatRequest(String prompt) {}

    /** {@code result} is the last tool call, kept for the single-tool planner UI; {@code results} has every call in order. */
    public record ChatTurn(String prompt, String note, ToolResult result, List<ToolResult> results) {
        public static ChatTurn text(String note) {
            return new ChatTurn(null, note, null, List.of());
        }

        public static ChatTurn of(String prompt, String note, List<ToolResult> results) {
            ToolResult last = results.isEmpty() ? null : results.get(results.size() - 1);
            return new ChatTurn(prompt, note, last, results);
        }
    }
}
