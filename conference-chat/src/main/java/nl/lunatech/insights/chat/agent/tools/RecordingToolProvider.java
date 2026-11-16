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
 * Wraps every MCP tool's executor so {@link ToolCallRecorder} can record the call for the chat
 * cards. It does not change what the tool does or returns.
 */
public class RecordingToolProvider implements ToolProvider {

    private final ToolProvider delegate;
    private final ToolCallRecorder recorder;

    public RecordingToolProvider(ToolProvider delegate, ToolCallRecorder recorder) {
        this.delegate = delegate;
        this.recorder = recorder;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult tools = delegate.provideTools(request);
        List<AiServiceTool> wrapped = tools.aiServiceTools().stream()
                .map(t -> t.toBuilder().toolExecutor(new Recorded(t.name(), t.toolExecutor())).build())
                .toList();
        return new ToolProviderResult(wrapped);
    }

    @Override
    public boolean isDynamic() {
        return delegate.isDynamic();
    }

    private final class Recorded implements ToolExecutor {

        private final String name;
        private final ToolExecutor executor;

        Recorded(String name, ToolExecutor executor) {
            this.name = name;
            this.executor = executor;
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            return recorder.run(name, request, () -> {
                String text = executor.execute(request, memoryId);
                return ToolExecutionResult.builder().resultText(text).build();
            }).resultText();
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            return recorder.run(name, request, () -> executor.executeWithContext(request, context));
        }
    }
}
