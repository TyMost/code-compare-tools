package com.example.migratediff.domain.repo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoBranch {

    private String name;
    private String commitId;

    /**
     * 时间区间下界（含），用于按照时间检索提交。
     */
    private Instant timeFrom;

    /**
     * 时间区间上界（含），用于按照时间检索提交。
     */
    private Instant timeTo;

    /**
     * 时间检索使用的参考引用（未提供时可退回分支名或 HEAD）。
     */
    private String refHint;
}
