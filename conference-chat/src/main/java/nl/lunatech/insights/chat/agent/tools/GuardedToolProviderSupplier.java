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
 * {@link GuardedToolProvider}.
 *
 * <p>Why not {@code @ToolInputGuardrails}? In quarkus-langchain4j 1.15 tool guardrails wrap local
 * {@code @Tool} methods; MCP tools come from the MCP tool provider and are not wrapped. So for
 * MCP the guard point is the {@link ToolProvider}, which is the documented extension point for
 * AI services.
 */
@ApplicationScoped
public class GuardedToolProviderSupplier implements Supplier<ToolProvider> {

    @Inject
    @McpClientName("conference")
    McpClient conference;

    @Inject
    ToolGuards guards;

    private volatile ToolProvider provider;

    @Override
    public ToolProvider get() {
        ToolProvider p = provider;
        if (p == null) {
            synchronized (this) {
                if (provider == null) {
                    provider = new GuardedToolProvider(
                            McpToolProvider.builder().mcpClients(conference).build(), guards);
                }
                p = provider;
            }
        }
        return p;
    }
}
