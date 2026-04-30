package com.example.migratediff.infrastructure.persistence;

import com.example.migratediff.domain.migration.MigrationTask;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MigrationRepository {

    MigrationTask save(MigrationTask task);

    Optional<MigrationTask> findById(String id);
}
