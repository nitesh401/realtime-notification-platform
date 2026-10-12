package com.example.notify.worker.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterRepository extends JpaRepository<DeadLetter, UUID> {
    List<DeadLetter> findByStatusOrderByCreatedAtAsc(DeadLetterStatus status, Pageable pageable);
}
