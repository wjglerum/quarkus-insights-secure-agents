package nl.lunatech.insights.chat.agent.guardrails;

import static io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult.failure;
import static io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult.success;

import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.insights.chat.agent.GuardrailSwitches;
import nl.lunatech.insights.chat.agent.GuardrailSwitches.Guard;
import nl.lunatech.insights.chat.agent.TurnLog.Outcome;
import nl.lunatech.insights.chat.agent.TurnLog.ToolCall;
import nl.lunatech.insights.chat.agent.tools.ToolCallRecorder;
import nl.lunatech.insights.chat.security.CurrentUser;

import java.util.Locale;

/**
 * Tool input guardrail on {@code send_email} (OWASP LLM02 and LLM03): the agent may only email
 * the signed-in user. It runs before the tool, so a mail to anyone else is never sent, whoever
 * asked for it: the user, or an attendee comment the model just read.
 *
 * <p>A failure goes back to the model as the tool result, so it can tell the user what happened.
 */
@ApplicationScoped
public class OwnAddressOnly implements ToolInputGuardrail {

    @Inject
    GuardrailSwitches switches;

    @Inject
    CurrentUser user;

    @Inject
    ToolCallRecorder recorder;

    @Override
    public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
        String to = request.argumentsAsJson().getString("to", "");
        if (!switches.on(Guard.TOOL_INPUT) || isMe(to) || isOwnAddress(to, user.email())) {
            return success();
        }
        String why = refusalText(to);
        recorder.record(new ToolCall(request.toolName(), recorder.parseArgs(request.arguments()),
                Outcome.BLOCKED, why, "tool input guardrail"));
        return failure(why);
    }

    public static boolean isMe(String to) {
        return to != null && to.trim().toLowerCase(Locale.ROOT).equals("me");
    }

    static boolean isOwnAddress(String to, String ownAddress) {
        return to != null && ownAddress != null && !ownAddress.isBlank() && to.trim().equalsIgnoreCase(ownAddress);
    }

    static String refusalText(String to) {
        return "Refused by the tool input guardrail: send_email only sends to the signed-in user, "
                + "not to " + to + ". Do not retry. Tell the user who asked for this mail.";
    }
}
