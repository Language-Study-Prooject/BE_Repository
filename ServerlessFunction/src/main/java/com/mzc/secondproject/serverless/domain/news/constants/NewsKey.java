package com.mzc.secondproject.serverless.domain.news.constants;

import com.mzc.secondproject.serverless.common.constants.DynamoDbKey;

/**
 * 뉴스 도메인 DynamoDB 키 상수 및 빌더
 */
public final class NewsKey {
	
	// Entity Prefixes
	public static final String NEWS = "NEWS#";
	public static final String ARTICLE = "ARTICLE#";
	public static final String LEVEL = "LEVEL#";
	public static final String CATEGORY = "CATEGORY#";
	public static final String READ = "READ#";
	public static final String QUIZ = "QUIZ#";
	public static final String WORD = "WORD#";
	public static final String BOOKMARK = "BOOKMARK#";
	public static final String COMMENT = "COMMENT#";
	public static final String STATS = "STATS";
	
	// User Suffixes
	public static final String SUFFIX_NEWS = "#NEWS";
	public static final String SUFFIX_NEWS_WORDS = "#NEWS_WORDS";
	public static final String SUFFIX_NEWS_COMMENTS = "#NEWS_COMMENTS";
	
	private NewsKey() {
	}
	
	// === Key Builders ===
	
	/**
	 * 뉴스 기사 PK: NEWS#{date}
	 */
	public static String newsPk(String date) {
		return NEWS + date;
	}
	
	/**
	 * 뉴스 기사 SK: ARTICLE#{articleId}
	 */
	public static String articleSk(String articleId) {
		return ARTICLE + articleId;
	}
	
	/**
	 * 레벨별 조회 GSI1 PK: LEVEL#{level}
	 */
	public static String levelPk(String level) {
		return LEVEL + level;
	}
	
	/**
	 * 카테고리별 조회 GSI2 PK: CATEGORY#{category}
	 */
	public static String categoryPk(String category) {
		return CATEGORY + category;
	}
	
	/**
	 * 사용자 뉴스 활동 PK: USER#{userId}#NEWS
	 */
	public static String userNewsPk(String userId) {
		return DynamoDbKey.USER + userId + SUFFIX_NEWS;
	}
	
	/**
	 * 읽기 기록 SK: READ#{articleId}
	 */
	public static String readSk(String articleId) {
		return READ + articleId;
	}
	
	/**
	 * 퀴즈 결과 SK: QUIZ#{articleId}
	 */
	public static String quizSk(String articleId) {
		return QUIZ + articleId;
	}
	
	/**
	 * 단어 수집 SK: WORD#{word}#{articleId}
	 */
	public static String wordSk(String word, String articleId) {
		return WORD + word + "#" + articleId;
	}
	
	/**
	 * 북마크 SK: BOOKMARK#{articleId}
	 */
	public static String bookmarkSk(String articleId) {
		return BOOKMARK + articleId;
	}
	
	/**
	 * 사용자 수집 단어 GSI1 PK: USER#{userId}#NEWS_WORDS
	 */
	public static String userNewsWordsPk(String userId) {
		return DynamoDbKey.USER + userId + SUFFIX_NEWS_WORDS;
	}
	
	/**
	 * 댓글 PK: NEWS_COMMENT#{articleId}
	 */
	public static String commentPk(String articleId) {
		return "NEWS_COMMENT#" + articleId;
	}
	
	/**
	 * 댓글 SK: COMMENT#{commentId}
	 */
	public static String commentSk(String commentId) {
		return COMMENT + commentId;
	}
	
	/**
	 * 사용자 댓글 GSI1 PK: USER#{userId}#NEWS_COMMENTS
	 */
	public static String userNewsCommentsPk(String userId) {
		return DynamoDbKey.USER + userId + SUFFIX_NEWS_COMMENTS;
	}
	
	/**
	 * 사용자 뉴스 통계 GSI1 PK: USER_NEWS_STAT#{userId}
	 */
	public static String userNewsStatPk(String userId) {
		return "USER_NEWS_STAT#" + userId;
	}
}
