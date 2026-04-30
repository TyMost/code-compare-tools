package com.example.migratediff.api.controller;

import com.example.migratediff.api.dto.ApiResponse;
import com.example.migratediff.api.dto.MigrationApplyRequestDTO;
import com.example.migratediff.api.dto.MigrationGenerateRequestDTO;
import com.example.migratediff.api.dto.MigrationRevertRequestDTO;
import com.example.migratediff.api.mapper.MigrationMapper;
import com.example.migratediff.application.scan.MigrationOperationResult;
import com.example.migratediff.application.scan.ScanAppService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/migrate")
public class MigrationController {

    private final ScanAppService scanAppService;
    private final MigrationMapper migrationMapper;

    public MigrationController(ScanAppService scanAppService, MigrationMapper migrationMapper) {
        this.scanAppService = scanAppService;
        this.migrationMapper = migrationMapper;
    }

    @PostMapping("/generate")
    public ApiResponse<?> generate(@Valid @RequestBody MigrationGenerateRequestDTO requestDTO) {
        MigrationOperationResult result = scanAppService.generateMigration(requestDTO.getTaskId(), requestDTO.getFilePath());
        return ApiResponse.success(
                "Migration diff generated",
                migrationMapper.toGenerateResponse(result.getResult(), result.getTaskId(), requestDTO.getFilePath())
        );
    }

    @PostMapping("/apply")
    public ApiResponse<?> apply(@Valid @RequestBody MigrationApplyRequestDTO requestDTO) {
        MigrationOperationResult result = scanAppService.applyMigration(requestDTO.getTaskId(), requestDTO.getFilePath());
        return ApiResponse.success(
                "Migration applied",
                migrationMapper.toApplyResponse(result.getResult(), result.getTaskId(), requestDTO.getFilePath())
        );
    }

    @PostMapping("/revert")
    public ApiResponse<?> revert(@Valid @RequestBody MigrationRevertRequestDTO requestDTO) {
        scanAppService.revertMigration(requestDTO.getTaskId(), requestDTO.getFilePath());
        return ApiResponse.success("Migration reverted", null);
    }
}
