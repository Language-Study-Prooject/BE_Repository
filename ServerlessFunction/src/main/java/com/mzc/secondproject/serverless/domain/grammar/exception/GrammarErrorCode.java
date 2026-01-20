package com.mzc.secondproject.serverless.domain.grammar.exception;

import com.mzc.secondproject.serverless.common.exception.DomainErrorCode;

public enum GrammarErrorCode implements DomainErrorCode {
	
	// 요청 검증 관련 에러
	INVALID_REQUEST("GRAMMAR_000", "잘못된 요청입니다", 400),
	
	// 문법 체크 관련 에러
	INVALID_SENTENCE("GRAMMAR_001", "유효하지 않은 문장입니다", 400),
	GRAMMAR_CHECK_FAILED("GRAMMAR_002", "문법 체크에 실패했습니다", 500),
	
	// 레벨 관련 에러
	INVALID_LEVEL("GRAMMAR_003", "유효하지 않은 레벨입니다", 400),
	
	// Bedrock API 관련 에러
	BEDROCK_API_ERROR("GRAMMAR_004", "AI 서비스 호출에 실패했습니다", 502),
	BEDROCK_RESPONSE_PARSE_ERROR("GRAMMAR_005", "AI 응답 파싱에 실패했습니다", 500),
	
	// 세션 관련 에러
	SESSION_NOT_FOUND("GRAMMAR_006", "세션을 찾을 수 없습니다", 404),
	SESSION_EXPIRED("GRAMMAR_007", "세션이 만료되었습니다", 410),
	;
	
	private static final String DOMAIN = "GRAMMAR";
	
	private final String code;
	private final String message;
	private final int statusCode;
	
	GrammarErrorCode(String code, String message, int statusCode) {
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
