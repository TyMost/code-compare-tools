package com.example.codecompare.rebuild.diff;

import com.example.codecompare.rebuild.block.model.CodeSnapshot;
import org.springframework.util.Assert;

/**
 * 行级 diff 请求模型，封装源/目标代码快照。
 */
public final class DiffRequest {

    private final CodeSnapshot source;
    private final CodeSnapshot target;

    private DiffRequest(Builder builder) {
        Assert.notNull(builder.source, "source snapshot must not be null");
        Assert.notNull(builder.target, "target snapshot must not be null");
        this.source = builder.source;
        this.target = builder.target;
    }

    public static Builder builder() {
        return new Builder();
    }

    public CodeSnapshot getSource() {
        return source;
    }

    public CodeSnapshot getTarget() {
        return target;
    }

    public static final class Builder {
        private CodeSnapshot source = CodeSnapshot.of(null, null, "");
        private CodeSnapshot target = CodeSnapshot.of(null, null, "");

        public Builder source(CodeSnapshot source) {
            if (source != null) {
                this.source = source;
            }
            return this;
        }

        public Builder target(CodeSnapshot target) {
            if (target != null) {
                this.target = target;
            }
            return this;
        }

        public DiffRequest build() {
            return new DiffRequest(this);
        }
    }
}
