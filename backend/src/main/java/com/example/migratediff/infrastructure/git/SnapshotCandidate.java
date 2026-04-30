package com.example.migratediff.infrastructure.git;

import org.eclipse.jgit.lib.ObjectId;
import java.time.Instant;
import java.util.Objects;

/**
 * 简化的快照候选类，用于SnapshotLocator
 */
public class SnapshotCandidate {
    private final ObjectId objectId;
    private final Instant instant;
    private final String refName;

    public SnapshotCandidate(ObjectId objectId, Instant instant, String refName) {
        this.objectId = Objects.requireNonNull(objectId);
        this.instant = Objects.requireNonNull(instant);
        this.refName = refName;
    }

    public ObjectId getObjectId() {
        return objectId;
    }

    public Instant getInstant() {
        return instant;
    }

    public String getRefName() {
        return refName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SnapshotCandidate that = (SnapshotCandidate) o;
        return Objects.equals(objectId, that.objectId) &&
               Objects.equals(instant, that.instant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectId, instant);
    }

    @Override
    public String toString() {
        return "SnapshotCandidate{" +
                "objectId=" + objectId +
                ", instant=" + instant +
                ", refName='" + refName + '\'' +
                '}';
    }
}
