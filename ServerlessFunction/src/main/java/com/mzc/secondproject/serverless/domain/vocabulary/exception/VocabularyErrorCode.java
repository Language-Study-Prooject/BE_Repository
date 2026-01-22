package com.mzc.secondproject.serverless.domain.vocabulary.exception;

import com.mzc.secondproject.serverless.common.exception.DomainErrorCode;

/**
 * 단어 학습 도메인 에러 코드
 * <p>
 * 단어(Word), 사용자 단어(UserWord), 일일 학습(DailyStudy) 관련 에러 코드를 정의합니다.
 */
public enum VocabularyErrorCode implements DomainErrorCode {
	
	// 단어 관련 에러
	WORD_NOT_FOUND("WORD_001", "단어를 찾을 수 없습니다", 404),
	WORD_ALREADY_EXISTS("WORD_002", "이미 존재하는 단어입니다", 409),
	INVALID_WORD_DATA("WORD_003", "단어 데이터가 유효하지 않습니다", 400),
	
	// 사용자 단어 관련 에러
	USER_WORD_NOT_FOUND("USER_WORD_001", "사용자 단어 정보를 찾을 수 없습니다", 404),
	INVALID_DIFFICULTY("USER_WORD_002", "유효하지 않은 난이도입니다", 400),
	INVALID_WORD_STATUS("USER_WORD_003", "유효하지 않은 단어 상태입니다", 400),
	
	// 학습 관련 에러
	DAILY_STUDY_NOT_FOUND("STUDY_001", "일일 학습 정보를 찾을 수 없습니다", 404),
	STUDY_LIMIT_EXCEEDED("STUDY_002", "일일 학습 한도를 초과했습니다", 400),
	INVALID_STUDY_LEVEL("STUDY_003", "유효하지 않은 학습 레벨입니다", 400),
	
	// 카테고리/레벨 관련 에러
	INVALID_CATEGORY("CATEGORY_001", "유효하지 않은 카테고리입니다", 400),
	INVALID_LEVEL("LEVEL_001", "유효하지 않은 레벨입니다", 400),
	
	// 단어 그룹 관련 에러
	GROUP_NOT_FOUND("GROUP_001", "단어 그룹을 찾을 수 없습니다", 404),
	GROUP_ALREADY_EXISTS("GROUP_002", "이미 존재하는 그룹입니다", 409),
	
	// 테스트 관련 에러
	TEST_NOT_FOUND("TEST_001", "테스트 정보를 찾을 수 없습니다", 404),
	NO_WORDS_TO_TEST("TEST_002", "테스트할 단어가 없습니다", 400),
	;
	
	private static final String DOMAIN = "VOCABULARY";
	
	private final String code;
	private final String message;
	private final int statusCode;
	
	VocabularyErrorCode(String code, String message, int statusCode) {
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
