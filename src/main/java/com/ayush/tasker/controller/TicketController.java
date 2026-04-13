package com.ayush.tasker.controller;

import com.ayush.tasker.model.Status;
import com.ayush.tasker.model.Ticket;
import com.ayush.tasker.repository.ProjectRepository;
import com.ayush.tasker.repository.TicketRepository;
import com.ayush.tasker.service.BoardEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final BoardEventService boardEventService;

    @PostMapping
    public ResponseEntity<Ticket> create(@RequestBody Map<String, String> body) {
        final long projectId = Long.parseLong(body.get("projectId"));
        return projectRepository.findById(projectId)
                .map(project -> {
                    var ticket = new Ticket();
                    ticket.setTitle(body.get("title"));
                    ticket.setDescription(body.getOrDefault("description", ""));
                    ticket.setStatus(Status.TODO);
                    ticket.setProject(project);
                    ticket.setDisplayOrder((int) ticketRepository.countByProjectId(projectId));
                    var saved = ticketRepository.save(ticket);
                    boardEventService.broadcast();
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Ticket> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ticketRepository.findById(id)
                .map(ticket -> {
                    if (body.containsKey("title")) ticket.setTitle(body.get("title"));
                    if (body.containsKey("description")) ticket.setDescription(body.get("description"));
                    if (body.containsKey("status")) ticket.setStatus(Status.valueOf(body.get("status")));
                    var saved = ticketRepository.save(ticket);
                    boardEventService.broadcast();
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(@RequestBody List<Long> ticketIds) {
        for (int i = 0; i < ticketIds.size(); i++) {
            int order = i;
            ticketRepository.findById(ticketIds.get(i)).ifPresent(ticket -> {
                ticket.setDisplayOrder(order);
                ticketRepository.save(ticket);
            });
        }
        boardEventService.broadcast();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ticketRepository.deleteById(id);
        boardEventService.broadcast();
        return ResponseEntity.ok().build();
    }
}
