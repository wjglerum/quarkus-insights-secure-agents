package nl.lunatech.porto.chat.agent.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.porto.chat.agent.GuardrailSwitches;
import nl.lunatech.porto.chat.agent.GuardrailSwitches.Guard;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Locale;

/**
 * Input guardrail (OWASP LLM01, direct prompt injection): rejects the user's message before it
 * reaches the model when it contains a known injection phrase.
 *
 * <p>Deterministic, cheap and unit-testable, and also easy to get around with a rephrase. It only
 * sees what the user typed, never tool results, so indirect injection passes it by design.
 */
@ApplicationScoped
public class PromptInjectionGuard implements InputGuardrail {

    @Inject
    GuardrailSwitches switches;

    @ConfigProperty(name = "guardrails.injection.phrases")
    List<String> phrases;

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        if (!switches.on(Guard.INPUT)) {
            return success();
        }
        String text = userMessage.singleText();
        if (text == null || text.isBlank()) {
            return success();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String phrase : phrases) {
            String p = phrase.trim().toLowerCase(Locale.ROOT);
            if (!p.isEmpty() && lower.contains(p)) {
                return fatal("PromptInjectionGuard: the message contains \"" + p + "\" and was not sent to the model.");
            }
        }
        return success();
    }
}
