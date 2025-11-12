package com.example.migratediff.api.mapper;

import com.example.migratediff.api.dto.DiffRequestDTO;
import com.example.migratediff.api.dto.DiffResponseDTO;
import com.example.migratediff.domain.diff.DeltaType;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.domain.repo.CommitLocatorMode;
import com.example.migratediff.domain.repo.RepoBranch;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoPath;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Component
public class DiffMapper {

    public DiffSummary toDomain(DiffRequestDTO requestDTO) {
        return toDomain(requestDTO, null);
    }

    public DiffSummary toDomain(DiffRequestDTO requestDTO, DeltaType defaultDeltaType) {
        if (requestDTO == null) {
            return null;
        }
        Instant timeFrom = parseInstant(requestDTO.getTimeFrom());
        Instant timeTo = parseInstant(requestDTO.getTimeTo());
        RepoConfig.RepoConfigBuilder builder = RepoConfig.builder()
                .repoPath(RepoPath.builder()
                        .absolutePath(requestDTO.getRepoPath())
                        .build())
                .branchFrom(RepoBranch.builder()
                        .name(requestDTO.getBranchFrom())
                        .timeFrom(timeFrom)
                        .timeTo(timeTo)
                        .refHint(resolveRefHint(requestDTO, false))
                        .build())
                .branchTo(RepoBranch.builder()
                        .name(requestDTO.getBranchTo())
                        .timeFrom(timeFrom)
                        .timeTo(timeTo)
                        .refHint(resolveRefHint(requestDTO, true))
                        .build())
                .deltaType(resolveDeltaType(requestDTO.getDeltaType(), defaultDeltaType))
                .locatorMode(resolveLocatorMode(requestDTO));
        if (requestDTO.getIncludeWorkingTree() != null) {
            builder.includeWorkingTree(requestDTO.getIncludeWorkingTree());
        }
        if (requestDTO.getFetchIfMissing() != null) {
            builder.fetchIfMissing(requestDTO.getFetchIfMissing());
        }
        if (StringUtils.hasText(requestDTO.getRemoteName())) {
            builder.remoteName(requestDTO.getRemoteName());
        }
        RepoConfig config = builder.build();
        return DiffSummary.builder()
                .repoConfig(config)
                .build();
    }

    public DiffResponseDTO toResponse(DiffSummary summary) {
        DiffResponseDTO responseDTO = new DiffResponseDTO();
        responseDTO.setSummary(summary);
        responseDTO.setMessage("Diff generation pending implementation");
        return responseDTO;
    }

    private DeltaType resolveDeltaType(String deltaType, DeltaType defaultDeltaType) {
        if (deltaType == null) {
            return defaultDeltaType != null ? defaultDeltaType : DeltaType.DELTA_O;
        }
        try {
            return DeltaType.valueOf(deltaType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return defaultDeltaType != null ? defaultDeltaType : DeltaType.DELTA_O;
        }
    }

    private CommitLocatorMode resolveLocatorMode(DiffRequestDTO requestDTO) {
        boolean hasBranch = StringUtils.hasText(requestDTO.getBranchFrom()) && StringUtils.hasText(requestDTO.getBranchTo());
        boolean hasTimeRange = StringUtils.hasText(requestDTO.getTimeFrom()) || StringUtils.hasText(requestDTO.getTimeTo());
        if (hasBranch && hasTimeRange) {
            return CommitLocatorMode.HYBRID;
        }
        if (hasTimeRange) {
            return CommitLocatorMode.TIME_RANGE;
        }
        return CommitLocatorMode.BRANCH;
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time value: " + value, ex);
        }
    }

    private String resolveRefHint(DiffRequestDTO requestDTO, boolean preferTargetBranch) {
        if (requestDTO == null) {
            return "HEAD";
        }
        if (StringUtils.hasText(requestDTO.getRefHint())) {
            return requestDTO.getRefHint();
        }
        if (preferTargetBranch && StringUtils.hasText(requestDTO.getBranchTo())) {
            return requestDTO.getBranchTo();
        }
        if (!preferTargetBranch && StringUtils.hasText(requestDTO.getBranchFrom())) {
            return requestDTO.getBranchFrom();
        }
        if (StringUtils.hasText(requestDTO.getBranchTo())) {
            return requestDTO.getBranchTo();
        }
        if (StringUtils.hasText(requestDTO.getBranchFrom())) {
            return requestDTO.getBranchFrom();
        }
        return "HEAD";
    }
}
