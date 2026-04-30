package com.example.migratediff.infrastructure.git;

import com.example.migratediff.infrastructure.config.ScanFilterProperties;
import org.eclipse.jgit.diff.DiffEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 扫描文件过滤器
 * 根据配置过滤不需要处理的文件
 */
@Component
public class ScanFileFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanFileFilter.class);

    private final ScanFilterProperties filterProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 默认测试文件模式
    private static final List<String> DEFAULT_TEST_PATTERNS = Arrays.asList(
            "*test.java", "*Test.java", "*TEST.java"
    );

    public ScanFileFilter(ScanFilterProperties filterProperties) {
        this.filterProperties = filterProperties;
    }

    /**
     * 判断文件是否应该被包含在扫描结果中
     * 
     * @param diffEntry Git差异条目
     * @return true表示应该包含，false表示应该过滤掉
     */
    public boolean shouldInclude(DiffEntry diffEntry) {
        // 如果未启用过滤，则包含所有文件
        if (!filterProperties.isEnabled()) {
            return true;
        }

        String filePath = getFilePath(diffEntry);
        if (!StringUtils.hasText(filePath)) {
            LOGGER.debug("文件路径为空，跳过处理: {}", diffEntry);
            return false;
        }

        // 检查文件扩展名
        if (!matchesFileExtensions(filePath)) {
            LOGGER.debug("文件扩展名不匹配，跳过: {}", filePath);
            return false;
        }

        // 检查测试文件排除
        if (shouldExcludeTestFiles(filePath)) {
            LOGGER.debug("匹配测试文件模式，跳过: {}", filePath);
            return false;
        }

        // 检查自定义排除模式
        if (matchesExcludePatterns(filePath)) {
            LOGGER.debug("匹配排除模式，跳过: {}", filePath);
            return false;
        }

        return true;
    }

    /**
     * 获取文件路径
     */
    private String getFilePath(DiffEntry diffEntry) {
        // 优先使用新路径，如果不存在则使用旧路径
        String newPath = diffEntry.getNewPath();
        String oldPath = diffEntry.getOldPath();

        // 处理删除文件的情况（新路径为"/dev/null"）
        if ("/dev/null".equals(newPath) && StringUtils.hasText(oldPath)) {
            return oldPath;
        }

        // 处理新增文件的情况（旧路径为"/dev/null"）
        if ("/dev/null".equals(oldPath) && StringUtils.hasText(newPath)) {
            return newPath;
        }

        // 正常情况，使用新路径
        return StringUtils.hasText(newPath) ? newPath : oldPath;
    }

    /**
     * 检查文件扩展名是否匹配
     */
    private boolean matchesFileExtensions(String filePath) {
        List<String> extensions = filterProperties.getFileExtensions();
        if (extensions == null || extensions.isEmpty()) {
            return true; // 如果没有配置扩展名限制，则包含所有文件
        }

        String fileName = new File(filePath).getName();
        for (String extension : extensions) {
            if (fileName.endsWith(extension)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否应该排除测试文件
     */
    private boolean shouldExcludeTestFiles(String filePath) {
        if (!filterProperties.isExcludeTestFiles()) {
            return false;
        }

        String fileName = new File(filePath).getName();
        for (String pattern : DEFAULT_TEST_PATTERNS) {
            if (pathMatcher.match(pattern, fileName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否匹配排除模式
     */
    private boolean matchesExcludePatterns(String filePath) {
        List<String> patterns = filterProperties.getExcludePatterns();
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }

        String fileName = new File(filePath).getName();
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, fileName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取所有排除模式的集合（用于日志记录）
     */
    public Set<String> getAllExcludePatterns() {
        Set<String> allPatterns = new HashSet<>();
        
        if (filterProperties.isExcludeTestFiles()) {
            allPatterns.addAll(DEFAULT_TEST_PATTERNS);
        }
        
        List<String> customPatterns = filterProperties.getExcludePatterns();
        if (customPatterns != null) {
            allPatterns.addAll(customPatterns);
        }
        
        return allPatterns;
    }
}
