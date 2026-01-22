package com.mzc.secondproject.serverless.common.exception;

/**
 * 에러 코드 표준 인터페이스 (Sealed Interface)
 *
 * 모든 에러 코드 enum이 구현해야 하는 표준 계약을 정의합니다.
 * Sealed interface를 사용하여 허용된 구현체만 존재하도록 제한합니다.
 *
 * 계층 구조:
 * ErrorCode (sealed)
 * ├── CommonErrorCode (시스템/공통 에러)
 * └── DomainErrorCode (non-sealed) - 도메인별 에러
 *     ├── VocabularyErrorCode
 *     └── ChattingErrorCode
 */
public sealed interface ErrorCode permits CommonErrorCode, DomainErrorCode {
	
	/**
	 * 에러 코드 반환 (예: AUTH_001, VOCAB_001, CHAT_001)
	 */
	String getCode();
	
	/**
	 * 에러 메시지 반환
	 */
	String getMessage();
	
	/**
	 * HTTP 상태 코드 반환 (예: 400, 404, 500)
	 */
	int getStatusCode();
	
	/**
	 * 클라이언트 에러 여부 (4xx)
	 */
	default boolean isClientError() {
		int status = getStatusCode();
		return status >= 400 && status < 500;
	}
	
	/**
	 * 서버 에러 여부 (5xx)
	 */
	default boolean isServerError() {
		int status = getStatusCode();
		return status >= 500 && status < 600;
	}
}
