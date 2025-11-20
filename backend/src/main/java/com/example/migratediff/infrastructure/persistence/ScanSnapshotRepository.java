package com.example.migratediff.infrastructure.persistence;

import com.example.migratediff.application.scan.ScanSnapshot;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScanSnapshotRepository {

    void save(ScanSnapshot snapshot);

    List<ScanSnapshot> findAll();

    void deleteAll();
}
