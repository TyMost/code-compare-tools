package com.example.migratediff.infrastructure.persistence.filesystem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * repo/index.json 对应的数据结构。
 */
public class RepoIndex {

    private List<RepoIndexEntry> entries = new ArrayList<>();

    public List<RepoIndexEntry> getEntries() {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        return entries;
    }

    public void setEntries(List<RepoIndexEntry> entries) {
        this.entries = entries;
    }

    public void upsert(String id, RepoIndexEntry template) {
        Optional<RepoIndexEntry> existing = getEntries().stream()
                .filter(entry -> entry.getId().equals(id))
                .findFirst();
        RepoIndexEntry target = existing.orElseGet(() -> {
            RepoIndexEntry newEntry = new RepoIndexEntry();
            newEntry.setId(id);
            getEntries().add(newEntry);
            return newEntry;
        });
        target.setAbsolutePath(template.getAbsolutePath());
        target.setBranchFrom(template.getBranchFrom());
        target.setBranchTo(template.getBranchTo());
        target.setUpdatedAt(template.getUpdatedAt());
    }

    public void sortByUpdatedAtDesc() {
        getEntries().sort(Comparator.comparing(RepoIndexEntry::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
    }
}
