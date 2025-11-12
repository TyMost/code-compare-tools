package com.example.migratediff.api.mapper;

import com.example.migratediff.api.dto.DiffRequestDTO;
import com.example.migratediff.api.dto.ScanRequestDTO;
import com.example.migratediff.application.scan.ScanInput;
import com.example.migratediff.application.scan.ScanMode;
import com.example.migratediff.application.scan.ScanPresetProperties;
import com.example.migratediff.domain.repo.RepoConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanMapperTest {

    private ScanMapper mapper;
    private ScanPresetProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ScanPresetProperties();
        ScanPresetProperties.ScanPreset preset = new ScanPresetProperties.ScanPreset();
        preset.setName("default-og");

        ScanPresetProperties.RepoPreset source = new ScanPresetProperties.RepoPreset();
        source.setCode("o-g");
        source.setPath("D:\\\\Coding\\\\code-compare-tools\\\\examples\\\\o");
        source.setBranchFrom("refs/heads/o1");
        source.setBranchTo("refs/heads/o2");
        source.setDeltaType("DELTA_O");
        source.setFetchIfMissing(true);
        source.setRemoteName("origin");
        preset.setSource(source);

        ScanPresetProperties.RepoPreset target = new ScanPresetProperties.RepoPreset();
        target.setCode("g");
        target.setPath("D:\\\\Coding\\\\code-compare-tools\\\\examples\\\\g");
        target.setBranchFrom("refs/heads/g1");
        target.setBranchTo("refs/heads/g2");
        target.setDeltaType("DELTA_G");
        target.setFetchIfMissing(true);
        target.setRemoteName("origin");
        preset.setTarget(target);

        properties.setPresets(Collections.singletonList(preset));
        mapper = new ScanMapper(new DiffMapper(), properties);
    }

    @Test
    void toInput_shouldUsePresetWhenRequestsEmpty() {
        ScanRequestDTO request = new ScanRequestDTO();
        request.setPresetName("default-og");

        ScanInput input = mapper.toInput(request, ScanMode.FULL);

        RepoConfig oracleConfig = input.getOracleSummary().getRepoConfig();
        assertEquals("D:\\\\Coding\\\\code-compare-tools\\\\examples\\\\o", oracleConfig.getRepoPath().getAbsolutePath());
        assertEquals("refs/heads/o1", oracleConfig.getBranchFrom().getName());
        assertEquals("refs/heads/o2", oracleConfig.getBranchTo().getName());
        assertEquals("origin", oracleConfig.getRemoteName());
        assertTrue(oracleConfig.isFetchIfMissing());
    }

    @Test
    void toInput_shouldOverridePresetValues() {
        ScanRequestDTO request = new ScanRequestDTO();
        request.setPresetName("default-og");
        DiffRequestDTO oracleOverride = new DiffRequestDTO();
        oracleOverride.setBranchTo("refs/heads/o3");
        oracleOverride.setIncludeWorkingTree(Boolean.TRUE);
        request.setOracle(oracleOverride);

        ScanInput input = mapper.toInput(request, ScanMode.FULL);

        RepoConfig oracleConfig = input.getOracleSummary().getRepoConfig();
        assertEquals("refs/heads/o3", oracleConfig.getBranchTo().getName());
        assertTrue(oracleConfig.isIncludeWorkingTree());
    }

    @Test
    void toInput_shouldEnableWorkingTreeForIncremental() {
        ScanRequestDTO request = new ScanRequestDTO();
        request.setPresetName("default-og");

        ScanInput input = mapper.toInput(request, ScanMode.INCREMENTAL);

        assertTrue(input.getOracleSummary().getRepoConfig().isIncludeWorkingTree());
        assertTrue(input.getGaussSummary().getRepoConfig().isIncludeWorkingTree());
    }

    @Test
    void toInput_shouldFailWhenPresetMissing() {
        ScanRequestDTO request = new ScanRequestDTO();
        request.setPresetName("unknown");

        assertThrows(ResponseStatusException.class, () -> mapper.toInput(request, ScanMode.FULL));
    }

    @Test
    void toInput_shouldFailWhenConfigIncomplete() {
        ScanRequestDTO request = new ScanRequestDTO();
        request.setOracle(new DiffRequestDTO());
        request.setGauss(new DiffRequestDTO());

        assertThrows(ResponseStatusException.class, () -> mapper.toInput(request, ScanMode.FULL));
    }
}
