package com.example.migratediff.shared.utils;

import java.nio.file.Paths;

public final class PathUtils {

    private PathUtils() {
    }

    public static String normalize(String base, String child) {
        return Paths.get(base, child).normalize().toString();
    }
}
