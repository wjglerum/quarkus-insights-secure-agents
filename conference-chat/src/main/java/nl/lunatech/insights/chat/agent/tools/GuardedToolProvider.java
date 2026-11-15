package nl.lunatech.insights.chat.agent.tools;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;

import java.util.List;

/**
 * Wraps every MCP tool's executor so {@link ToolGuards} sees each call before it goes out and
 * each result before the model reads it.
 */
public class GuardedToolProvider implements ToolProvider {

    private final ToolProvider delegate;
    private final ToolGuards guards;

    public GuardedToolProvider(ToolProvider delegate, ToolGuards guards) {
        this.delegate = delegate;
        this.guards = guards;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult tools = delegate.provideTools(request);
        List<AiServiceTool> wrapped = tools.aiServiceTools().stream()
                .map(t -> t.toBuilder().toolExecutor(new Guarded(t.name(), t.toolExecutor())).build())
                .toList();
        return new ToolProviderResult(wrapped);
    }

    @Override
    public boolean isDynamic() {
        return delegate.isDynamic();
    }

    private final class Guarded implements ToolExecutor {

        private final String name;
        private final ToolExecutor executor;

        Guarded(String name, ToolExecutor executor) {
            this.name = name;
            this.executor = executor;
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            return guards.run(name, request, () -> {
                String text = executor.execute(request, memoryId);
                return ToolExecutionResult.builder().resultText(text).build();
            }).resultText();
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            return guards.run(name, request, () -> executor.executeWithContext(request, context));
        }
    }
}
