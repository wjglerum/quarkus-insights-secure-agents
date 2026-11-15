package nl.lunatech.insights.mcp;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import jakarta.inject.Inject;
import nl.lunatech.insights.mcp.security.StepUp;
import nl.lunatech.insights.mcp.tools.AttendeeTools;
import nl.lunatech.insights.mcp.tools.PublicTools;
import nl.lunatech.insights.mcp.tools.SpeakerTools;
import nl.lunatech.insights.mcp.tools.StepUpTools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(OidcWiremockTestResource.class)
class ToolsSmokeTest {

    @Inject
    PublicTools publicTools;

    @Inject
    AttendeeTools attendeeTools;

    @Inject
    SpeakerTools speakerTools;

    @Inject
    StepUpTools stepUpTools;

    @Inject
    StepUp stepUp;

    @Test
    void allToolBeansAreInjectable() {
        assertNotNull(publicTools);
        assertNotNull(attendeeTools);
        assertNotNull(speakerTools);
        assertNotNull(stepUpTools);
        assertNotNull(stepUp);
    }
}
