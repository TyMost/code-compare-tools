package com.example.migratediff.api.controller;

import com.example.migratediff.api.dto.ApiResponse;
import com.example.migratediff.api.dto.DiffDetailRequestDTO;
import com.example.migratediff.api.dto.ScanRequestDTO;
import com.example.migratediff.api.mapper.ScanMapper;
import com.example.migratediff.application.scan.DiffDetail;
import com.example.migratediff.application.scan.ScanAppService;
import com.example.migratediff.application.scan.ScanInput;
import com.example.migratediff.application.scan.ScanMode;
import com.example.migratediff.application.scan.ScanPresetProperties;
import com.example.migratediff.application.scan.ScanReport;
import com.example.migratediff.shared.exception.NotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/scan")
public class ScanController {

    private final ScanAppService scanAppService;
    private final ScanMapper scanMapper;
    private final ScanPresetProperties presetProperties;

    public ScanController(ScanAppService scanAppService, ScanMapper scanMapper, ScanPresetProperties presetProperties) {
        this.scanAppService = scanAppService;
        this.scanMapper = scanMapper;
        this.presetProperties = presetProperties;
    }

    @PostMapping("/full")
    public ApiResponse<?> scanFull(@Valid @RequestBody ScanRequestDTO requestDTO) {
        ScanInput input = scanMapper.toInput(requestDTO, ScanMode.FULL);
        ScanReport report = scanAppService.scan(input);
        return ApiResponse.success(scanMapper.toResponse(report));
    }

    @PostMapping
    public ApiResponse<?> scanIncremental(@Valid @RequestBody ScanRequestDTO requestDTO) {
        ScanInput input = scanMapper.toInput(requestDTO, ScanMode.INCREMENTAL);
        ScanReport report = scanAppService.scan(input);
        return ApiResponse.success(scanMapper.toResponse(report));
    }

    @PostMapping("/detail")
    public ApiResponse<?> detail(@Valid @RequestBody DiffDetailRequestDTO requestDTO) {
        DiffDetail detail = scanAppService.fetchDetail(requestDTO.getTaskId(), requestDTO.getFilePath())
                .orElseThrow(() -> new NotFoundException("Failed to locate scan record for file: " + requestDTO.getFilePath()));
        String migrationDiff = scanAppService.generateMigrationTemplate(detail);
        return ApiResponse.success(scanMapper.toDetailResponse(detail, migrationDiff));
    }

    @GetMapping("/presets")
    public ApiResponse<?> listPresets() {
        return ApiResponse.success(scanMapper.toPresetDTOs(presetProperties.getPresets()));
    }
}
