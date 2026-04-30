package com.example.migratediff.infrastructure.persistence;

import com.example.migratediff.domain.coverage.CoverageSummary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CoverageRepository {

    void save(CoverageSummary summary);

    Optional<CoverageSummary> findByTaskId(String taskId);
}
