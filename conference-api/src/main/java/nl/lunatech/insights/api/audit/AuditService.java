package nl.lunatech.insights.api.audit;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.lunatech.insights.api.domain.AuditEvent;
import nl.lunatech.insights.api.security.StepUp;
import nl.lunatech.insights.api.security.Tokens;
import nl.lunatech.insights.api.web.AttendeeService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@ApplicationScoped
public class AuditService {

    @Inject
    JsonWebToken jwt;

    @Inject
    AttendeeService attendees;

    @Inject
    StepUp stepUp;

    /** Records an action as part of the caller's transaction: it is only kept if the action commits. */
    @Transactional
    public void record(String action, String target, String detail) {
        persist(action, target, detail);
    }

    /**
     * Records a refused attempt in its own transaction. The exception that follows rolls back the
     * caller's transaction, and the audit row must survive that.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordDenied(String action, String target, String detail) {
        persist(action, target, detail);
    }

    private void persist(String action, String target, String detail) {
        AuditEvent event = new AuditEvent();
        event.attendeeSubject = attendees.subject();
        event.action = action;
        event.target = target;
        event.tokenAcr = stepUp.acr();
        event.tokenAmr = Tokens.amr(jwt);
        event.executedByClient = stepUp.executedByClient();
        event.tokenAct = Tokens.act(jwt);
        event.tokenIss = Tokens.iss(jwt);
        // Real time, not the demo clock: the trail records when the action actually happened.
        event.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        event.detail = detail;
        event.persist();
        Log.infof("AUDIT subject=%s action=%s target=%s acr=%s amr=%s client=%s act=%s iss=%s",
                event.attendeeSubject, event.action, event.target, event.tokenAcr, event.tokenAmr,
                event.executedByClient, event.tokenAct, event.tokenIss);
    }
}
