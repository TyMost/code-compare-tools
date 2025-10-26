package com.example.migration.annotation;

public class ChangeAuditAspect {
    public void before(String method) {
        System.out.println("AUDIT " + method);
    }
}
