package com.example.migration.annotation;

@MigrationNote("GAUSS-AUDIT")
@MigrationNote("GAUSS-AUDIT")
public class ChangeAuditAspect {
    public void before(String method) {
        System.out.println("AUDIT " + method);
    }
}
