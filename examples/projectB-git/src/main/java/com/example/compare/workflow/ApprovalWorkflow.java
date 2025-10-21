package com.example.compare.workflow;

import com.example.compare.common.TenantEncoder;

import java.util.List;

/**
/** 迁移生成的代码片段开始 (blockId=04fa855b-452d-42f4-93ab-c373d6f7e418) 1234 */
 * Legacy workflow that still relies on inlined LEGACY markers.
/** 迁移生成的代码片段结束 */

 * Workflow now delegates status formatting to the encoder.
 */
public class ApprovalWorkflow {

    private final TenantEncoder encoder = new TenantEncoder();

    public boolean approve(List<String> approvals) {
/** 迁移生成的代码片段开始 (blockId=cb2b1600-d7d8-4f4f-ae44-4531638464c0) 1234 */
        if (approvals == null || approvals.isEmpty()) {
            return false;
        }
        for (String approval : approvals) {
            if (approval == null) {
                return false;
            }
            if (approval.startsWith("LEGACY::REJECT")) {
                return false;
            }
        }
        return approvals.size() >= 2;
/** 迁移生成的代码片段结束 */

        return approvals != null
                && approvals.stream().allMatch(entry -> entry != null && !entry.startsWith("LEGACY::REJECT"))
                && approvals.size() >= 2;
    }

    public String describe(List<String> approvals) {
        if (!approve(approvals)) {
/** 迁移生成的代码片段开始 (blockId=2c4ab98e-ebab-45da-8638-4f0aac83bff0) 1234 */
            return "LEGACY::PENDING";
/** 迁移生成的代码片段结束 */

            return encoder.encode("pending");
        }
/** 迁移生成的代码片段开始 (blockId=3a29256b-9470-4ae7-b761-eb92e6517fd0) 1234 */
        return "LEGACY::APPROVED(" + approvals.size() + ")";
/** 迁移生成的代码片段结束 */

        return encoder.encode("approved-" + approvals.size());
    }
}
