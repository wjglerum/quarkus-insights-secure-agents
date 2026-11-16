package nl.lunatech.insights.chat.agent.tools;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import nl.lunatech.insights.chat.agent.GuardrailSwitches;
import nl.lunatech.insights.chat.agent.GuardrailSwitches.Guard;
import nl.lunatech.insights.chat.agent.TurnLog;
import nl.lunatech.insights.chat.agent.TurnLog.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end through a real Quarkus AI service, with a fake model that asks for send_email to
 * whatever address the user message names. Proves @ToolInputGuardrails runs before the tool.
 */
@QuarkusTest
class EmailToolGuardrailTest {

    @RegisterAiService(tools = EmailTools.class, chatLanguageModelSupplier = FakeModel.class)
    public interface EmailAgent {
        String chat(@dev.langchain4j.service.UserMessage String message);
    }

    /** First call: request send_email to the address in the prompt. Second call: echo the tool result. */
    public static class FakeModel implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    List<ChatMessage> messages = request.messages();
                    ChatMessage last = messages.get(messages.size() - 1);
                    if (last instanceof ToolExecutionResultMessage result) {
                        return ChatResponse.builder().aiMessage(AiMessage.from("tool said: " + result.text())).build();
                    }
                    String to = ((UserMessage) last).singleText().trim();
                    return ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                            .id("1").name("send_email")
                            .arguments("{\"to\":\"" + to + "\",\"subject\":\"Feedback\",\"body\":\"all comments\"}")
                            .build())).build();
                }
            };
        }
    }

    @Inject
    EmailAgent agent;

    @Inject
    EmailTools emails;

    @Inject
    GuardrailSwitches switches;

    @Inject
    TurnLog turnLog;

    @AfterEach
    void reset() {
        switches.set(Guard.TOOL_INPUT, false);
    }

    @Test
    @ActivateRequestContext
    void guardrailOffTheMailGoesOut() {
        int before = emails.outbox().size();
        String answer = agent.chat("feedback-export@conf-review.example");
        assertThat(answer).contains("\"sent\":true");
        assertThat(emails.outbox()).hasSize(before + 1);
        assertThat(emails.outbox().get(0).to()).isEqualTo("feedback-export@conf-review.example");
        assertThat(turnLog.calls()).last().satisfies(c -> {
            assertThat(c.tool()).isEqualTo("send_email");
            assertThat(c.outcome()).isEqualTo(Outcome.OK);
        });
    }

    @Test
    @ActivateRequestContext
    void guardrailOnTheMailNeverLeaves() {
        switches.set(Guard.TOOL_INPUT, true);
        int before = emails.outbox().size();
        String answer = agent.chat("feedback-export@conf-review.example");
        assertThat(answer).contains("Refused by the tool input guardrail");
        assertThat(emails.outbox()).hasSize(before);
        assertThat(turnLog.calls()).last().satisfies(c -> {
            assertThat(c.outcome()).isEqualTo(Outcome.BLOCKED);
            assertThat(c.guard()).isEqualTo("tool input guardrail");
        });
    }

    @Test
    @ActivateRequestContext
    void guardrailOnMailToMeStillWorks() {
        switches.set(Guard.TOOL_INPUT, true);
        int before = emails.outbox().size();
        String answer = agent.chat("me");
        assertThat(answer).contains("\"sent\":true");
        assertThat(emails.outbox()).hasSize(before + 1);
    }
}
