package com.wh.reputation.ai;

import java.util.List;

public record OpenAiChatRequest(
        String model,
        List<Message> messages,
        Double temperature,
        Integer maxTokens) {
    public record Message(String role, String content) {
    }

    public static OpenAiChatRequest create(String model, String systemPrompt, String userPrompt) {
        return new OpenAiChatRequest(
                model,
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userPrompt)),
                0.7,
                2000);
    }
}
