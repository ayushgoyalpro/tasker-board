package com.ayush.tasker.repository;

import com.ayush.tasker.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @Query("SELECT DISTINCT p FROM Project p LEFT JOIN FETCH p.tickets ORDER BY p.displayOrder")
    List<Project> findAllWithTickets();
}