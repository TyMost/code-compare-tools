package com.example.migration.annotation;

public class OracleChangeMarker {
    public String render(String code) {
        return "<!-- ORACLE_CHANGE_START -->\n" + code + "\n<!-- ORACLE_CHANGE_END -->";
    }
}
