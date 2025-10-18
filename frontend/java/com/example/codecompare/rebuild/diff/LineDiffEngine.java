package com.example.codecompare.rebuild.diff;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 默认的行级 diff 引擎实现。
 */
@Component
public class LineDiffEngine implements DiffEngine {

    @Override
    public Patch<String> compare(List<String> sourceLines, List<String> targetLines) {
        List<String> safeSource = sourceLines == null ? Collections.emptyList() : sourceLines;
        List<String> safeTarget = targetLines == null ? Collections.emptyList() : targetLines;
        return DiffUtils.diff(safeSource, safeTarget);
    }
}
