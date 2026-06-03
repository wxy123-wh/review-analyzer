package com.wh.reputation.analysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

final class DataFileLocator {
    private DataFileLocator() {}

    static Path resolveRequired(String filename) {
        List<Path> candidates = List.of(
                Paths.get("data").resolve(filename),
                Paths.get("..").resolve("data").resolve(filename)
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .map(Path::toAbsolutePath)
                .orElseThrow(() -> new IllegalStateException("未找到数据文件：" + filename + "，已尝试：" + candidates));
    }
}
