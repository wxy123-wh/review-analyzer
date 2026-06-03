package com.wh.reputation.ai;

import java.util.List;

public record OpenAiChatResponse(
        String id,
        String object,
        Long created,
        String model,
        List<Choice> choices,
        Usage usage) {
    public record Choice(
            Integer index,
            Message message,
            String finishReason) {
    }

    public record Message(
            String role,
            String content) {
    }

    public record Usage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens) {
    }

    public String getContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Message message = choices.get(0).message();
        return message == null ? null : message.content();
    }
}
