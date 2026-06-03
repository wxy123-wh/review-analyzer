package com.wh.reputation.review;

import java.util.ArrayList;
import java.util.List;

final class CsvUtils {
    private CsvUtils() {}

    static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                        continue;
                    }
                    inQuotes = false;
                    continue;
                }
                current.append(c);
                continue;
            }

            if (c == '"') {
                inQuotes = true;
                continue;
            }
            if (c == ',') {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }

        if (inQuotes) {
            throw new IllegalArgumentException("表格行格式错误：引号未闭合");
        }
        values.add(current.toString());
        return values;
    }
}
