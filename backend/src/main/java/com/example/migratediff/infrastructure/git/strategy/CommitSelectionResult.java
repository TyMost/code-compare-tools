package com.example.migratediff.infrastructure.git.strategy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.jgit.lib.ObjectId;

/**
 * 提交选择结果，包含提交ID和对应的分支名称
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommitSelectionResult {
    private ObjectId commit;
    private String branchName;
    
    public static CommitSelectionResult of(ObjectId commit, String branchName) {
        return new CommitSelectionResult(commit, branchName);
    }
    
    public static CommitSelectionResult empty() {
        return new CommitSelectionResult(null, null);
    }
    
    public boolean isValid() {
        return commit != null && branchName != null;
    }
}
