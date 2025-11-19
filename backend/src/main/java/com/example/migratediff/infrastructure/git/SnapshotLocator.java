package com.example.migratediff.infrastructure.git;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SnapshotLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotLocator.class);

    public SnapshotPair locate(Repository repository, Instant startTime, Instant endTime, SnapshotLocatorOptions options) throws IOException {
        if (repository == null) {
            throw new IllegalArgumentException("Repository is required");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Snapshot scan requires both startTime and endTime");
        }
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime must not be after endTime");
        }
        SnapshotLocatorOptions effectiveOptions = options != null ? options : SnapshotLocatorOptions.builder().build();
        List<Ref> refs = collectRefs(repository, effectiveOptions);
        if (refs.isEmpty()) {
            throw new IllegalStateException("No refs available for snapshot scan");
        }
        ObjectId earliestId = null;
        ObjectId latestId = null;
        Instant earliestInstant = null;
        Instant latestInstant = null;
        int scannedRefs = 0;
        for (Ref ref : refs) {
            if (ref == null || ref.getObjectId() == null) {
                continue;
            }
            scannedRefs++;
            try (RevWalk revWalk = new RevWalk(repository)) {
                revWalk.sort(RevSort.COMMIT_TIME_DESC, true);
                revWalk.markStart(revWalk.parseCommit(ref.getObjectId()));
                for (RevCommit commit : revWalk) {
                    Instant commitInstant = Instant.ofEpochSecond(commit.getCommitTime());
                    if (commitInstant.isAfter(endTime)) {
                        continue;
                    }
                    if (commitInstant.isBefore(startTime)) {
                        break;
                    }
                    if (latestInstant == null || commitInstant.isAfter(latestInstant)) {
                        latestInstant = commitInstant;
                        latestId = commit.getId().copy();
                        LOGGER.debug("Snapshot latest candidate updated: ref={}, commit={}, time={}", ref.getName(), latestId.name(), latestInstant);
                    }
                    if (earliestInstant == null || commitInstant.isBefore(earliestInstant)) {
                        earliestInstant = commitInstant;
                        earliestId = commit.getId().copy();
                        LOGGER.debug("Snapshot earliest candidate updated: ref={}, commit={}, time={}", ref.getName(), earliestId.name(), earliestInstant);
                    }
                }
            }
        }
        if (earliestId == null || latestId == null) {
            throw new IllegalStateException("No commits found in the requested time range");
        }
        LOGGER.debug("Snapshot locator scanned {} refs; earliest={}, latest={}", scannedRefs, earliestInstant, latestInstant);
        return new SnapshotPair(earliestId, earliestInstant, latestId, latestInstant);
    }

    private List<Ref> collectRefs(Repository repository, SnapshotLocatorOptions options) throws IOException {
        RefDatabase refDatabase = repository.getRefDatabase();
        Map<String, Ref> refs = new LinkedHashMap<>();
        addRefs(refs, refDatabase.getRefsByPrefix(Constants.R_HEADS));
        if (options.isIncludeRemoteRefs()) {
            addRefs(refs, refDatabase.getRefsByPrefix(Constants.R_REMOTES));
        }
        if (options.isIncludeTags()) {
            addRefs(refs, refDatabase.getRefsByPrefix(Constants.R_TAGS));
        }
        // HEAD is not included in refsByPrefix so we add it explicitly for completeness.
        Ref head = repository.exactRef(Constants.HEAD);
        if (head != null) {
            refs.putIfAbsent(head.getName(), head);
        }
        List<Ref> ordered = new ArrayList<>(refs.values());
        if (options.getMaxRefs() > 0 && ordered.size() > options.getMaxRefs()) {
            LOGGER.warn("Ref list truncated from {} to {} entries to honor snapshot maxRefs", ordered.size(), options.getMaxRefs());
            return new ArrayList<>(ordered.subList(0, options.getMaxRefs()));
        }
        return ordered;
    }

    private void addRefs(Map<String, Ref> sink, List<Ref> refs) {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        for (Ref ref : refs) {
            if (ref != null) {
                sink.putIfAbsent(ref.getName(), ref);
            }
        }
    }
}
