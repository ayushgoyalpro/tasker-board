# Tasker

Personal kanban/notes board — a lightweight self-hosted alternative to Jira for personal use.

## Mission

A simple task board where columns represent projects and cards represent tickets with statuses. Designed to be managed both through the web UI and programmatically via a Claude agent using Spring AI MCP (planned).

## Architecture

- **Backend**: Spring Boot 4.0.5, Java 21
- **Frontend**: Static HTML + vanilla JS (`src/main/resources/static/index.html`) — no framework, no Thymeleaf
- **Database**: SQLite via `sqlite-jdbc` + Hibernate community dialect. Single file at `$TASKER_DB_PATH` (default `./tasker.db`). Schema auto-managed by Hibernate `ddl-auto: update`
- **Real-time**: Server-Sent Events (SSE) at `/api/board/events` — any mutation broadcasts a `refresh` event so all connected browsers re-render
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
│   ├── ProjectController.java        # CRUD /api/projects
│   └── TicketController.java         # CRUD /api/tickets, PUT /api/tickets/reorder
└── service/
    ├── BoardEventService.java        # SSE emitter registry, broadcasts on mutations
    └── TaskerTools.java              # MCP tool definitions (@Tool annotated)
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

The app is an MCP server via `spring-ai-starter-mcp-server-webmvc` (SSE transport). The MCP endpoint is at `/sse`.

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

### Connecting Claude Code

Add to `~/.claude/claude_code_config.json`:

```json
{
  "mcpServers": {
    "tasker": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

### Connecting Claude Desktop

Add to Claude Desktop settings (`claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "tasker": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```
