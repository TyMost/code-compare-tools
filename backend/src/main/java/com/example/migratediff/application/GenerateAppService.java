package com.example.migratediff.application;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.migration.DecisionType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 负责根据差异片段生成迁移模板，不直接操作文件系统。
 */
@Service
public class GenerateAppService {

    private static final String INSERT_TEMPLATE = join(
            "/** 迁移生成的代码片段开始 */",
            " * 请按照 project_rules.md 调整适配",
            " */",
            "${deltaO}",
            "/** 迁移生成的代码片段结束 */"
    );

    private static final String UPDATE_TEMPLATE = join(
            "/** 迁移适配开始 */",
            " * 请按照 project_rules.md 调整适配",
            " */",
            "/** 目标仓库原实现开始 */",
            "${deltaG}",
            "/** 目标仓库原实现结束 */",
            "/** 迁移适配建议开始 */",
            "${deltaO}",
            "/** 迁移适配建议结束 */",
            "/** 迁移适配段落结束 */"
    );

    private static final String DELETE_HINT_TEMPLATE = "// TODO: 请主动删除目标仓库中的对应实现";

    public DecisionType classifyBlock(DiffBlock block) {
        if (block == null) {
            return DecisionType.SKIP;
        }
        return classifyBlock(block.getContentFrom(), block.getContentTo());
    }

    public DecisionType classifyBlock(String oracleSnippet, String gaussSnippet) {
        boolean hasOracle = StringUtils.hasText(oracleSnippet);
        boolean hasGauss = StringUtils.hasText(gaussSnippet);
        if (hasOracle && !hasGauss) {
            return DecisionType.INSERT;
        }
        if (!hasOracle && hasGauss) {
            return DecisionType.DELETE;
        }
        if (hasOracle && hasGauss) {
            return safe(oracleSnippet).trim().equals(safe(gaussSnippet).trim())
                    ? DecisionType.SKIP
                    : DecisionType.UPDATE;
        }
        return DecisionType.SKIP;
    }

    public String generateTemplate(DiffBlock block, DecisionType decision) {
        if (block == null) {
            return "";
        }
        return generateTemplate(block.getContentFrom(), block.getContentTo(), decision);
    }

    public String generateTemplate(String oracleSnippet, String gaussSnippet, DecisionType decision) {
        if (decision == null) {
            return "";
        }
        switch (decision) {
            case INSERT:
                return format(INSERT_TEMPLATE, oracleSnippet, null);
            case UPDATE:
                return format(UPDATE_TEMPLATE, oracleSnippet, gaussSnippet);
            case DELETE:
                return DELETE_HINT_TEMPLATE;
            default:
                return "";
        }
    }

    private String format(String template, String deltaO, String deltaG) {
        String value = template;
        if (value.contains("${deltaO}")) {
            value = value.replace("${deltaO}", safe(deltaO));
        }
        if (value.contains("${deltaG}")) {
            value = value.replace("${deltaG}", safe(deltaG));
        }
        return value;
    }

    private String safe(String content) {
        return content == null ? "" : content;
    }

    private static String join(String... lines) {
        String lineSeparator = System.lineSeparator();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            builder.append(lines[i]);
            if (i < lines.length - 1) {
                builder.append(lineSeparator);
            }
        }
        return builder.toString();
    }
}

