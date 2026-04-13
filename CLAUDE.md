# Tasker

Personal kanban/notes board — a lightweight self-hosted alternative to Jira for personal use.

## Mission

A simple task board where columns represent projects and cards represent tickets with statuses. Managed both through the web UI and programmatically via Claude agents using MCP.

## Architecture

- **Backend**: Spring Boot 4.0.5, Java 21
- **Frontend**: Static HTML + vanilla JS (`src/main/resources/static/index.html`) — no framework, no Thymeleaf
- **Database**: SQLite via `sqlite-jdbc` + Hibernate community dialect. Single file at `$TASKER_DB_PATH` (default `./tasker.db`). Schema auto-managed by Hibernate `ddl-auto: update`
- **Real-time**: Server-Sent Events (SSE) at `/api/board/events` — any mutation broadcasts a `refresh` event so all connected browsers re-render
- **MCP**: Hand-rolled MCP server (JSON-RPC over SSE) at `/sse` — no Spring AI dependency, implements the protocol directly in `McpController`
- **Distribution**: Single Docker image, no external dependencies. `./tasker.sh start|stop|rebuild`

## Project Structure

```
src/main/java/com/ayush/tasker/
├── Application.java                  # Entry point
├── model/
│   ├── Status.java                   # Enum: TODO, IN_PROGRESS, DONE
│   ├── Project.java                  # JPA entity — board columns
│   └── Ticket.java                   # JPA entity — cards within columns
├── repository/
│   ├── ProjectRepository.java        # findAllWithTickets() uses JOIN FETCH
│   └── TicketRepository.java
├── controller/
│   ├── BoardController.java          # GET /api/board (full board JSON), GET /api/board/events (SSE)
│   ├── McpController.java            # MCP server: GET /sse (connect), POST /mcp/message (JSON-RPC)
│   ├── ProjectController.java        # CRUD /api/projects
│   └── TicketController.java         # CRUD /api/tickets, PUT /api/tickets/reorder
└── service/
    └── BoardEventService.java        # SSE emitter registry, broadcasts on mutations
```

## API Endpoints

All mutations broadcast an SSE refresh event.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/board` | All projects with nested tickets |
| GET | `/api/board/events` | SSE stream (event: `refresh`) |
| POST | `/api/projects` | Create project `{name, description?}` |
| PUT | `/api/projects/{id}` | Update project `{name?, description?}` |
| DELETE | `/api/projects/{id}` | Delete project + cascade tickets |
| POST | `/api/tickets` | Create ticket `{projectId, title, description?}` |
| PUT | `/api/tickets/{id}` | Update ticket `{title?, description?, status?}` |
| PUT | `/api/tickets/reorder` | Reorder tickets `[id1, id2, ...]` |
| DELETE | `/api/tickets/{id}` | Delete ticket |

## Key Design Decisions

- No Thymeleaf — pure static HTML + JS fetches data from REST APIs. This avoids page reloads and enables real-time updates via SSE.
- SQLite over Postgres — embedded, zero-config, single-file DB. Makes the Docker image fully self-contained.
- `@JsonIgnore` on `Ticket.project` to break the Jackson circular reference. `Project.tickets` is serialized normally.
- `ProjectRepository.findAllWithTickets()` uses `JOIN FETCH` to avoid lazy loading issues outside JPA sessions.
- Controllers use `Map<String, String>` for request bodies — intentionally simple, no DTOs.
- MCP implemented directly (no Spring AI) — the protocol is simple JSON-RPC over SSE, and the Spring AI MCP starters had compatibility issues with Spring Boot 4.

## Build & Run

```bash
# Local
./mvnw spring-boot:run        # creates ./tasker.db, serves on :8080

# Docker
./tasker.sh start              # build + run detached
./tasker.sh stop               # stop container
./tasker.sh rebuild            # rebuild image + restart
```

## MCP Server

The app is an MCP server via a hand-rolled implementation in `McpController`. The MCP endpoint is at `/sse`.

### MCP Tools Exposed

| Tool | Description |
|------|-------------|
| `listProjects` | List all projects with their tickets |
| `createProject` | Create a new project (name, description?) |
| `deleteProject` | Delete a project and all its tickets |
| `createTicket` | Create a ticket in a project (projectId, title, description?) |
| `updateTicket` | Update a ticket (ticketId, title?, description?, status?) |
| `deleteTicket` | Delete a ticket |

All MCP tool calls also broadcast SSE refresh events, so the web UI updates in real time when a Claude agent makes changes.

### Connecting Claude

The MCP server is configured in this project's `.claude/settings.json`. When Tasker is running on `localhost:8080`, Claude Code in this repo automatically has access to the MCP tools.

For Claude Desktop, add to `claude_desktop_config.json` (Settings > Developer > Edit Config):

```json
{
  "mcpServers": {
    "tasker": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

### Usage with Claude

Claude can manage the board directly. Examples:
- "List my projects on Tasker"
- "Create a project called 'Side Projects' with a ticket 'Build CLI tool'"
- "Mark ticket 3 as done"
- "What tickets are in progress?"

All changes made via MCP update the web UI in real time via SSE.
