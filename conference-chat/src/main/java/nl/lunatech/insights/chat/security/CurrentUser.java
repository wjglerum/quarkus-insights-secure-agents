package nl.lunatech.insights.chat.security;

import io.quarkus.oidc.IdToken;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.stream.StreamSupport;

/** The caller's identity, shown on the chat page (Qute) and by {@code /api/me} (JSON). */
@RequestScoped
public class CurrentUser {

    @Inject
    @IdToken
    JsonWebToken idToken;

    @Inject
    JsonWebToken accessToken;

    @Inject
    SecurityIdentity identity;

    public String subject() {
        return claim(accessToken, "preferred_username", accessToken.getSubject());
    }

    public String name() {
        return claim(idToken, "name", claim(idToken, "preferred_username", accessToken.getSubject()));
    }

    public String rolesDisplay() {
        return identity.getRoles().isEmpty() ? "(none)" : String.join(", ", identity.getRoles());
    }

    /** The user's own email address, from the ID token. Empty when the token has none. */
    public String email() {
        return claim(idToken, "email", claim(accessToken, "email", ""));
    }

    public String acr() {
        return claim(accessToken, "acr", "1");
    }

    public String amrDisplay() {
        Object amr = accessToken.getClaim("amr");
        if (amr instanceof Iterable<?> values) {
            String joined = String.join(", ", StreamSupport.stream(values.spliterator(), false)
                    .map(String::valueOf).toList());
            return joined.isEmpty() ? "(none)" : joined;
        }
        return amr == null ? "(none)" : String.valueOf(amr);
    }

    private static String claim(JsonWebToken jwt, String name, String defaultValue) {
        Object value = jwt == null ? null : jwt.getClaim(name);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }
}
