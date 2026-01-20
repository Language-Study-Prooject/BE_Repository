package com.mzc.secondproject.serverless.domain.grammar.exception;

import com.mzc.secondproject.serverless.common.exception.ServerlessException;

public class GrammarException extends ServerlessException {
	
	private GrammarException(GrammarErrorCode errorCode) {
		super(errorCode);
	}
	
	private GrammarException(GrammarErrorCode errorCode, String message) {
		super(errorCode, message);
	}
	
	private GrammarException(GrammarErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
	
	// === 요청 검증 관련 팩토리 메서드 ===
	
	public static GrammarException invalidRequest(String field, String reason) {
		return (GrammarException) new GrammarException(GrammarErrorCode.INVALID_REQUEST,
				String.format("잘못된 요청입니다: %s", reason))
				.addDetail("field", field)
				.addDetail("reason", reason);
	}
	
	// === 문법 체크 관련 팩토리 메서드 ===
	
	public static GrammarException invalidSentence(String sentence) {
		return (GrammarException) new GrammarException(GrammarErrorCode.INVALID_SENTENCE,
				"유효하지 않은 문장입니다. 문장을 확인해주세요.")
				.addDetail("sentence", sentence);
	}
	
	public static GrammarException grammarCheckFailed(String reason) {
		return (GrammarException) new GrammarException(GrammarErrorCode.GRAMMAR_CHECK_FAILED,
				String.format("문법 체크에 실패했습니다: %s", reason))
				.addDetail("reason", reason);
	}
	
	// === 레벨 관련 팩토리 메서드 ===
	
	public static GrammarException invalidLevel(String level) {
		return (GrammarException) new GrammarException(GrammarErrorCode.INVALID_LEVEL,
				String.format("유효하지 않은 레벨입니다: '%s'. BEGINNER, INTERMEDIATE, ADVANCED 중 하나여야 합니다.", level))
				.addDetail("invalidValue", level)
				.addDetail("allowedValues", "BEGINNER, INTERMEDIATE, ADVANCED");
	}
	
	// === Bedrock API 관련 팩토리 메서드 ===
	
	public static GrammarException bedrockApiError(Throwable cause) {
		return (GrammarException) new GrammarException(GrammarErrorCode.BEDROCK_API_ERROR, cause)
				.addDetail("errorType", cause.getClass().getSimpleName());
	}
	
	public static GrammarException bedrockResponseParseError(String response) {
		return (GrammarException) new GrammarException(GrammarErrorCode.BEDROCK_RESPONSE_PARSE_ERROR,
				"AI 응답을 파싱하는데 실패했습니다.")
				.addDetail("rawResponse", response);
	}
	
	// === 세션 관련 팩토리 메서드 ===
	
	public static GrammarException sessionNotFound(String sessionId) {
		return (GrammarException) new GrammarException(GrammarErrorCode.SESSION_NOT_FOUND,
				String.format("세션을 찾을 수 없습니다 (sessionId: %s)", sessionId))
				.addDetail("sessionId", sessionId);
	}
	
	public static GrammarException sessionExpired(String sessionId) {
		return (GrammarException) new GrammarException(GrammarErrorCode.SESSION_EXPIRED,
				String.format("세션이 만료되었습니다 (sessionId: %s)", sessionId))
				.addDetail("sessionId", sessionId);
	}
}
