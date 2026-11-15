package nl.lunatech.porto.chat.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ToolDispatcher {

    private static final Logger LOG = Logger.getLogger(ToolDispatcher.class);

    @Inject
    ToolProvider mcpToolProvider;

    @Inject
    ObjectMapper mapper;

    public ToolResult invoke(String tool, Map<String, Object> args) {
        Map<String, Object> safeArgs = args == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(args);

        try {
            ToolProviderResult tools = mcpToolProvider.provideTools(null);
            ToolExecutor executor = findExecutor(tools, tool);
            if (executor == null) {
                return ToolResult.err(tool, safeArgs, "Unknown MCP tool: " + tool);
            }

            String argsJson = mapper.writeValueAsString(safeArgs);
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(tool)
                    .arguments(argsJson)
                    .build();
            String resultText = executor.execute(request, null);
            if (resultText != null && isStepUp(resultText)) {
                return ToolResult.stepUp(tool, safeArgs);
            }
            return ToolResult.ok(tool, safeArgs, parseJsonIfPossible(resultText));
        } catch (Exception e) {
            LOG.warnf(e, "MCP tool call failed: tool=%s", tool);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (isStepUp(msg)) {
                return ToolResult.stepUp(tool, safeArgs);
            }
            return ToolResult.err(tool, safeArgs, e.getClass().getSimpleName() + ": " + msg);
        }
    }

    private static ToolExecutor findExecutor(ToolProviderResult tools, String name) {
        for (AiServiceTool t : tools.aiServiceTools()) {
            if (name.equals(t.name())) return t.toolExecutor();
        }
        return null;
    }

    private Object parseJsonIfPossible(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String trimmed = raw.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return raw;
        }
        try {
            // The MCP server emits one content block per list element, which the client
            // concatenates into a single string. Read every JSON value, not just the first,
            // so multi-result tools (e.g. whats_on_now across tracks) keep all their items.
            List<Object> values = mapper.readerFor(Object.class).<Object>readValues(trimmed).readAll();
            if (values.isEmpty()) return raw;
            return values.size() == 1 ? values.get(0) : values;
        } catch (Exception e) {
            return raw;
        }
    }

    // Match only the RFC 9470 error code; looser markers can appear in legitimate tool results.
    private static boolean isStepUp(String message) {
        return message != null && message.toLowerCase().contains("insufficient_user_authentication");
    }

    /** {@code guard} names the guardrail that blocked or rewrote this call, or is null. */
    public record ToolResult(String tool, Map<String, Object> args, Object result,
                              String error, boolean stepUpRequired, String guard) {
        public static ToolResult ok(String tool, Map<String, Object> args, Object result) {
            return new ToolResult(tool, args, result, null, false, null);
        }
        public static ToolResult err(String tool, Map<String, Object> args, String error) {
            return new ToolResult(tool, args, null, error, false, null);
        }
        public static ToolResult stepUp(String tool, Map<String, Object> args) {
            return new ToolResult(tool, args, null,
                    "insufficient_user_authentication: this tool requires step-up MFA",
                    true, null);
        }
        public static ToolResult blocked(String tool, Map<String, Object> args, String error, String guard) {
            return new ToolResult(tool, args, null, error, false, guard);
        }
        public static ToolResult rewritten(String tool, Map<String, Object> args, Object shownToModel, String guard) {
            return new ToolResult(tool, args, shownToModel, null, false, guard);
        }
    }

    /** Same JSON handling as for direct calls, for results the agent already fetched. */
    public Object parseResult(String raw) {
        return parseJsonIfPossible(raw);
    }
}
