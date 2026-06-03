package com.wh.reputation.review;

final class TextCleaner {
    private TextCleaner() {}

    static String clean(String input) {
        if (input == null) {
            return "";
        }

        String noHtml = input.replaceAll("<[^>]*>", " ");
        StringBuilder sb = new StringBuilder(noHtml.length());
        noHtml.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            if (type == Character.FORMAT) {
                return;
            }
            if (type == Character.CONTROL) {
                if (Character.isWhitespace(cp)) {
                    sb.append(' ');
                }
                return;
            }
            if (type == Character.PRIVATE_USE || type == Character.SURROGATE || type == Character.UNASSIGNED) {
                return;
            }
            sb.appendCodePoint(cp);
        });

        return sb.toString().replaceAll("\\s+", " ").trim();
    }
}
