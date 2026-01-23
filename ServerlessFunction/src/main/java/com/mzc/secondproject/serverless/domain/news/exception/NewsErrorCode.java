package com.mzc.secondproject.serverless.domain.news.exception;

import com.mzc.secondproject.serverless.common.exception.DomainErrorCode;

/**
 * 뉴스 도메인 에러 코드
 * 뉴스 기사, 퀴즈, 단어 수집, 댓글 관련 에러 코드를 정의합니다.
 */
public enum NewsErrorCode implements DomainErrorCode {

	// 일반 에러
	INVALID_REQUEST("COMMON_001", "유효하지 않은 요청입니다", 400),

	// 인증 관련 에러
	UNAUTHORIZED("AUTH_001", "인증이 필요합니다", 401),

	// 뉴스 기사 관련 에러
	ARTICLE_NOT_FOUND("ARTICLE_001", "뉴스 기사를 찾을 수 없습니다", 404),
	INVALID_ARTICLE_DATA("ARTICLE_002", "뉴스 기사 데이터가 유효하지 않습니다", 400),
	ARTICLE_ALREADY_EXISTS("ARTICLE_003", "이미 존재하는 뉴스 기사입니다", 409),

	// 카테고리/레벨 관련 에러
	INVALID_CATEGORY("CATEGORY_001", "유효하지 않은 카테고리입니다", 400),
	INVALID_LEVEL("LEVEL_001", "유효하지 않은 레벨입니다", 400),

	// 읽기 기록 관련 에러
	READ_RECORD_NOT_FOUND("READ_001", "읽기 기록을 찾을 수 없습니다", 404),
	ALREADY_READ("READ_002", "이미 읽은 기사입니다", 409),

	// 퀴즈 관련 에러
	QUIZ_NOT_FOUND("QUIZ_001", "퀴즈를 찾을 수 없습니다", 404),
	QUIZ_ALREADY_SUBMITTED("QUIZ_002", "이미 제출한 퀴즈입니다", 409),
	INVALID_QUIZ_ANSWER("QUIZ_003", "유효하지 않은 퀴즈 답변입니다", 400),

	// 단어 수집 관련 에러
	WORD_ALREADY_COLLECTED("WORD_001", "이미 수집한 단어입니다", 409),
	WORD_NOT_COLLECTED("WORD_002", "수집한 단어를 찾을 수 없습니다", 404),

	// 북마크 관련 에러
	BOOKMARK_NOT_FOUND("BOOKMARK_001", "북마크를 찾을 수 없습니다", 404),
	ALREADY_BOOKMARKED("BOOKMARK_002", "이미 북마크한 기사입니다", 409),
	BOOKMARK_LIMIT_EXCEEDED("BOOKMARK_003", "북마크 한도를 초과했습니다", 400),

	// 댓글 관련 에러
	COMMENT_NOT_FOUND("COMMENT_001", "댓글을 찾을 수 없습니다", 404),
	COMMENT_NOT_OWNER("COMMENT_002", "댓글 작성자만 수정/삭제할 수 있습니다", 403),
	INVALID_COMMENT_DATA("COMMENT_003", "유효하지 않은 댓글 데이터입니다", 400),

	// 통계 관련 에러
	STATS_NOT_FOUND("STATS_001", "통계 정보를 찾을 수 없습니다", 404);

	private static final String DOMAIN = "NEWS";

	private final String code;
	private final String message;
	private final int statusCode;

	NewsErrorCode(String code, String message, int statusCode) {
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
