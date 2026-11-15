# conference-api

Backing data API for the Quarkus Insights #268 secure agents demo. Exposes the schedule, attendee
agendas, ratings, and an open audit feed under `/api/v1/`, and serves the
second-screen audit dashboard at `/audit-live/`.

Part of a three-app monorepo. See `../README.md` for the architecture and
`../DESIGN.md` for the security decisions.

## Stack

- Quarkus 3.x on Java 25
- REST (Reactive) + Jackson
- Hibernate ORM with Panache, PostgreSQL via Dev Services
- OIDC bearer-only (Keycloak via Dev Services, shared realm at
  `../keycloak-realm.json`)
- SmallRye OpenAPI + Swagger UI
- Hibernate Validator

No Flyway. Hibernate generates the schema (`drop-and-create`).

## Running

From this directory:

```shell
./mvnw quarkus:dev
```

Dev Services boots Postgres and a shared Keycloak realm automatically. The
seeders fill the schedule from `src/main/resources/seed/insights-2026-agenda.json`
and create demo attendees, bookmarks, and ratings.

Useful URLs in dev mode:

- `http://localhost:8080/q/dev/` -- Dev UI
- `http://localhost:8080/q/swagger-ui/` -- Swagger UI
- `http://localhost:8080/audit-live/` -- second-screen audit dashboard
- `http://localhost:8080/api/v1/sessions` -- public schedule
- `http://localhost:8080/api/v1/audit/recent` -- open audit feed

## REST surface

Public (no auth):

- `GET /api/v1/sessions` (`?speaker_id=`, `?speaker_name=`, `?q=`)
- `GET /api/v1/sessions/{id}`
- `GET /api/v1/sessions/current?at=`
- `GET /api/v1/sessions/next?at=&limit=`
- `GET /api/v1/speakers`
- `GET /api/v1/audit/recent?limit=`

Attendee role (`attendee`):

- `GET /api/v1/me`
- `GET|POST /api/v1/me/agenda`, `DELETE /api/v1/me/agenda/{sessionId}`
- `GET /api/v1/me/conflicts`
- `POST /api/v1/sessions/{id}/ratings`
- `GET /api/v1/me/ratings`

Speaker role (`speaker`):

- `GET /api/v1/me/sessions/feedback`

Speaker role plus step-up (`acr=urn:insights:mfa`, on the token or asserted by
conference-mcp in `X-Mcp-Acr`):

- `GET /api/v1/sessions/{id}/attendees`
- `POST /api/v1/sessions/{id}/cancel` (`"undo": true` restores the session)

conference-mcp only:

- `POST /api/v1/audit/refusals` (audits a step-up tool it refused)

## Demo clock

The rating cutoff and `whats_on_now`/`whats_next` resolve to `demo.now` when
set, otherwise to the wall clock in UTC. Session times leave the API in venue time (Europe/Amsterdam, `+01:00` in November). In `%dev` the default is
`2026-11-16T15:10:00+01:00` so the demo lands half-way through the MCP talk slot out of
the box. Override with `DEMO_NOW`.

## Tests

Run the full suite (no live Keycloak required; `%test` verifies tokens against
the wiremock OIDC server):

```shell
./mvnw test
```

The suite covers public endpoints, the audit-live dashboard route, attendee
agenda CRUD with audit, rating gating against a profile-pinned demo clock, the
open audit feed, and step-up + reversible cancellation for the speaker.

## Configuration

See `src/main/resources/application.properties`. Dev Services provides Postgres and
Keycloak. Set `DEMO_NOW` to move the demo clock.

## Packaging

```shell
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```
