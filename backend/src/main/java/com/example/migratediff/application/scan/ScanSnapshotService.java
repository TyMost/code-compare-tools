package com.example.migratediff.application.scan;

import com.example.migratediff.api.dto.ScanResponseDTO;
import com.example.migratediff.infrastructure.persistence.ScanReportRepository;
import com.example.migratediff.infrastructure.persistence.ScanSnapshotRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
public class ScanSnapshotService {

    private final ScanSnapshotRepository snapshotRepository;
    private final ScanReportRepository scanReportRepository;

    public ScanSnapshotService(ScanSnapshotRepository snapshotRepository,
                               @Nullable ScanReportRepository scanReportRepository) {
        this.snapshotRepository = snapshotRepository;
        this.scanReportRepository = scanReportRepository;
    }

    public void saveSnapshot(ScanReport report, ScanResponseDTO responseDTO) {
        if (report == null || responseDTO == null) {
            return;
        }
        ScanSnapshot snapshot = ScanSnapshot.builder()
                .repoId(report.getRepoId())
                .repoName(report.getRepoName())
                .taskId(report.getTaskId())
                .mode(report.getMode())
                .persisted(report.isPersisted())
                .cachedAt(report.getGeneratedAt() != null ? report.getGeneratedAt() : Instant.now())
                .response(responseDTO)
                .build();
        snapshotRepository.save(snapshot);
    }

    public List<ScanSnapshot> listSnapshots() {
        List<ScanSnapshot> snapshots = snapshotRepository.findAll();
        if (snapshots == null) {
            return Collections.emptyList();
        }
        return snapshots;
    }

    public void deleteAll() {
        snapshotRepository.deleteAll();
        if (scanReportRepository != null) {
            scanReportRepository.deleteAll();
        }
    }
}
