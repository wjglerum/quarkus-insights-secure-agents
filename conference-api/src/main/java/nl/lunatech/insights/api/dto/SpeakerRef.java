package nl.lunatech.insights.api.dto;

import nl.lunatech.insights.api.domain.Speaker;

public record SpeakerRef(Long id, String name) {
    public static SpeakerRef of(Speaker s) {
        return s == null ? null : new SpeakerRef(s.id, s.name);
    }
}
