package nl.lunatech.insights.chat.wiring;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import nl.lunatech.insights.chat.agent.ConferenceAgent;
import nl.lunatech.insights.chat.agent.tools.GuardedToolProviderSupplier;
import nl.lunatech.insights.chat.web.ToolDispatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class WiringSmokeTest {

    @Inject
    ToolDispatcher dispatcher;

    @Inject
    ConferenceAgent agent;

    @Inject
    GuardedToolProviderSupplier guardedTools;

    @Inject
    Instance<ChatModel> chatModel;

    @Test
    void coreBeansResolve() {
        assertNotNull(dispatcher);
        assertNotNull(agent, "the ConferenceAgent AI service must be injectable");
        assertNotNull(guardedTools.get(), "the guarded MCP tool provider must build");
    }

    @Test
    void anthropicChatModelIsConfigured() {
        assertTrue(chatModel.isResolvable(), "the single Anthropic ChatModel bean must resolve");
    }
}
