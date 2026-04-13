package com.ayush.tasker.repository;

import com.ayush.tasker.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByProjectIdOrderByDisplayOrderAsc(Long projectId);

    long countByProjectId(Long projectId);
}