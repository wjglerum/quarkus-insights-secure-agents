package nl.lunatech.insights.chat.agent.guardrails;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.insights.chat.agent.ConferenceAgent;
import nl.lunatech.insights.chat.agent.GuardrailSwitches;
import nl.lunatech.insights.chat.agent.GuardrailSwitches.Guard;

/**
 * Output guardrail (OWASP LLM08, hidden context exposure): drops the answer when it contains the
 * canary from the system prompt. It only catches a verbatim leak; a paraphrase gets past it.
 *
 * <p>OWASP's 2026 advice is to assume hidden context is discoverable, so the real fix is to keep
 * secrets out of prompts. The canary tells you when it happened anyway.
 */
@ApplicationScoped
public class HiddenContextGuard implements OutputGuardrail {

    @Inject
    GuardrailSwitches switches;

    @Override
    public OutputGuardrailResult validate(AiMessage answer) {
        if (!switches.on(Guard.OUTPUT)) {
            return success();
        }
        String text = answer.text();
        if (text == null || text.isBlank()) {
            return success();
        }
        if (text.contains(ConferenceAgent.CANARY)) {
            return fatal("HiddenContextGuard: the answer contained the system prompt canary and was dropped.");
        }
        return success();
    }
}
