package nl.lunatech.insights.chat.agent;

import jakarta.enterprise.context.RequestScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every tool call the agent made in one chat turn, with its outcome, so the chat can show each
 * call as a card.
 */
@RequestScoped
public class TurnLog {

    public enum Outcome { OK, ERROR, STEP_UP, BLOCKED }

    /** One tool call. {@code guard} names the guardrail that blocked it, or is null. */
    public record ToolCall(String tool, Map<String, Object> args, Outcome outcome, String resultText, String guard) {
    }

    private final List<ToolCall> calls = new ArrayList<>();

    public void add(ToolCall call) {
        calls.add(call);
    }

    public List<ToolCall> calls() {
        return List.copyOf(calls);
    }
}
