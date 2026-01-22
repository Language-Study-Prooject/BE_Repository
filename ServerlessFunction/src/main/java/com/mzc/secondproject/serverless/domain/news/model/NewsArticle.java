package com.mzc.secondproject.serverless.domain.news.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.List;

/**
 * 뉴스 기사 모델
 * PK: NEWS#{date}
 * SK: ARTICLE#{articleId}
 * GSI1: LEVEL#{level} / {publishedAt} - 레벨별 최신순 조회
 * GSI2: CATEGORY#{category} / {publishedAt} - 카테고리별 최신순 조회
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class NewsArticle {

	private String pk;          // NEWS#{date}
	private String sk;          // ARTICLE#{articleId}
	private String gsi1pk;      // LEVEL#{level}
	private String gsi1sk;      // {publishedAt}
	private String gsi2pk;      // CATEGORY#{category}
	private String gsi2sk;      // {publishedAt}

	// 기본 정보
	private String articleId;
	private String title;
	private String summary;         // AI 생성 3줄 요약
	private String originalUrl;     // 원문 링크
	private String source;          // BBC, VOA, NPR, NewsAPI
	private String imageUrl;        // 썸네일 이미지

	// 분류
	private String category;        // TECH, BUSINESS, SPORTS 등
	private String level;           // BEGINNER, INTERMEDIATE, ADVANCED
	private String cefrLevel;       // A1, A2, B1, B2, C1, C2 (원본 CEFR 레벨)

	// AI 분석 결과
	private List<KeywordInfo> keywords;         // 핵심 단어 정보
	private List<String> highlightWords;        // 사용자 레벨 대비 어려운 단어
	private List<QuizQuestion> quiz;            // 퀴즈 문제 (5개)

	// 메타데이터
	private String publishedAt;     // 원본 발행일
	private String collectedAt;     // 수집일
	private Long readCount;         // 조회수
	private Long commentCount;      // 댓글수
	private Long ttl;

	@DynamoDbPartitionKey
	@DynamoDbAttribute("PK")
	public String getPk() {
		return pk;
	}

	@DynamoDbSortKey
	@DynamoDbAttribute("SK")
	public String getSk() {
		return sk;
	}

	@DynamoDbSecondaryPartitionKey(indexNames = "GSI1")
	@DynamoDbAttribute("GSI1PK")
	public String getGsi1pk() {
		return gsi1pk;
	}

	@DynamoDbSecondarySortKey(indexNames = "GSI1")
	@DynamoDbAttribute("GSI1SK")
	public String getGsi1sk() {
		return gsi1sk;
	}

	@DynamoDbSecondaryPartitionKey(indexNames = "GSI2")
	@DynamoDbAttribute("GSI2PK")
	public String getGsi2pk() {
		return gsi2pk;
	}

	@DynamoDbSecondarySortKey(indexNames = "GSI2")
	@DynamoDbAttribute("GSI2SK")
	public String getGsi2sk() {
		return gsi2sk;
	}
}
