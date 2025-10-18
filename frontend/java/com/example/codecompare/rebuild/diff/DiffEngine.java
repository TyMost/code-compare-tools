package com.example.codecompare.rebuild.diff;

import com.github.difflib.patch.Patch;

import java.util.List;

/**
 * 自定义 diff 引擎接口，默认实现基于 java-diff-utils。
 */
public interface DiffEngine {

    Patch<String> compare(List<String> sourceLines, List<String> targetLines);
}
