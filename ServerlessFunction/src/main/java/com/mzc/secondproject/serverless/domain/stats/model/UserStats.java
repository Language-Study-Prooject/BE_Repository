package com.mzc.secondproject.serverless.domain.stats.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * 사용자 학습 통계
 * PK: USER#{userId}#STATS
 * SK: DAILY#{date} / WEEKLY#{year}-W{week} / MONTHLY#{year}-{month} / TOTAL
 * <p>
 * Write-time Aggregation 패턴:
 * - 이벤트 발생 시 Atomic Counter로 증분 업데이트
 * - 조회 시 Scan 없이 O(1) GetItem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class UserStats {
	
	private String pk;          // USER#{userId}#STATS
	private String sk;          // DAILY#{date} / WEEKLY#{year}-W{week} / MONTHLY#{year}-{month} / TOTAL
	
	private String userId;
	private String periodType;  // DAILY, WEEKLY, MONTHLY, TOTAL
	private String period;      // 2026-01-13, 2026-W02, 2026-01, TOTAL
	
	// 테스트 통계
	private Integer testsCompleted;     // 완료한 테스트 수
	private Integer questionsAnswered;  // 답변한 문제 수
	private Integer correctAnswers;     // 정답 수
	private Integer incorrectAnswers;   // 오답 수
	private Double successRate;         // 정답률
	
	// 학습 통계
	private Integer newWordsLearned;    // 새로 학습한 단어 수
	private Integer wordsReviewed;      // 복습한 단어 수
	private Integer wordsMastered;      // 마스터한 단어 수
	
	// Streak (연속 학습)
	private Integer currentStreak;      // 현재 연속 학습일
	private Integer longestStreak;      // 최장 연속 학습일
	private String lastStudyDate;       // 마지막 학습일

	// 게임 통계
	private Integer gamesPlayed;        // 참여한 게임 수
	private Integer gamesWon;           // 1등 횟수
	private Integer correctGuesses;     // 정답 맞춘 횟수
	private Integer totalGameScore;     // 누적 게임 점수
	private Integer quickGuesses;       // 5초 내 정답 횟수
	private Integer perfectDraws;       // 전원 정답 유도 횟수

	// 메타데이터
	private String createdAt;
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
}
