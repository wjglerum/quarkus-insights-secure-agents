package nl.lunatech.insights.chat.agent.tools;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.function.Supplier;

/**
 * Gives {@code ConferenceAgent} the tools of the "conference" MCP client, wrapped in
 * {@link RecordingToolProvider} so every call shows up as a card in the chat.
 */
@ApplicationScoped
public class RecordingToolProviderSupplier implements Supplier<ToolProvider> {

    @Inject
    @McpClientName("conference")
    McpClient conference;

    @Inject
    ToolCallRecorder recorder;

    @Override
    public ToolProvider get() {
        return new RecordingToolProvider(McpToolProvider.builder().mcpClients(conference).build(), recorder);
    }
}
