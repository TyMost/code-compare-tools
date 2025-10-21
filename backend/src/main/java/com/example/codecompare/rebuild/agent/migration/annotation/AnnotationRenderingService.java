package com.example.codecompare.rebuild.agent.migration.annotation;

import com.example.codecompare.rebuild.agent.AnnotationTemplateProvider;
import com.example.codecompare.rebuild.agent.migration.LineEndingNormalizer;
import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.stats.CodeBlockDetailDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Generates annotated code snippets for migration workflows.
 */
@Component
public class AnnotationRenderingService {

    private static final String DEFAULT_ANNOTATION_START = "/** 迁移生成的代码片段开始（blockId=%s） */";
    private static final String DEFAULT_ANNOTATION_END = "/** 迁移生成的代码片段结束 */";

    private final AnnotationTemplateProvider annotationTemplateProvider;

    public AnnotationRenderingService(AnnotationTemplateProvider annotationTemplateProvider) {
        this.annotationTemplateProvider = annotationTemplateProvider;
    }

    public RenderedAnnotation render(CodeBlockDetailDTO detail,
                                     BlockDiff diff,
                                     String blockId,
                                     String overrideTemplate) {
        String normalizedSource = LineEndingNormalizer.normalize(detail.getOldCode());
        String targetBaseline = extractTargetBaseline(detail, diff);
        if (StringUtils.hasText(overrideTemplate)) {
            return renderWithCustomTemplate(blockId, normalizedSource, targetBaseline, overrideTemplate);
        }
        boolean useAdaptTemplate = hasAdaptCategory(detail.getCategoryKeys());
        AnnotationTemplateProvider.AnnotationRenderResult result =
                annotationTemplateProvider.render(blockId, normalizedSource, targetBaseline, useAdaptTemplate);
        return new RenderedAnnotation(result.getContent(), result.getTemplate(), result.getTemplateKey());
    }

    public String normalizeLineEndings(String text) {
        return LineEndingNormalizer.normalize(text);
    }

    private RenderedAnnotation renderWithCustomTemplate(String blockId,
                                                        String normalizedSource,
                                                        String normalizedOriginal,
                                                        String template) {
        String rawTemplate = template;
        String processedTemplate = template == null ? "" : template.trim();
        String lineSeparator = System.lineSeparator();
        String safeBlockId = blockId == null ? "" : blockId;
        if (StringUtils.hasText(processedTemplate)) {
            String processed = processedTemplate;
            if (processed.contains("${blockId}")) {
                processed = processed.replace("${blockId}", safeBlockId);
            }
            boolean replaced = false;
            if (processed.contains("${code}")) {
                processed = processed.replace("${code}", normalizedSource);
                replaced = true;
            }
            if (processed.contains("${original}")) {
                processed = processed.replace("${original}", normalizedOriginal);
                replaced = true;
            }
            if (!replaced && processed.contains("%s")) {
                processed = String.format(processed, normalizedSource);
                replaced = true;
            }
            if (replaced) {
                return new RenderedAnnotation(processed, rawTemplate, "custom");
            }
            String startLine = processed;
            if (startLine.contains("%s")) {
                startLine = startLine.replace("%s", safeBlockId);
            }
            if (startLine.contains("${blockId}")) {
                startLine = startLine.replace("${blockId}", safeBlockId);
            }
            if (startLine.endsWith(lineSeparator)) {
                startLine = startLine.substring(0, startLine.length() - lineSeparator.length());
            }
            String header = StringUtils.hasText(startLine)
                    ? startLine
                    : String.format(DEFAULT_ANNOTATION_START, safeBlockId);
            StringBuilder builder = new StringBuilder();
            builder.append(header).append(lineSeparator);
            builder.append(normalizedSource);
            if (!normalizedSource.endsWith(lineSeparator)) {
                builder.append(lineSeparator);
            }
            builder.append(DEFAULT_ANNOTATION_END);
            return new RenderedAnnotation(builder.toString(), rawTemplate, "custom");
        }
        AnnotationTemplateProvider.AnnotationRenderResult result =
                annotationTemplateProvider.render(blockId, normalizedSource, normalizedOriginal, false);
        return new RenderedAnnotation(result.getContent(), result.getTemplate(), result.getTemplateKey());
    }

    private String extractTargetBaseline(CodeBlockDetailDTO detail, BlockDiff diff) {
        if (diff != null && StringUtils.hasText(diff.getTargetContent())) {
            return LineEndingNormalizer.normalize(diff.getTargetContent());
        }
        if (StringUtils.hasText(detail.getNewCode())) {
            return LineEndingNormalizer.normalize(detail.getNewCode());
        }
        return "";
    }

    private boolean hasAdaptCategory(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return false;
        }
        for (String category : categories) {
            if (category != null && "migrate_adapt".equalsIgnoreCase(category.trim())) {
                return true;
            }
        }
        return false;
    }
}
