package com.example.compare.workflow;

import java.util.List;

/**
 * Legacy workflow that still relies on inlined LEGACY markers.
 */
public class ApprovalWorkflow {

    public boolean approve(List<String> approvals) {
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
    }

    public String describe(List<String> approvals) {
        if (!approve(approvals)) {
            return "LEGACY::PENDING";
        }
        return "LEGACY::APPROVED(" + approvals.size() + ")";
    }
}
