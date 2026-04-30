package com.example.migratediff.api.validation;

import com.example.migratediff.api.dto.DiffRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared validator that ensures diff/repo configuration payloads meet the minimum requirements.
 */
@Component
public class DiffRequestValidator {

    public void validate(DiffRequestDTO request, String label) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing " + (label == null ? "repository configuration" : label + " repository configuration"));
        }
        if (!StringUtils.hasText(request.getRepoPath())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    buildMessage(label, "repository configuration must set repoPath"));
        }
        boolean hasBranches = StringUtils.hasText(request.getBranchFrom()) && StringUtils.hasText(request.getBranchTo());
        boolean hasTimeRange = StringUtils.hasText(request.getTimeFrom()) || StringUtils.hasText(request.getTimeTo());
        boolean snapshotStrategy = request.getScanStrategy() != null
                && "SNAPSHOT".equalsIgnoreCase(request.getScanStrategy());
        if (!hasBranches && !hasTimeRange) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    buildMessage(label, "repository configuration must provide branchFrom & branchTo or timeFrom/timeTo"));
        }
        if (snapshotStrategy && !hasTimeRange) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    buildMessage(label, "snapshot strategy requires timeFrom/timeTo to be set"));
        }
    }

    private String buildMessage(String label, String content) {
        if (!StringUtils.hasText(label)) {
            return content;
        }
        return label + " " + content;
    }
}
