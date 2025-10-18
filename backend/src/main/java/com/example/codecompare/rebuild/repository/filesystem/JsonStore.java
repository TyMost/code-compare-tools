package com.example.codecompare.rebuild.repository.filesystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper class that reads and writes JSON files while wrapping IOExceptions.
 */
final class JsonStore {

    private JsonStore() {
    }

    static <T> T read(Path file, ObjectMapper mapper, TypeReference<T> type) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            return mapper.readValue(bytes, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JSON file: " + file, e);
        }
    }

    static void write(Path file, ObjectMapper mapper, Object value, boolean prettyPrint) {
        try {
            Files.createDirectories(file.getParent());
            byte[] bytes = serialize(mapper, value, prettyPrint);
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write JSON file: " + file, e);
        }
    }

    private static byte[] serialize(ObjectMapper mapper, Object value, boolean prettyPrint) throws JsonProcessingException {
        if (prettyPrint) {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
                    .getBytes(StandardCharsets.UTF_8);
        }
        return mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
    }
}
