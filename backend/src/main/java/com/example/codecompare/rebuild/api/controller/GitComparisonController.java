package com.example.codecompare.rebuild.api.controller;

import com.example.codecompare.rebuild.api.dto.GitComparisonResponseView;
import com.example.codecompare.rebuild.api.response.ApiResponse;
import com.example.codecompare.rebuild.api.response.ApiResponseFactory;
import com.example.codecompare.rebuild.scanning.IncrementalDiffFacade;
import com.example.codecompare.rebuild.scanning.batch.GitComparisonBatchExportResult;
import com.example.codecompare.rebuild.scanning.batch.GitComparisonBatchService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST endpoint that exposes dual project Git comparisons.
 */
@RestController
@RequestMapping("/api/v1/migration/git-comparison")
public class GitComparisonController {

    private final IncrementalDiffFacade incrementalDiffFacade;
    private final GitComparisonBatchService batchService;

    public GitComparisonController(IncrementalDiffFacade incrementalDiffFacade,
                                   GitComparisonBatchService batchService) {
        this.incrementalDiffFacade = incrementalDiffFacade;
        this.batchService = batchService;
    }

    @GetMapping
    public ApiResponse<GitComparisonResponseView> compare(
            @RequestParam("sourceProjectKey") String sourceProjectKey,
            @RequestParam("targetProjectKey") String targetProjectKey,
            @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh) {
        if (!StringUtils.hasText(sourceProjectKey) || !StringUtils.hasText(targetProjectKey)) {
            return ApiResponseFactory.error("sourceProjectKey 和 targetProjectKey 均不能为空");
        }
        GitComparisonResponseView response = incrementalDiffFacade.compareProjects(sourceProjectKey, targetProjectKey, refresh);
        return ApiResponseFactory.ok(response);
    }

    @GetMapping(value = "/batch-export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<Resource> exportBatch(
            @RequestParam(value = "configPath", required = false) String configPath,
            @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh) {
        try {
            GitComparisonBatchExportResult exportResult = batchService.export(configPath, refresh);
            ByteArrayResource resource = new ByteArrayResource(exportResult.getContent());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(exportResult.getFilename(), StandardCharsets.UTF_8)
                    .build());
            headers.setContentLength(exportResult.getContent().length);
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            return new ResponseEntity<>(resource, headers, HttpStatus.OK);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "批量导出失败", ex);
        }
    }
}
