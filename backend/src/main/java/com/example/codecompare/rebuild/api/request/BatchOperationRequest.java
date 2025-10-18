package com.example.codecompare.rebuild.api.request;

import java.util.Collections;
import java.util.List;

/**
 * 批量操作请求，承载代码块 ID 与可选的注解模板。
 */
public class BatchOperationRequest {

    private List<String> blockIds = Collections.emptyList();
    private String annotationTemplate;

    public List<String> getBlockIds() {
        return blockIds;
    }

    public void setBlockIds(List<String> blockIds) {
        this.blockIds = blockIds;
    }

    public String getAnnotationTemplate() {
        return annotationTemplate;
    }

    public void setAnnotationTemplate(String annotationTemplate) {
        this.annotationTemplate = annotationTemplate;
    }

    public int size() {
        return blockIds == null ? 0 : blockIds.size();
    }
}
