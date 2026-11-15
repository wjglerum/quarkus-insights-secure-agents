# conference-mcp

Quarkus MCP server for the Quarkus Insights #268 secure agents demo. Thin protocol adapter that exposes
conference tools to AI clients, secured with OIDC. Calls to `conference-api` carry
a token obtained through RFC 8693 token exchange, never the user's own token.

- Port: 8081
- MCP endpoint (streamable HTTP): `http://localhost:8081/mcp`
- Health: `GET /q/health/ready`

Start it with `./mvnw quarkus:dev` from this directory. Dev Services provisions a
shared Keycloak using the realm at `../keycloak-realm.json`.

See `../README.md` for the architecture and `../DESIGN.md` for the tool tiers.
