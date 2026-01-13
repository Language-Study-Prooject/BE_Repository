package com.mzc.secondproject.serverless.domain.chatting.factory;

/**
 * AI 채팅 응답 Value Object
 */
public record ChatResponse(
		String content,
		String modelId,
		long processingTimeMs
) {
	public static ChatResponse of(String content, String modelId, long processingTimeMs) {
		return new ChatResponse(content, modelId, processingTimeMs);
	}
	
	public static ChatResponse of(String content) {
		return new ChatResponse(content, "unknown", 0);
	}
}
