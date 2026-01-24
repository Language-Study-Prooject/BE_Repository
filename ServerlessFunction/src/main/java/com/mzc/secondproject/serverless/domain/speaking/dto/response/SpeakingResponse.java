package com.mzc.secondproject.serverless.domain.speaking.dto.response;

/**
 * Speaking API 응답 DTO
 */
public record SpeakingResponse(
		String sessionId,         // 세션 ID (다음 요청에 사용)
		String userTranscript,    // 사용자가 말한 내용 (STT 결과)
		String aiText,            // AI 응답 텍스트
		String aiAudioUrl,        // AI 응답 음성 URL (Polly)
		double confidence         // STT 신뢰도
) {
}
