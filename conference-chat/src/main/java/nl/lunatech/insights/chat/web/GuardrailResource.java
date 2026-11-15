package nl.lunatech.insights.chat.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.insights.chat.agent.GuardrailSwitches;
import nl.lunatech.insights.chat.agent.GuardrailSwitches.GuardState;
import org.jboss.logging.Logger;

import java.util.List;

/** Backs the guardrail switches in the chat sidebar. Demo only. */
@Path("/api/guardrails")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class GuardrailResource {

    private static final Logger LOG = Logger.getLogger(GuardrailResource.class);

    @Inject
    GuardrailSwitches switches;

    @GET
    public List<GuardState> list() {
        return switches.all();
    }

    @POST
    @Path("/{key}")
    public List<GuardState> set(@PathParam("key") String key, Toggle toggle) {
        GuardrailSwitches.Guard guard = GuardrailSwitches.Guard.byKey(key)
                .orElseThrow(() -> new NotFoundException("Unknown guardrail: " + key));
        boolean enabled = toggle != null && toggle.enabled();
        switches.set(guard, enabled);
        LOG.infof("Guardrail %s switched %s", guard.key, enabled ? "ON" : "OFF");
        return switches.all();
    }

    public record Toggle(boolean enabled) {
    }
}
