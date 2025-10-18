package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.repository.model.FileRecord;
import org.springframework.context.ApplicationEvent;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * 扫描完成事件，向统计模块广播最新的扫描摘要信息。
 */
public class ScanCompletedEvent extends ApplicationEvent {

    private final ScanSummary summary;
    private final List<FileRecord> changedRecords;
    private final boolean fullRescan;

    public ScanCompletedEvent(Object source,
                              ScanSummary summary,
                              List<FileRecord> changedRecords,
                              boolean fullRescan) {
        super(source);
        this.summary = summary;
        this.changedRecords = CollectionUtils.isEmpty(changedRecords)
                ? Collections.emptyList()
                : Collections.unmodifiableList(changedRecords);
        this.fullRescan = fullRescan;
    }

    public ScanSummary getSummary() {
        return summary;
    }

    public List<FileRecord> getChangedRecords() {
        return changedRecords;
    }

    public boolean isFullRescan() {
        return fullRescan;
    }
}
