package nl.lunatech.insights.chat.agent.guardrails;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OwnAddressOnlyTest {

    private static final String ME = "willem.jan@lunatech.nl";

    @Test
    void mailToTheUserThemselvesIsAllowed() {
        assertThat(OwnAddressOnly.isMe("me")).isTrue();
        assertThat(OwnAddressOnly.isMe(" Me ")).isTrue();
        assertThat(OwnAddressOnly.isOwnAddress("Willem.Jan@Lunatech.nl", ME)).isTrue();
    }

    @Test
    void mailToAnyoneElseIsRefused() {
        assertThat(OwnAddressOnly.isMe("feedback-export@conf-review.example")).isFalse();
        assertThat(OwnAddressOnly.isOwnAddress("feedback-export@conf-review.example", ME)).isFalse();
        assertThat(OwnAddressOnly.isOwnAddress("", ME)).isFalse();
        assertThat(OwnAddressOnly.isOwnAddress(null, ME)).isFalse();
        assertThat(OwnAddressOnly.refusalText("x@example.com"))
                .startsWith("Refused by the tool input guardrail").contains("x@example.com");
    }

    @Test
    void withoutAnAddressInTheTokenNoAddressMatches() {
        assertThat(OwnAddressOnly.isOwnAddress("", "")).isFalse();
        assertThat(OwnAddressOnly.isOwnAddress("someone@example.com", "")).isFalse();
    }
}
