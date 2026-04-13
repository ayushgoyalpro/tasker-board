package com.ayush.tasker.controller;

import com.ayush.tasker.model.Status;
import com.ayush.tasker.repository.ProjectRepository;
import com.ayush.tasker.repository.TicketRepository;
import com.ayush.tasker.service.BoardEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequiredArgsConstructor
public class McpController {

    private final ObjectMapper mapper;
    private final ProjectRepository projectRepository;
    private final TicketRepository ticketRepository;
    private final BoardEventService boardEventService;

    private final Map<String, SseEmitter> sessions = new ConcurrentHashMap<>();

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect() throws IOException {
        var emitter = new SseEmitter(0L);
        var sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, emitter);
        emitter.onCompletion(() -> sessions.remove(sessionId));
        emitter.onTimeout(() -> sessions.remove(sessionId));
        emitter.onError(e -> sessions.remove(sessionId));

        emitter.send(SseEmitter.event()
                .name("endpoint")
                .data("/mcp/message?sessionId=" + sessionId));
        return emitter;
    }

    @PostMapping(value = "/mcp/message", produces = MediaType.APPLICATION_JSON_VALUE)
    public void handleMessage(@RequestParam String sessionId, @RequestBody JsonNode request) throws IOException {
        var emitter = sessions.get(sessionId);
        if (emitter == null) return;

        var method = request.path("method").asString("");
        var id = request.path("id");

        ObjectNode response = switch (method) {
            case "initialize" -> initializeResponse(id);
            case "tools/list" -> toolsListResponse(id);
            case "tools/call" -> toolsCallResponse(id, request.path("params"));
            case "notifications/initialized", "notifications/cancelled" -> null; // no response
            default -> errorResponse(id, -32601, "Method not found: " + method);
        };

        if (response != null) {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(mapper.writeValueAsString(response)));
        }
    }

    private ObjectNode initializeResponse(JsonNode id) {
        var result = mapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        var caps = result.putObject("capabilities");
        caps.putObject("tools");
        var info = result.putObject("serverInfo");
        info.put("name", "tasker");
        info.put("version", "1.0.0");
        return jsonRpcResponse(id, result);
    }

    private ObjectNode toolsListResponse(JsonNode id) {
        var tools = mapper.createArrayNode();
        tools.add(tool("listProjects", "List all projects with their tickets", mapper.createObjectNode()));
        tools.add(tool("createProject", "Create a new project (column on the board)",
                props(Map.of("name", prop("string", "Project name"),
                        "description", prop("string", "Project description (optional)")))));
        tools.add(tool("deleteProject", "Delete a project and all its tickets",
                props(Map.of("projectId", prop("number", "Project ID")))));
        tools.add(tool("createTicket", "Create a new ticket in a project",
                props(Map.of("projectId", prop("number", "Project ID"),
                        "title", prop("string", "Ticket title"),
                        "description", prop("string", "Ticket description (optional)")))));
        tools.add(tool("updateTicket", "Update a ticket's title, description, or status",
                props(Map.of("ticketId", prop("number", "Ticket ID"),
                        "title", prop("string", "New title (optional)"),
                        "description", prop("string", "New description (optional)"),
                        "status", prop("string", "New status: TODO, IN_PROGRESS, or DONE (optional)")))));
        tools.add(tool("deleteTicket", "Delete a ticket",
                props(Map.of("ticketId", prop("number", "Ticket ID")))));

        var result = mapper.createObjectNode();
        result.set("tools", tools);
        return jsonRpcResponse(id, result);
    }

    private ObjectNode toolsCallResponse(JsonNode id, JsonNode params) {
        var toolName = params.path("name").asString();
        var args = params.path("arguments");

        try {
            String resultText = switch (toolName) {
                case "listProjects" -> mapper.writeValueAsString(projectRepository.findAllWithTickets());
                case "createProject" -> {
                    var p = new com.ayush.tasker.model.Project();
                    p.setName(args.path("name").asString());
                    p.setDescription(args.path("description").asString(""));
                    p.setDisplayOrder((int) projectRepository.count());
                    var saved = projectRepository.save(p);
                    boardEventService.broadcast();
                    yield mapper.writeValueAsString(saved);
                }
                case "deleteProject" -> {
                    long pid = args.path("projectId").asLong();
                    if (!projectRepository.existsById(pid)) yield "Project not found";
                    projectRepository.deleteById(pid);
                    boardEventService.broadcast();
                    yield "Project deleted";
                }
                case "createTicket" -> {
                    long pid = args.path("projectId").asLong();
                    var project = projectRepository.findById(pid).orElseThrow(() -> new IllegalArgumentException("Project not found"));
                    var t = new com.ayush.tasker.model.Ticket();
                    t.setTitle(args.path("title").asString());
                    t.setDescription(args.path("description").asString(""));
                    t.setStatus(Status.TODO);
                    t.setProject(project);
                    t.setDisplayOrder((int) ticketRepository.countByProjectId(pid));
                    var saved = ticketRepository.save(t);
                    boardEventService.broadcast();
                    yield mapper.writeValueAsString(saved);
                }
                case "updateTicket" -> {
                    long tid = args.path("ticketId").asLong();
                    var ticket = ticketRepository.findById(tid).orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
                    if (args.has("title")) ticket.setTitle(args.path("title").asString());
                    if (args.has("description")) ticket.setDescription(args.path("description").asString());
                    if (args.has("status")) ticket.setStatus(Status.valueOf(args.path("status").asString()));
                    var saved = ticketRepository.save(ticket);
                    boardEventService.broadcast();
                    yield mapper.writeValueAsString(saved);
                }
                case "deleteTicket" -> {
                    long tid = args.path("ticketId").asLong();
                    if (!ticketRepository.existsById(tid)) yield "Ticket not found";
                    ticketRepository.deleteById(tid);
                    boardEventService.broadcast();
                    yield "Ticket deleted";
                }
                default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
            };

            var result = mapper.createObjectNode();
            var content = result.putArray("content");
            var item = content.addObject();
            item.put("type", "text");
            item.put("text", resultText);
            return jsonRpcResponse(id, result);

        } catch (Exception e) {
            return errorResponse(id, -32000, e.getMessage());
        }
    }

    // --- helpers ---

    private ObjectNode jsonRpcResponse(JsonNode id, ObjectNode result) {
        var resp = mapper.createObjectNode();
        resp.put("jsonrpc", "2.0");
        resp.set("id", id);
        resp.set("result", result);
        return resp;
    }

    private ObjectNode errorResponse(JsonNode id, int code, String message) {
        var resp = mapper.createObjectNode();
        resp.put("jsonrpc", "2.0");
        resp.set("id", id);
        var err = resp.putObject("error");
        err.put("code", code);
        err.put("message", message);
        return resp;
    }

    private ObjectNode tool(String name, String description, ObjectNode inputSchema) {
        var t = mapper.createObjectNode();
        t.put("name", name);
        t.put("description", description);
        inputSchema.put("type", "object");
        t.set("inputSchema", inputSchema);
        return t;
    }

    private ObjectNode props(Map<String, ObjectNode> properties) {
        var schema = mapper.createObjectNode();
        var propsNode = schema.putObject("properties");
        properties.forEach(propsNode::set);
        return schema;
    }

    private ObjectNode prop(String type, String description) {
        var p = mapper.createObjectNode();
        p.put("type", type);
        p.put("description", description);
        return p;
    }
}
