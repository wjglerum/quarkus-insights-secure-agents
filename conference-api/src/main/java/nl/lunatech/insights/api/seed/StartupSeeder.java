package nl.lunatech.insights.api.seed;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/** Seeds the schedule, then the demo data. A seeding failure fails startup instead of leaving a half-empty demo. */
@ApplicationScoped
public class StartupSeeder {

    @Inject
    StaticScheduleSeeder staticSeeder;

    @Inject
    DemoDataSeeder demoSeeder;

    void onStart(@Observes StartupEvent ev) throws Exception {
        staticSeeder.seedIfEmpty();
        demoSeeder.seedIfEmpty();
    }
}
