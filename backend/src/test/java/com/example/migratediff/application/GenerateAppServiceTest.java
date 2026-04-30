package com.example.migratediff.application;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.migration.DecisionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateAppServiceTest {

    private GenerateAppService generateAppService;

    @BeforeEach
    void setUp() {
        generateAppService = new GenerateAppService();
    }

    @Test
    void classifyBlock_shouldReturnInsertWhenTargetMissing() {
        DiffBlock block = DiffBlock.builder()
                .contentFrom("System.out.println(\"O\");")
                .contentTo("")
                .build();
        DecisionType decision = generateAppService.classifyBlock(block);
        assertEquals(DecisionType.INSERT, decision);
    }

    @Test
    void classifyBlock_shouldReturnSkipWhenContentSame() {
        DiffBlock block = DiffBlock.builder()
                .contentFrom("int value = 1;")
                .contentTo("int value = 1;")
                .build();
        DecisionType decision = generateAppService.classifyBlock(block);
        assertEquals(DecisionType.SKIP, decision);
    }

    @Test
    void generateTemplate_shouldFillUpdateTemplate() {
        DiffBlock block = DiffBlock.builder()
                .contentFrom("System.out.println(\"O\");")
                .contentTo("System.out.println(\"G\");")
                .build();
        String template = generateAppService.generateTemplate(block, DecisionType.UPDATE);
        assertTrue(template.contains("迁移适配段落结束"));
        assertTrue(template.contains("System.out.println(\"O\");"));
        assertTrue(template.contains("System.out.println(\"G\");"));
    }
}
