package nl.lunatech.insights.api.web;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.lunatech.insights.api.domain.Attendee;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Optional;

@ApplicationScoped
public class AttendeeService {

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity identity;

    /**
     * The attendee record for the caller, created on first use. Only the seeded speaker is linked
     * to a {@code Speaker}; the speaker role alone does not make anyone the owner of a session.
     */
    @Transactional
    public Attendee currentAttendee() {
        String subject = subject();
        Attendee attendee = Attendee.findBySubject(subject);
        if (attendee == null) {
            attendee = new Attendee();
            attendee.subject = subject;
            attendee.displayName = claim("name").orElse(subject);
            attendee.persist();
        }
        return attendee;
    }

    /**
     * Who the caller is, for attendee records and the audit trail alike. The readable username
     * wins over the opaque {@code sub} UUID so the trail names a person (e.g. willem.jan).
     */
    public String subject() {
        return claim("preferred_username")
                .or(() -> claim("sub"))
                .orElseGet(() -> identity.getPrincipal().getName());
    }

    /** A non-blank string JWT claim, or empty if the claim is absent. */
    private Optional<String> claim(String name) {
        Object value = jwt.getClaim(name);
        return Optional.ofNullable(value).map(String::valueOf).filter(s -> !s.isBlank());
    }
}
