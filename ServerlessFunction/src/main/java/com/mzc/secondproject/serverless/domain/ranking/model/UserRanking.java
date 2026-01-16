package com.mzc.secondproject.serverless.domain.ranking.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * 사용자 랭킹
 * PK: RANKING#{period}  (예: RANKING#DAILY#2026-01-16, RANKING#WEEKLY#2026-W03)
 * SK: SCORE#{invertedScore}#{userId}  (역순 정렬용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class UserRanking {

	private String pk;
	private String sk;
	private String userId;
	private String periodType;   // DAILY, WEEKLY, MONTHLY, TOTAL
	private String period;       // 2026-01-16, 2026-W03, 2026-01, TOTAL
	private Integer score;
	private String nickname;
	private String profileUrl;
	private String updatedAt;

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

	public static String buildPk(String periodType, String period) {
		return "RANKING#" + periodType + "#" + period;
	}

	public static String buildSk(int score, String userId) {
		int invertedScore = 999999 - score;
		return String.format("SCORE#%06d#%s", invertedScore, userId);
	}

	public static UserRanking create(String periodType, String period, String userId, int score) {
		return UserRanking.builder()
				.pk(buildPk(periodType, period))
				.sk(buildSk(score, userId))
				.userId(userId)
				.periodType(periodType)
				.period(period)
				.score(score)
				.build();
	}
}
