package com.example.migratediff.application.scan;

/**
 * 迁移任务的键，用于索引和查找迁移任务
 */
public class MigrationKey {
    private final String taskId;
    private final String filePath;

    public MigrationKey(String taskId, String filePath) {
        this.taskId = taskId != null ? taskId : "";
        this.filePath = filePath != null ? filePath : "";
    }

    public String getTaskId() {
        return taskId;
    }

    public String getFilePath() {
        return filePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MigrationKey)) return false;
        MigrationKey that = (MigrationKey) o;
        return taskId.equals(that.taskId) && filePath.equals(that.filePath);
    }

    @Override
    public int hashCode() {
        int result = taskId.hashCode();
        result = 31 * result + filePath.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("MigrationKey{taskId='%s', filePath='%s'}", taskId, filePath);
    }
}
