package com.mzc.secondproject.serverless.domain.speaking.dto.request;

/**
 * Speaking API 요청 DTO
 */
public record SpeakingRequest(
		String sessionId,   // 세션 ID (첫 요청 시 null)
		String audio,       // 음성 데이터 (base64)
		String text,        // 텍스트 입력
		String level        // 레벨 (BEGINNER, INTERMEDIATE, ADVANCED)
) {
	/**
	 * 기본값 적용된 레벨 반환
	 */
	public String getLevelOrDefault() {
		return level != null && !level.isEmpty() ? level : "INTERMEDIATE";
	}
	
	/**
	 * 음성 입력인지 확인
	 */
	public boolean hasAudio() {
		return audio != null && !audio.isEmpty();
	}
	
	/**
	 * 텍스트 입력인지 확인
	 */
	public boolean hasText() {
		return text != null && !text.trim().isEmpty();
	}
}
