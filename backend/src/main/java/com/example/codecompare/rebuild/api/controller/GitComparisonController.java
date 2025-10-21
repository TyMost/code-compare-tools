package com.example.codecompare.rebuild.api.controller;

import com.example.codecompare.rebuild.api.dto.GitComparisonResponseView;
import com.example.codecompare.rebuild.api.response.ApiResponse;
import com.example.codecompare.rebuild.api.response.ApiResponseFactory;
import com.example.codecompare.rebuild.scanning.IncrementalDiffFacade;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint that exposes dual project Git comparisons.
 */
@RestController
@RequestMapping("/api/v1/migration/git-comparison")
public class GitComparisonController {

    private final IncrementalDiffFacade incrementalDiffFacade;

    public GitComparisonController(IncrementalDiffFacade incrementalDiffFacade) {
        this.incrementalDiffFacade = incrementalDiffFacade;
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
}

