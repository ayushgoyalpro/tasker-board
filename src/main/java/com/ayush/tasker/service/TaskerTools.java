package com.ayush.tasker.service;

import com.ayush.tasker.model.Project;
import com.ayush.tasker.model.Status;
import com.ayush.tasker.model.Ticket;
import com.ayush.tasker.repository.ProjectRepository;
import com.ayush.tasker.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskerTools {

    private final ProjectRepository projectRepository;
    private final TicketRepository ticketRepository;
    private final BoardEventService boardEventService;

    @Tool(description = "List all projects with their tickets")
    public List<Project> listProjects() {
        return projectRepository.findAllWithTickets();
    }

    @Tool(description = "Create a new project (column on the board)")
    public Project createProject(
            @ToolParam(description = "Project name") String name,
            @ToolParam(description = "Project description", required = false) String description) {
        var project = new Project();
        project.setName(name);
        project.setDescription(description != null ? description : "");
        project.setDisplayOrder((int) projectRepository.count());
        var saved = projectRepository.save(project);
        boardEventService.broadcast();
        return saved;
    }

    @Tool(description = "Delete a project and all its tickets")
    public String deleteProject(@ToolParam(description = "Project ID") Long projectId) {
        if (!projectRepository.existsById(projectId)) return "Project not found";
        projectRepository.deleteById(projectId);
        boardEventService.broadcast();
        return "Project deleted";
    }

    @Tool(description = "Create a new ticket in a project")
    public Ticket createTicket(
            @ToolParam(description = "Project ID to add the ticket to") Long projectId,
            @ToolParam(description = "Ticket title") String title,
            @ToolParam(description = "Ticket description", required = false) String description) {
        return projectRepository.findById(projectId)
                .map(project -> {
                    var ticket = new Ticket();
                    ticket.setTitle(title);
                    ticket.setDescription(description != null ? description : "");
                    ticket.setStatus(Status.TODO);
                    ticket.setProject(project);
                    ticket.setDisplayOrder((int) ticketRepository.countByProjectId(projectId));
                    var saved = ticketRepository.save(ticket);
                    boardEventService.broadcast();
                    return saved;
                })
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    @Tool(description = "Update a ticket's title, description, or status")
    public Ticket updateTicket(
            @ToolParam(description = "Ticket ID") Long ticketId,
            @ToolParam(description = "New title", required = false) String title,
            @ToolParam(description = "New description", required = false) String description,
            @ToolParam(description = "New status: TODO, IN_PROGRESS, or DONE", required = false) String status) {
        return ticketRepository.findById(ticketId)
                .map(ticket -> {
                    if (title != null) ticket.setTitle(title);
                    if (description != null) ticket.setDescription(description);
                    if (status != null) ticket.setStatus(Status.valueOf(status));
                    var saved = ticketRepository.save(ticket);
                    boardEventService.broadcast();
                    return saved;
                })
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
    }

    @Tool(description = "Delete a ticket")
    public String deleteTicket(@ToolParam(description = "Ticket ID") Long ticketId) {
        if (!ticketRepository.existsById(ticketId)) return "Ticket not found";
        ticketRepository.deleteById(ticketId);
        boardEventService.broadcast();
        return "Ticket deleted";
    }
}
