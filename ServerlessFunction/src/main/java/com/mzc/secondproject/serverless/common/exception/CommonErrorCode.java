package com.mzc.secondproject.serverless.common.exception;

/**
 * 공통/시스템 에러 코드
 *
 * 도메인에 종속되지 않는 공통 에러 코드를 정의합니다.
 * - 인증/인가 에러 (AUTH_XXX)
 * - 검증 에러 (VALIDATION_XXX)
 * - 시스템 에러 (SYSTEM_XXX)
 */
public enum CommonErrorCode implements ErrorCode {
	
	// 인증/인가 관련 에러
	UNAUTHORIZED("AUTH_001", "인증이 필요합니다", 401),
	FORBIDDEN("AUTH_002", "접근 권한이 없습니다", 403),
	INVALID_TOKEN("AUTH_003", "유효하지 않은 토큰입니다", 401),
	TOKEN_EXPIRED("AUTH_004", "토큰이 만료되었습니다", 401),
	
	// 검증 관련 에러
	INVALID_INPUT("VALIDATION_001", "잘못된 입력입니다", 400),
	REQUIRED_FIELD_MISSING("VALIDATION_002", "필수 필드가 누락되었습니다", 400),
	INVALID_FORMAT("VALIDATION_003", "형식이 올바르지 않습니다", 400),
	VALUE_OUT_OF_RANGE("VALIDATION_004", "값이 허용 범위를 벗어났습니다", 400),
	
	// 리소스 관련 에러
	RESOURCE_NOT_FOUND("RESOURCE_001", "리소스를 찾을 수 없습니다", 404),
	METHOD_NOT_ALLOWED("RESOURCE_003", "허용되지 않는 메서드입니다", 405),
	RESOURCE_ALREADY_EXISTS("RESOURCE_002", "이미 존재하는 리소스입니다", 409),
	
	// 시스템 에러
	INTERNAL_SERVER_ERROR("SYSTEM_001", "내부 서버 오류가 발생했습니다", 500),
	DATABASE_ERROR("SYSTEM_002", "데이터베이스 오류가 발생했습니다", 500),
	EXTERNAL_API_ERROR("SYSTEM_003", "외부 API 호출 오류가 발생했습니다", 502),
	SERVICE_UNAVAILABLE("SYSTEM_004", "서비스를 일시적으로 사용할 수 없습니다", 503),
	;
	
	private final String code;
	private final String message;
	private final int statusCode;
	
	CommonErrorCode(String code, String message, int statusCode) {
		this.code = code;
		this.message = message;
		this.statusCode = statusCode;
	}
	
	@Override
	public String getCode() {
		return code;
	}
	
	@Override
	public String getMessage() {
		return message;
	}
	
	@Override
	public int getStatusCode() {
		return statusCode;
	}
}
