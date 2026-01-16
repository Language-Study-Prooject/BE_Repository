package com.mzc.secondproject.serverless.domain.user.exception;

import com.mzc.secondproject.serverless.common.exception.DomainErrorCode;

/**
 * User 도메인 에러 코드
 * 사용자 프로필, 인증 관련 에러 코드를 정의합니다.
 */
public enum UserErrorCode implements DomainErrorCode {
	
	// 사용자 조회 관련 에러
	USER_NOT_FOUND("USER_001", "사용자를 찾을 수 없습니다", 404),
	
	// 프로필 수정 관련 에러
	INVALID_NICKNAME("USER_002", "닉네임은 2~20자여야 합니다", 400),
	INVALID_LEVEL("USER_003", "유효하지 않은 레벨입니다", 400),
	
	// 이미지 업로드 관련 에러
	INVALID_IMAGE_TYPE("USER_004", "지원하지 않는 이미지 형식입니다", 400),
	IMAGE_UPLOAD_FAILED("USER_005", "이미지 업로드에 실패했습니다", 500),
	
	// 인증 관련 에러
	COGNITO_SYNC_FAILED("USER_006", "Cognito 동기화에 실패했습니다", 500),
	;
	
	private static final String DOMAIN = "USER";
	
	private final String code;
	private final String message;
	private final int statusCode;
	
	UserErrorCode(String code, String message, int statusCode) {
		this.code = code;
		this.message = message;
		this.statusCode = statusCode;
	}
	
	@Override
	public String getDomain() {
		return DOMAIN;
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
