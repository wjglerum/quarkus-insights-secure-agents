package nl.lunatech.insights.chat.agent;

import jakarta.enterprise.context.RequestScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Everything the agent did in one chat turn: the user's own prompt (which the tool call guard
 * compares against) and every MCP tool call with its outcome, so the chat can show each call
 * as a card.
 */
@RequestScoped
public class TurnLog {

    public enum Outcome { OK, ERROR, STEP_UP, BLOCKED }

    /**
     * One tool call. {@code shownToModel} is the result text after the tool result guard, when it
     * changed anything; {@code guard} names the guard that blocked or rewrote the call.
     */
    public record ToolCall(String tool, Map<String, Object> args, Outcome outcome,
                           String resultText, String shownToModel, String guard) {
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
