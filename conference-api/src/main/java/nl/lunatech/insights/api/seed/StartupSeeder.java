package nl.lunatech.insights.api.seed;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class StartupSeeder {

    @Inject
    StaticScheduleSeeder staticSeeder;

    @Inject
    DemoDataSeeder demoSeeder;

    void onStart(@Observes StartupEvent ev) {
        try {
            staticSeeder.seedIfEmpty();
        } catch (Exception e) {
            Log.warnf(e, "Static schedule seeding failed");
        }
        try {
            demoSeeder.seedIfEmpty();
        } catch (Exception e) {
            Log.warnf(e, "Demo data seeding failed");
        }
    }
}
