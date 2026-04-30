package com.example.migratediff.api.mapper;

import com.example.migratediff.api.dto.CoverageRequestDTO;
import com.example.migratediff.api.dto.CoverageResponseDTO;
import com.example.migratediff.domain.coverage.CoverageSummary;
import com.example.migratediff.domain.diff.DeltaGroup;
import org.springframework.stereotype.Component;

@Component
public class CoverageMapper {

    public DeltaGroup toDomain(CoverageRequestDTO requestDTO) {
        if (requestDTO == null) {
            return null;
        }
        return requestDTO.getDeltaGroup();
    }

    public CoverageResponseDTO toResponse(CoverageSummary summary) {
        CoverageResponseDTO responseDTO = new CoverageResponseDTO();
        responseDTO.setSummary(summary);
        if (summary != null) {
            responseDTO.setTaskId(summary.getTaskId());
            responseDTO.setMessage("Coverage analysis completed");
        } else {
            responseDTO.setMessage("Coverage summary not found");
        }
        return responseDTO;
    }
}
