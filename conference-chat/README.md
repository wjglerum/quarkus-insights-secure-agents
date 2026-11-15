# conference-chat

AI chat client for the Quarkus Insights #268 secure agents demo. A Quarkus app on port 8082 that:

- Logs the user in via the OIDC web-app flow with PKCE S256 (shared Keycloak Dev Services realm).
- Connects to `conference-mcp` over `quarkus-langchain4j-mcp` and sends the user's bearer token
  on every MCP call (`McpAuthProvider`).
- Runs each prompt through `ConferenceAgent`, a `@RegisterAiService` on Anthropic Claude that may
  call several MCP tools and reads their results, with input, tool and output guardrails.
- Renders each tool call as an `mcp.tool_call` + `mcp.tool_result` card.

See `../README.md` for the guardrails and the demo flow, and `../DESIGN.md` for the security
decisions.

## Run

```shell
export ANTHROPIC_API_KEY=...
./mvnw quarkus:dev
```

The app listens on `http://localhost:8082/`. Anonymous requests redirect to Keycloak; log in as
`attendee / attendee` or `willem.jan / willem.jan`. Clients and users come from the
monorepo-root `keycloak-realm.json`.

`conference-mcp` (port 8081) and `conference-api` (port 8080) must be running for tool calls to
succeed.

Without an API key, or when the model is unreachable, the chat falls back to `ScriptedPlanner`,
which only understands the demo prompts in the sidebar.

## Endpoints

| Path | Auth | Purpose |
|------|------|---------|
| `GET /` | authenticated | Server-rendered chat shell (Qute), including the quick prompts. |
| `POST /api/chat/send` | authenticated | Run a prompt; returns the answer and every tool call with its result. |
| `GET /api/me` | authenticated | Current identity claims; refreshes the sidebar after step-up. |
| `GET /api/guardrails`, `POST /api/guardrails/{key}` | authenticated | Read and flip the guardrail switches. Demo only. |
| `GET /step-up` | authenticated, `acr=urn:insights:mfa` | Opened in a popup; triggers the MFA login and signals the chat when done. |
| `GET /logout` | authenticated | RP-initiated logout at Keycloak. |

Static assets (`/chat.css`, `/chat.js`, `/favicon.ico`) are public. An unauthenticated `/api/*`
call gets a plain 401 instead of a login redirect (`ApiAwareAuthMechanism`).

## Tests

```shell
./mvnw test
```

- `GuardrailsTest`, `ToolGuardsTest`: the guardrails as plain unit tests, no model needed.
- `AgentTurnTest`: how an agent turn becomes chat cards, with a stub agent.
- `ScriptedPlannerTest`: the scripted fallback matches the demo prompts.
- `WiringSmokeTest`: `@QuarkusTest` checking that the agent, the guarded tool provider and the
  Anthropic `ChatModel` resolve.
- `ChatPlaywrightTest`: browser test of the OIDC login, the Qute shell and the step-up popup
  against a real Keycloak. It needs Chromium, which the Playwright driver downloads on first run.

The test profile disables OIDC and Keycloak Dev Services and swaps the MCP HTTP client for a
dummy stdio process (`sleep 600`), so the `@QuarkusTest` boots in seconds. The Playwright class
re-enables OIDC and Keycloak through a `QuarkusTestProfile`.

## Extensions

| Extension | Purpose |
|-----------|---------|
| `quarkus-rest` + `quarkus-rest-jackson` | REST endpoints under `/api`. |
| `quarkus-oidc` | OIDC web-app flow with PKCE S256. |
| `quarkus-qute` + `quarkus-rest-qute` | Server-rendered chat shell. |
| `quarkus-langchain4j-mcp` | MCP client to `conference-mcp`. |
| `quarkus-langchain4j-anthropic` | Anthropic Claude provider. |
| `quarkus-oidc-client` | Service-account token for MCP calls outside a user request. |

The LangChain4j extensions come from the Quarkiverse BOM
(`io.quarkiverse.langchain4j:quarkus-langchain4j-bom`), imported next to the Quarkus platform BOM.

## Configuration

| Env var | Purpose | Default |
|---------|---------|---------|
| `ANTHROPIC_API_KEY` | API key for Claude. | unset (scripted fallback) |
| `DEMO_PLANNER` | `agent`: the AI service with guardrails. `scripted`: no model at all. | `agent` |
| `DEMO_GUARDRAILS` | Guardrails switched on at start: `none`, `all`, or a list of `input,prompt,tool-intent,tool-output,output`. | `none` |
| `CONFERENCE_MCP_URL` | MCP endpoint (streamable HTTP). | `http://localhost:8081/mcp` |

## Quarkus guides

- <https://quarkus.io/guides/security-openid-connect-web-authentication>
- <https://quarkus.io/guides/qute>
- <https://docs.quarkiverse.io/quarkus-langchain4j/dev/mcp.html>
- <https://docs.quarkiverse.io/quarkus-langchain4j/dev/guardrails.html>
- <https://docs.quarkiverse.io/quarkus-langchain4j/dev/anthropic-chat-models.html>
