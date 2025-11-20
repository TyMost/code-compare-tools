package com.example.migratediff.infrastructure.persistence;

import com.example.migratediff.domain.repo.RepoConfig;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepoRepository {

    RepoConfig save(RepoConfig repoConfig);

    Optional<RepoConfig> findById(String id);

    List<RepoConfig> findAll();

    void deleteById(String id);
}
