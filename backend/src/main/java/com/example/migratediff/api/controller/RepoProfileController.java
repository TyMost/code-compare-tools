package com.example.migratediff.api.controller;

import com.example.migratediff.api.dto.ApiResponse;
import com.example.migratediff.api.dto.RepoProfileBundleDTO;
import com.example.migratediff.api.dto.RepoProfileDTO;
import com.example.migratediff.api.dto.DiffRequestDTO;
import com.example.migratediff.api.validation.DiffRequestValidator;
import com.example.migratediff.application.scan.ScanPresetProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import javax.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/repo-profiles")
public class RepoProfileController {

    private final ScanPresetProperties presetProperties;
    private final DiffRequestValidator diffRequestValidator;
    private final String configuredVersion;

    public RepoProfileController(ScanPresetProperties presetProperties,
                                 DiffRequestValidator diffRequestValidator,
                                 @Value("${migratediff.presets.version:}") String configuredVersion) {
        this.presetProperties = presetProperties;
        this.diffRequestValidator = diffRequestValidator;
        this.configuredVersion = configuredVersion;
    }

    @GetMapping("/defaults")
    public ApiResponse<RepoProfileBundleDTO> defaultProfiles() {
        List<ScanPresetProperties.ScanPreset> presets = presetProperties.getPresets();
        List<RepoProfileDTO> profiles = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(1);
        for (ScanPresetProperties.ScanPreset preset : presets) {
            profiles.add(toProfile(preset, counter.getAndIncrement()));
        }
        String version = resolveVersion(profiles);
        profiles.forEach(profile -> profile.setVersion(version));
        RepoProfileBundleDTO bundle = RepoProfileBundleDTO.builder()
                .version(version)
                .generatedAt(Instant.now())
                .profiles(profiles)
                .build();
        return ApiResponse.success(bundle);
    }

    @PostMapping("/validate")
    public ApiResponse<RepoProfileBundleDTO> validateBundle(@Valid @RequestBody RepoProfileBundleDTO bundle) {
        if (bundle == null || CollectionUtils.isEmpty(bundle.getProfiles())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "配置列表为空");
        }
        int index = 0;
        for (RepoProfileDTO profile : bundle.getProfiles()) {
            index++;
            if (profile == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "配置项 #" + index + " 为空");
            }
            validateProfile(profile, index);
        }
        return ApiResponse.success("validated", bundle);
    }

    private void validateProfile(RepoProfileDTO profile, int index) {
        DiffRequestDTO oracle = profile.getOracle();
        DiffRequestDTO gauss = profile.getGauss();
        String label = StringUtils.hasText(profile.getName()) ? profile.getName() : "配置#" + index;
        diffRequestValidator.validate(oracle, label + " oracle");
        diffRequestValidator.validate(gauss, label + " gauss");
    }

    private RepoProfileDTO toProfile(ScanPresetProperties.ScanPreset preset, int fallbackIndex) {
        if (preset == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "预设配置为空");
        }
        String presetName = StringUtils.hasText(preset.getName())
                ? preset.getName()
                : "preset-" + fallbackIndex;
        return RepoProfileDTO.builder()
                .id(presetName)
                .name(presetName)
                .presetName(presetName)
                .oracle(toDiffRequest(preset.getSource()))
                .gauss(toDiffRequest(preset.getTarget()))
                .build();
    }

    private DiffRequestDTO toDiffRequest(ScanPresetProperties.RepoPreset preset) {
        DiffRequestDTO dto = new DiffRequestDTO();
        if (preset == null) {
            return dto;
        }
        dto.setRepoPath(preset.getPath());
        dto.setBranchFrom(preset.getBranchFrom());
        dto.setBranchTo(preset.getBranchTo());
        dto.setTimeFrom(preset.getTimeFrom());
        dto.setTimeTo(preset.getTimeTo());
        dto.setRefHint(preset.getRefHint());
        dto.setDeltaType(preset.getDeltaType());
        dto.setIncludeWorkingTree(preset.isIncludeWorkingTree());
        dto.setFetchIfMissing(preset.isFetchIfMissing());
        dto.setRemoteName(preset.getRemoteName());
        dto.setScanStrategy(preset.getScanStrategy());
        dto.setSnapshotIncludeRemoteRefs(preset.isSnapshotIncludeRemoteRefs());
        dto.setSnapshotIncludeTags(preset.isSnapshotIncludeTags());
        dto.setSnapshotMaxRefs(preset.getSnapshotMaxRefs());
        return dto;
    }

    private String resolveVersion(List<RepoProfileDTO> profiles) {
        if (StringUtils.hasText(configuredVersion)) {
            return configuredVersion;
        }
        if (!CollectionUtils.isEmpty(profiles)) {
            int hash = profiles.stream()
                    .map(RepoProfileDTO::getId)
                    .collect(Collectors.toList())
                    .hashCode();
            return "preset-" + Integer.toHexString(hash);
        }
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    }
}
