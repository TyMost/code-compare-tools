package com.example.migratediff.domain.diff;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffFile {

    private String relativePath;
    @Builder.Default
    private List<DiffBlock> blocks = new ArrayList<>();
    private DiffType diffType;
    private DeltaType deltaType;
    
    /**
     * Git 提交哈希
     * 用于 FullFileContextStrategy 等需要访问完整文件内容的情况
     */
    private String commitHash;
    
    /**
     * Git 仓库路径
     * 用于 FullFileContextStrategy 等需要访问完整文件内容的情况
     */
    private String repoPath;
}
