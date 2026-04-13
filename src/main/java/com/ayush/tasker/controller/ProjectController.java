package com.ayush.tasker.controller;

import com.ayush.tasker.model.Project;
import com.ayush.tasker.repository.ProjectRepository;
import com.ayush.tasker.service.BoardEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final BoardEventService boardEventService;

    @PostMapping
    public ResponseEntity<Project> create(@RequestBody Map<String, String> body) {
        var project = new Project();
        project.setName(body.get("name"));
        project.setDescription(body.getOrDefault("description", ""));
        project.setDisplayOrder((int) projectRepository.count());
        var saved = projectRepository.save(project);
        boardEventService.broadcast();
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return projectRepository.findById(id)
                .map(p -> {
                    if (body.containsKey("name")) p.setName(body.get("name"));
                    if (body.containsKey("description")) p.setDescription(body.get("description"));
                    var saved = projectRepository.save(p);
                    boardEventService.broadcast();
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectRepository.deleteById(id);
        boardEventService.broadcast();
        return ResponseEntity.ok().build();
    }
}
