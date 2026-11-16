package nl.lunatech.insights.chat.agent;

import jakarta.enterprise.context.RequestScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Everything the agent did in one chat turn: the user's prompt and every tool call with its
 * outcome, so the chat can show each call as a card.
 */
@RequestScoped
public class TurnLog {

    public enum Outcome { OK, ERROR, STEP_UP, BLOCKED }

    /** One tool call. {@code guard} names the guardrail that blocked it, or is null. */
    public record ToolCall(String tool, Map<String, Object> args, Outcome outcome, String resultText, String guard) {
    }

    private String userPrompt = "";
    private final List<ToolCall> calls = new ArrayList<>();

    public String userPrompt() {
        return userPrompt;
    }

    public void userPrompt(String prompt) {
        this.userPrompt = prompt;
    }

    public void add(ToolCall call) {
        calls.add(call);
    }

    public List<ToolCall> calls() {
        return List.copyOf(calls);
    }
}
