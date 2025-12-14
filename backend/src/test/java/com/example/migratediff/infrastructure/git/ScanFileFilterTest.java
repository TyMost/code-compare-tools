package com.example.migratediff.infrastructure.git;

import com.example.migratediff.infrastructure.config.ScanFilterProperties;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.FileMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 扫描文件过滤器测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScanFileFilterTest {

    private ScanFilterProperties filterProperties;
    private ScanFileFilter scanFileFilter;

    @BeforeEach
    void setUp() {
        filterProperties = new ScanFilterProperties();
        scanFileFilter = new ScanFileFilter(filterProperties);
    }

    @Test
    void testFilterDisabled_ShouldIncludeAllFiles() {
        // Given
        filterProperties.setEnabled(false);
        DiffEntry diffEntry = createMockDiffEntry("test.txt");

        // When & Then
        assertTrue(scanFileFilter.shouldInclude(diffEntry));
    }

    @Test
    void testFileExtensionsMatch_ShouldIncludeFile() {
        // Given
        filterProperties.setEnabled(true);
        filterProperties.setFileExtensions(Arrays.asList(".java", ".xml"));
        DiffEntry diffEntry = createMockDiffEntry("UserService.java");

        // When & Then
        assertTrue(scanFileFilter.shouldInclude(diffEntry));
    }

    @Test
    void testFileExtensionsNotMatch_ShouldExcludeFile() {
        // Given
        filterProperties.setEnabled(true);
        filterProperties.setFileExtensions(Arrays.asList(".java", ".xml"));
        DiffEntry diffEntry = createMockDiffEntry("script.py");

        // When & Then
        assertFalse(scanFileFilter.shouldInclude(diffEntry));
    }

    @Test
    void testEmptyFileExtensions_ShouldIncludeAllFiles() {
        // Given
        filterProperties.setEnabled(true);
        filterProperties.setFileExtensions(Arrays.asList());
        DiffEntry diffEntry = createMockDiffEntry("anyfile.txt");

        // When & Then
        assertTrue(scanFileFilter.shouldInclude(diffEntry));
    }

    @Test
    void testExcludeTestFilesEnabled_ShouldExcludeTestFiles() {
        // Given
        filterProperties.setEnabled(true);
        filterProperties.setFileExtensions(Arrays.asList(".java"));
        filterProperties.setExcludeTestFiles(true);

        // Test various test file patterns
        assertTrue(shouldExcludeTestFile("UserServiceTest.java"));
        assertTrue(shouldExcludeTestFile("UserServiceTEST.java"));
        assertTrue(shouldExcludeTestFile("UserServiceTest.java"));
        assertTrue(shouldExcludeTestFile("servicetest.java"));
    }

    @Test
    void testExcludeTestFilesDisabled_ShouldIncludeTestFiles() {
        // Given
        filterProperties.setEnabled(true);
        filterProperties.setFileExtensions(Arrays.asList(".java"));
        filterProperties.setExcludeTestFiles(false);

        // When & Then
        DiffEntry diffEntry = createMockDiffEntry("UserServiceTest.java");
        assertTrue(scanFileFilter.shouldInclude(diffEntry));
    }

    @Test
    void testCustomExcludePatterns_ShouldExcludeMatchingFiles() {
        // Given
        filterProperties.setEnabled(true);
        filterProperties.setFileExtensions(Arrays.asList(".java"));
        filterProperties.setExcludePatterns(Arrays.asList("*Temp*.java", "Mock*.java"));

        // When & Then
        assertTrue(shouldExcludeByCustomPattern("UserTempService.java"));
        assertTrue(shouldExcludeByCustomPattern("MockUserService.java"));
        assertFalse(shouldExcludeByCustomPattern("UserService.java"));
    }

    @Test
    void testCombinedFilters_ShouldApplyAllRules() {
        // Given
        filterProperties.setEnabled(true);
        filterProperties.setFileExtensions(Arrays.asList(".java", ".xml"));
        filterProperties.setExcludeTestFiles(true);
        filterProperties.setExcludePatterns(Arrays.asList("Temp*.*"));

        // Test cases
        assertTrue(shouldIncludeByCombinedFilters("UserService.java")); // Normal file
        assertFalse(shouldIncludeByCombinedFilters("UserServiceTest.java")); // Test file
        assertFalse(shouldIncludeByCombinedFilters("TempFile.java")); // Custom exclude pattern
        assertFalse(shouldIncludeByCombinedFilters("script.py")); // Wrong extension
        assertTrue(shouldIncludeByCombinedFilters("config.xml")); // Valid XML file
    }

    @Test
    void testDeletedFile_ShouldUseOldPath() {
        // Given
        filterProperties.setEnabled(true);
        filterProperties.setFileExtensions(Arrays.asList(".java"));
        DiffEntry diffEntry = createMockDiffEntryForDeletion("UserService.java");

        // When & Then
        assertTrue(scanFileFilter.shouldInclude(diffEntry));
    }

    @Test
    void testAddedFile_ShouldUseNewPath() {
        // Given
        filterProperties.setEnabled(true);
        filterProperties.setFileExtensions(Arrays.asList(".java"));
        DiffEntry diffEntry = createMockDiffEntryForAddition("UserService.java");

        // When & Then
        assertTrue(scanFileFilter.shouldInclude(diffEntry));
    }

    @Test
    void testGetAllExcludePatterns_ShouldIncludeDefaultAndCustomPatterns() {
        // Given
        filterProperties.setExcludeTestFiles(true);
        filterProperties.setExcludePatterns(Arrays.asList("Temp*.*", "Mock*.java"));

        // When
        java.util.Set<String> allPatterns = scanFileFilter.getAllExcludePatterns();

        // Then
        assertTrue(allPatterns.contains("*test.java"));
        assertTrue(allPatterns.contains("*Test.java"));
        assertTrue(allPatterns.contains("*TEST.java"));
        assertTrue(allPatterns.contains("Temp*.*"));
        assertTrue(allPatterns.contains("Mock*.java"));
    }

    // Helper methods
    private boolean shouldExcludeTestFile(String fileName) {
        filterProperties.setFileExtensions(Arrays.asList(".java"));
        DiffEntry diffEntry = createMockDiffEntry(fileName);
        return !scanFileFilter.shouldInclude(diffEntry);
    }

    private boolean shouldExcludeByCustomPattern(String fileName) {
        DiffEntry diffEntry = createMockDiffEntry(fileName);
        return !scanFileFilter.shouldInclude(diffEntry);
    }

    private boolean shouldIncludeByCombinedFilters(String fileName) {
        DiffEntry diffEntry = createMockDiffEntry(fileName);
        return scanFileFilter.shouldInclude(diffEntry);
    }

    private DiffEntry createMockDiffEntry(String filePath) {
        DiffEntry diffEntry = mock(DiffEntry.class);
        when(diffEntry.getNewPath()).thenReturn(filePath);
        when(diffEntry.getOldPath()).thenReturn(filePath);
        return diffEntry;
    }

    private DiffEntry createMockDiffEntryForDeletion(String oldPath) {
        DiffEntry diffEntry = mock(DiffEntry.class);
        when(diffEntry.getNewPath()).thenReturn("/dev/null");
        when(diffEntry.getOldPath()).thenReturn(oldPath);
        return diffEntry;
    }

    private DiffEntry createMockDiffEntryForAddition(String newPath) {
        DiffEntry diffEntry = mock(DiffEntry.class);
        when(diffEntry.getNewPath()).thenReturn(newPath);
        when(diffEntry.getOldPath()).thenReturn("/dev/null");
        return diffEntry;
    }
}
