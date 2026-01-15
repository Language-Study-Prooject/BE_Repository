package com.mzc.secondproject.serverless.domain.grammar.streaming;

/**
 * Grammar 스트리밍 요청 DTO
 * Java 16+ Record 활용
 */
public record StreamingRequest(
		String sessionId,
		String message,
		String userId,
		String level
) {
	public boolean isValid() {
		return message != null && !message.trim().isEmpty()
				&& userId != null && !userId.trim().isEmpty();
	}
}
