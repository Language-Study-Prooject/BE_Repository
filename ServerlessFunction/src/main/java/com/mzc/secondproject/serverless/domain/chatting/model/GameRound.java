package com.mzc.secondproject.serverless.domain.chatting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 캐치마인드 게임 라운드 기록
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class GameRound {
	
	private String pk;              // ROOM#{roomId}#GAME
	private String sk;              // ROUND#{roundNumber}
	
	private String roomId;
	private Integer roundNumber;
	private String drawerId;        // 출제자 userId
	private String wordId;          // 제시어 wordId
	private String word;            // 제시어 (korean)
	private String wordEnglish;     // 제시어 (english)
	
	private List<String> correctGuessers;       // 정답 맞춘 순서
	private Map<String, Long> guessTimes;       // userId -> 정답까지 걸린 시간(ms)
	private Map<String, Integer> roundScores;   // userId -> 이 라운드 획득 점수
	
	private Long startTime;         // 라운드 시작 시간 (Unix timestamp ms)
	private Long endTime;           // 라운드 종료 시간
	private String endReason;       // TIME_UP, ALL_CORRECT, SKIP
	
	private Boolean hintUsed;       // 힌트 사용 여부
	private String createdAt;
	private Long ttl;               // 자동 만료 (7일 후)
	
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
	
	/**
	 * 라운드가 진행 중인지 확인
	 */
	@DynamoDbIgnore
	public boolean isActive() {
		return endTime == null;
	}
	
	/**
	 * 경과 시간 계산 (ms)
	 */
	@DynamoDbIgnore
	public long getElapsedTime() {
		if (startTime == null) return 0;
		long end = endTime != null ? endTime : System.currentTimeMillis();
		return end - startTime;
	}
}
