package com.example.migratediff.infrastructure.persistence;

import com.example.migratediff.application.scan.ScanReport;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScanReportRepository {

    void save(ScanReport report);

    Optional<ScanReport> find(String taskId);

    void delete(String taskId);

    void deleteAll();
}
