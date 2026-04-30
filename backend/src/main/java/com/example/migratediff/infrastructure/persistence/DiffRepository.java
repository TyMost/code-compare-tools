package com.example.migratediff.infrastructure.persistence;

import com.example.migratediff.domain.diff.DiffSummary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiffRepository {

    DiffSummary save(DiffSummary summary);

    Optional<DiffSummary> findById(String id);
}
