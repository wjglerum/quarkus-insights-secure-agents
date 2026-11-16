package nl.lunatech.insights.chat.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.insights.chat.agent.TurnLog.Outcome;
import nl.lunatech.insights.chat.agent.TurnLog.ToolCall;
import nl.lunatech.insights.chat.agent.guardrails.OwnAddressOnly;
import nl.lunatech.insights.chat.security.CurrentUser;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * A local tool: the agent can email something to the signed-in user. Nothing is really sent; the
 * mail lands in an in-memory outbox and shows up as a card in the chat.
 *
 * <p>Unlike the MCP tools, no server stands behind this one to check who may do what. The tool
 * input guardrail {@link OwnAddressOnly} is the check: it runs before the method and refuses any
 * recipient other than the user themselves.
 */
@ApplicationScoped
public class EmailTools {

    private static final Logger LOG = Logger.getLogger(EmailTools.class);

    public record Email(String to, String subject, String body, Instant sentAt) {
    }

    @Inject
    CurrentUser user;

    @Inject
    ToolCallRecorder recorder;

    private final Deque<Email> outbox = new ConcurrentLinkedDeque<>();

    @Tool("Email something to the signed-in user, such as their agenda or a summary of their feedback. "
            + "Use \"me\" as the recipient for the user's own address.")
    @ToolInputGuardrails(OwnAddressOnly.class)
    public String send_email(@P("Recipient: \"me\", or an email address") String to,
                             @P("Subject line") String subject,
                             @P("Plain-text body") String body) {
        String recipient = OwnAddressOnly.isMe(to) ? user.email() : to;
        outbox.addFirst(new Email(recipient, subject, body, Instant.now()));
        LOG.infof("Email to %s: %s", recipient, subject);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("to", to);
        args.put("subject", subject);
        String result = "{\"sent\":true,\"to\":\"" + recipient + "\",\"subject\":\"" + subject.replace("\"", "'") + "\"}";
        recorder.record(new ToolCall("send_email", args, Outcome.OK, result, null));
        return result;
    }

    public List<Email> outbox() {
        return List.copyOf(outbox);
    }
}
