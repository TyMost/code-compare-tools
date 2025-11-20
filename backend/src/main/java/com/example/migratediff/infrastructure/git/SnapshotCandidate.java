package com.example.migratediff.infrastructure.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;

import java.time.Instant;
import java.util.Objects;

/**
 * 快照候选者，表示在时间范围内找到的提交候选项
 * 支持JDK 8兼容性实现
 */
public class SnapshotCandidate {

    private final ObjectId objectId;
    private final Instant instant;
    private final String refName;

    public SnapshotCandidate(ObjectId objectId, Instant instant, String refName) {
        this.objectId = Objects.requireNonNull(objectId, "ObjectId cannot be null");
        this.instant = Objects.requireNonNull(instant, "Instant cannot be null");
        this.refName = refName; // refName可以为null
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
               Objects.equals(instant, that.instant) &&
               Objects.equals(refName, that.refName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectId, instant, refName);
    }

    @Override
    public String toString() {
        return String.format("SnapshotCandidate{objectId=%s, instant=%s, refName='%s'}", 
            objectId.name(), instant, refName);
    }
}
