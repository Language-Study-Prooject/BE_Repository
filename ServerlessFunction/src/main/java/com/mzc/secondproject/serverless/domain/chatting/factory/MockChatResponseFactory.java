package com.mzc.secondproject.serverless.domain.chatting.factory;

import com.mzc.secondproject.serverless.domain.chatting.enums.ChatLevel;

/**
 * 테스트용 Mock AI 채팅 응답 Factory.
 * 외부 API 호출 없이 고정된 응답을 반환한다.
 */
public class MockChatResponseFactory implements ChatResponseFactory {

    private static final String MOCK_MODEL_ID = "mock-model-v1";

    @Override
    public ChatResponse create(String userMessage, ChatLevel level, String conversationHistory) {
        String response = generateMockResponse(userMessage, level);
        return ChatResponse.of(response, MOCK_MODEL_ID, 10);
    }

    private String generateMockResponse(String userMessage, ChatLevel level) {
        return switch (level) {
            case BEGINNER -> String.format(
                    "Hello! That's a great question. '%s' - Let me explain simply. " +
                    "(안녕하세요! 좋은 질문이에요. 쉽게 설명해 드릴게요.)",
                    truncate(userMessage, 50)
            );
            case INTERMEDIATE -> String.format(
                    "Good point! Regarding '%s', I think we can explore this topic further. " +
                    "What do you think about it?",
                    truncate(userMessage, 50)
            );
            case ADVANCED -> String.format(
                    "That's an insightful observation about '%s'. " +
                    "Let me offer a nuanced perspective on this matter.",
                    truncate(userMessage, 50)
            );
        };
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
