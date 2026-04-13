package com.ayush.tasker.controller;

import com.ayush.tasker.model.Project;
import com.ayush.tasker.repository.ProjectRepository;
import com.ayush.tasker.service.BoardEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
public class BoardController {

    private final ProjectRepository projectRepository;
    private final BoardEventService boardEventService;

    @GetMapping
    public List<Project> board() {
        return projectRepository.findAllWithTickets();
    }

    @GetMapping(value = "/events", produces = "text/event-stream")
    public SseEmitter events() {
        return boardEventService.subscribe();
    }
}
