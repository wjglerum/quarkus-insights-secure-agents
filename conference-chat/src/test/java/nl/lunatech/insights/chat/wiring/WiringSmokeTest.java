package nl.lunatech.insights.chat.wiring;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import nl.lunatech.insights.chat.llm.LlmToolPlanner;
import nl.lunatech.insights.chat.web.QuickPrompts;
import nl.lunatech.insights.chat.web.ToolDispatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class WiringSmokeTest {

    @Inject
    ToolDispatcher dispatcher;

    @Inject
    LlmToolPlanner planner;

    @Inject
    nl.lunatech.insights.chat.agent.ConferenceAgent agent;

    @Inject
    nl.lunatech.insights.chat.agent.tools.GuardedToolProviderSupplier guardedTools;

    @Inject
    Instance<ChatModel> chatModel;

    @Test
    void coreBeansResolve() {
        assertNotNull(dispatcher);
        assertNotNull(planner);
        assertNotNull(agent, "the ConferenceAgent AI service must be injectable");
        assertNotNull(guardedTools.get(), "the guarded MCP tool provider must build");
    }

    @Test
    void anthropicChatModelIsConfigured() {
        assertTrue(chatModel.isResolvable(), "the single Anthropic ChatModel bean must resolve");
    }

    @Test
    void quickPromptsDriveTheDemoScenarios() {
        assertEquals(13, QuickPrompts.all().size());
    }
}
