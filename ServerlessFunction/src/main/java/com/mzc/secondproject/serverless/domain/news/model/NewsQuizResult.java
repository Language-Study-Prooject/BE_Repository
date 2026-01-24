package com.mzc.secondproject.serverless.domain.news.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.List;

/**
 * 뉴스 퀴즈 결과
 * PK: USER#{userId}#NEWS
 * SK: QUIZ#{articleId}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class NewsQuizResult {
	
	private String pk;          // USER#{userId}#NEWS
	private String sk;          // QUIZ#{articleId}
	private String gsi1pk;      // USER_NEWS_STAT#{userId}
	private String gsi1sk;      // {date}#QUIZ
	
	private String userId;
	private String articleId;
	private String articleTitle;
	private String articleLevel;
	private int score;          // 0-100
	private int totalPoints;    // 총 배점
	private int earnedPoints;   // 획득 점수
	private List<QuizAnswerResult> answers;
	private Integer timeTaken;  // 소요 시간 (초)
	private String submittedAt;
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
}
