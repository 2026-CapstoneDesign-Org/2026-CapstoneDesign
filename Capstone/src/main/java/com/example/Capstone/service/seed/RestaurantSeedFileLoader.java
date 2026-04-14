package com.example.Capstone.service.seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RestaurantSeedFileLoader {

    private final ObjectMapper objectMapper;

    public Path resolvePath(String filePath, Path defaultPath) {
        if (filePath == null || filePath.isBlank()) {
            return defaultPath;
        }

        return Path.of(filePath);
    }

    public <T> List<T> readRows(Path path, TypeReference<List<T>> typeReference) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("seed 파일을 찾을 수 없습니다: " + path.toAbsolutePath());
        }

        try {
            return objectMapper.readValue(path.toFile(), typeReference);
        } catch (IOException exception) {
            throw new IllegalStateException("seed 파일을 읽을 수 없습니다: " + path.toAbsolutePath(), exception);
        }
    }
}
