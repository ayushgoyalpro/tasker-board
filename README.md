# Tasker

A lightweight self-hosted kanban board for personal use — a simple alternative to Jira. Manage tasks through a web UI or programmatically via Claude agents using MCP.

## Features

- **Projects as columns** — each project is a board column; tickets are cards within it
- **Ticket statuses** — `TODO`, `IN_PROGRESS`, `DONE`
- **Drag-and-drop reordering** — reorder tickets within a project
- **Move tickets across projects** — reassign a ticket to a different project
- **Real-time updates** — any change (from the UI or via MCP) instantly refreshes all open browser tabs via Server-Sent Events
- **MCP server built-in** — Claude agents can read and manage your board directly
- **Zero external dependencies** — SQLite database, single Docker image, no Postgres, no Redis

## Running on Your Machine

### Option 1 — Docker (recommended)

```bash
./tasker.sh start     # build image and start container
./tasker.sh stop      # stop container
./tasker.sh rebuild   # rebuild image and restart
```

Open [http://localhost:8080](http://localhost:8080).

The database is stored at `./tasker.db` by default. Set `TASKER_DB_PATH` to change the location.

### Option 2 — Local (Java 21 required)

```bash
./mvnw spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080).

## MCP — Claude Agent Integration

Tasker is an MCP server. Claude agents (Claude Code, Claude Desktop) can list, create, update, and delete projects and tickets directly, with all changes reflected in the web UI in real time.

### Tools available to Claude

| Tool | Description |
|------|-------------|
| `listProjects` | List all projects with their tickets |
| `createProject` | Create a new project |
| `deleteProject` | Delete a project and all its tickets |
| `createTicket` | Create a ticket in a project |
| `updateTicket` | Update a ticket's title, description, or status |
| `deleteTicket` | Delete a ticket |

### Connecting Claude Code

The MCP server is pre-configured in `.claude/settings.json`. When Tasker is running on `localhost:8080`, Claude Code in this repo automatically has access to the tools — no extra setup needed.

### Connecting Claude Desktop

Add to `claude_desktop_config.json` (Settings → Developer → Edit Config):

```json
{
  "mcpServers": {
    "tasker": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

Then restart Claude Desktop. You can now ask Claude things like:

- "List my projects on Tasker"
- "Create a project called 'Side Projects' with a ticket 'Build CLI tool'"
- "Mark ticket 3 as done"
- "What tickets are in progress?"

## Tech Stack

- **Backend**: Spring Boot 4, Java 21
- **Frontend**: Static HTML + vanilla JS (no framework)
- **Database**: SQLite (embedded, single file)
- **Real-time**: Server-Sent Events (SSE)
- **MCP**: Hand-rolled JSON-RPC over SSE — no Spring AI dependency