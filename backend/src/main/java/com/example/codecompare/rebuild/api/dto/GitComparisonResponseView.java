package com.example.codecompare.rebuild.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregated response for Git comparison across two projects.
 */
@JsonDeserialize(builder = GitComparisonResponseView.Builder.class)
public final class GitComparisonResponseView {

    private final GitComparisonProjectView sourceProject;
    private final GitComparisonProjectView targetProject;
    private final List<GitComparisonFileView> files;

    private GitComparisonResponseView(Builder builder) {
        this.sourceProject = builder.sourceProject;
        this.targetProject = builder.targetProject;
        this.files = builder.files == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(builder.files));
    }

    public static Builder builder() {
        return new Builder();
    }

    public GitComparisonProjectView getSourceProject() {
        return sourceProject;
    }

    public GitComparisonProjectView getTargetProject() {
        return targetProject;
    }

    public List<GitComparisonFileView> getFiles() {
        return files;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private GitComparisonProjectView sourceProject;
        private GitComparisonProjectView targetProject;
        private List<GitComparisonFileView> files;

        public Builder sourceProject(@JsonProperty("sourceProject") GitComparisonProjectView sourceProject) {
            this.sourceProject = sourceProject;
            return this;
        }

        public Builder targetProject(@JsonProperty("targetProject") GitComparisonProjectView targetProject) {
            this.targetProject = targetProject;
            return this;
        }

        public Builder files(@JsonProperty("files") List<GitComparisonFileView> files) {
            this.files = files;
            return this;
        }

        public GitComparisonResponseView build() {
            return new GitComparisonResponseView(this);
        }
    }
}

