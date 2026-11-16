package nl.lunatech.insights.chat.wiring;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import nl.lunatech.insights.chat.agent.ConferenceAgent;
import nl.lunatech.insights.chat.agent.tools.RecordingToolProviderSupplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class WiringSmokeTest {

    @Inject
    ConferenceAgent agent;

    @Inject
    RecordingToolProviderSupplier mcpTools;

    @Inject
    Instance<ChatModel> chatModel;

    @Test
    void coreBeansResolve() {
        assertNotNull(agent, "the ConferenceAgent AI service must be injectable");
        assertNotNull(mcpTools.get(), "the MCP tool provider must build");
    }

    @Test
    void anthropicChatModelIsConfigured() {
        assertTrue(chatModel.isResolvable(), "the single Anthropic ChatModel bean must resolve");
    }
}
