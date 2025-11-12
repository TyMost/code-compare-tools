package com.example.migration.common;

import java.time.Instant;
import java.util.Objects;

public class RequestContext {
    private final String requestId;
    private final String operator;
    private final Instant startTime;

    public RequestContext(String requestId, String operator) {
        this.requestId = requestId;
        this.operator = operator;
        this.startTime = Instant.now();
    }

    public String getRequestId() {
        return requestId;
    }

    public String getOperator() {
        return operator;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public RequestContext withOperator(String newOperator) {
        return new RequestContext(requestId, newOperator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RequestContext)) {
            return false;
        }
        RequestContext that = (RequestContext) o;
        return Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }
}
