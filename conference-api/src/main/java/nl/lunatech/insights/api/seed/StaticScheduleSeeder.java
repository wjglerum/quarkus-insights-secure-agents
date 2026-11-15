package nl.lunatech.insights.api.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.lunatech.insights.api.domain.Session;
import nl.lunatech.insights.api.domain.Speaker;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class StaticScheduleSeeder {

    private static final String AGENDA_RESOURCE = "seed/insights-2026-agenda.json";

    @Transactional
    public void seedIfEmpty() throws IOException {
        if (Session.count() > 0) {
            Log.info("Static schedule: sessions already present, skipping");
            return;
        }

        Agenda agenda;
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(AGENDA_RESOURCE)) {
            agenda = new ObjectMapper().readValue(in, Agenda.class);
        }

        Map<String, Speaker> speakers = new HashMap<>();
        for (SpeakerJson row : agenda.speakers()) {
            Speaker speaker = new Speaker();
            speaker.name = row.name();
            speaker.bio = row.bio();
            speaker.persist();
            speakers.put(row.name(), speaker);
        }

        for (SessionJson row : agenda.sessions()) {
            Session s = new Session();
            s.title = row.title();
            s.abstractText = row.abstractText();
            s.room = row.room();
            s.startsAt = OffsetDateTime.parse(row.startsAt());
            s.endsAt = OffsetDateTime.parse(row.endsAt());
            s.speaker = speakers.get(row.speaker());
            s.persist();
        }

        Log.infof("Static schedule: seeded %d sessions and %d speakers from %s",
                agenda.sessions().size(), speakers.size(), AGENDA_RESOURCE);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Agenda(List<SpeakerJson> speakers, List<SessionJson> sessions) {}

    record SpeakerJson(String name, String bio) {}

    record SessionJson(
            String title,
            @JsonProperty("abstract") String abstractText,
            String speaker,
            String room,
            String startsAt,
            String endsAt) {}
}
