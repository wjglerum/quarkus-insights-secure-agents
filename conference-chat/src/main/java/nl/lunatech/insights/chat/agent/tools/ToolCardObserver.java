package nl.lunatech.insights.chat.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import nl.lunatech.insights.chat.agent.TurnLog;
import nl.lunatech.insights.chat.agent.TurnLog.Outcome;
import nl.lunatech.insights.chat.agent.TurnLog.ToolCall;
import nl.lunatech.insights.chat.agent.guardrails.OwnAddressOnly;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns every tool call the agent makes (MCP and local) into a chat card, by observing the
 * {@link ToolExecutedEvent} that Quarkus LangChain4j fires after each call. Demo scaffolding:
 * it records, it does not guard.
 */
@ApplicationScoped
public class ToolCardObserver {

    @Inject
    ObjectMapper mapper;

    void onToolExecuted(@Observes ToolExecutedEvent event) {
        String text = event.resultText();
        Outcome outcome;
        String guard = null;
        if (text != null && text.contains(OwnAddressOnly.REFUSAL_PREFIX)) {
            outcome = Outcome.BLOCKED;
            guard = "tool input guardrail";
        } else if (!event.isError()) {
            // Step-up is only ever an error. A successful result is data, even if it quotes the code.
            outcome = Outcome.OK;
        } else if (text != null && text.toLowerCase(Locale.ROOT).contains("insufficient_user_authentication")) {
            outcome = Outcome.STEP_UP;
        } else {
            outcome = Outcome.ERROR;
        }
        turnLog().add(new ToolCall(event.request().name(), parseArgs(event.request().arguments()), outcome, text, guard));
    }

    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            return new LinkedHashMap<>(Map.of("raw", json));
        }
    }

    /** Outside a chat request (background calls), a throwaway log. */
    private static TurnLog turnLog() {
        ArcContainer arc = Arc.container();
        return arc != null && arc.requestContext().isActive() ? arc.instance(TurnLog.class).get() : new TurnLog();
    }
}
