package com.example.migratediff.infrastructure.persistence.filesystem;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 将业务 ID 编码为文件系统安全的文件名。
 */
public final class SafeFileNameEncoder {

    private SafeFileNameEncoder() {
    }

    public static String encode(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
