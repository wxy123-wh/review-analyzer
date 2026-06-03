package com.wh.reputation.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class DateRangeParser {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DateRangeParser() {}

    public static LocalDate parseDateOrNull(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(input.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("日期格式不正确：" + input);
        }
    }
}
