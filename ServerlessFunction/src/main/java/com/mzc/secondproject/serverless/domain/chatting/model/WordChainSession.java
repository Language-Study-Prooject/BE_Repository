package com.mzc.secondproject.serverless.domain.chatting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.*;

/**
 * 끝말잇기 게임 세션 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class WordChainSession {

	private String pk;              // WORDCHAIN#{sessionId}
	private String sk;              // METADATA
	private String gsi1pk;          // ROOM#{roomId}
	private String gsi1sk;          // WORDCHAIN#{createdAt}

	private String sessionId;
	private String roomId;
	private String gameType;        // "wordchain"

	// 게임 상태
	private String status;          // WAITING, PLAYING, FINISHED
	private String startedBy;
	private Long startedAt;
	private Long endedAt;

	// 턴 정보
	private Integer currentRound;
	private String currentPlayerId;
	private String currentWord;
	private Character nextLetter;   // 다음 사람이 시작해야 할 글자
	private Long turnStartTime;
	private Integer timeLimit;      // 현재 라운드 시간 제한 (초)

	// 플레이어 관리
	private List<String> players;           // 전체 플레이어 (순서대로)
	private List<String> activePlayers;     // 탈락하지 않은 플레이어
	private List<String> eliminatedPlayers; // 탈락한 플레이어
	private Map<String, Integer> scores;

	// 게임 기록
	private List<String> usedWords;         // 사용된 단어 목록
	private Map<String, String> wordDefinitions; // 단어 -> 뜻 (게임 종료 후 학습용)

	// TTL
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

	// ========== 비즈니스 메서드 ==========

	/**
	 * 게임이 활성 상태인지 확인
	 */
	public boolean isActive() {
		return "PLAYING".equals(status);
	}

	/**
	 * 현재 턴인지 확인
	 */
	public boolean isCurrentTurn(String userId) {
		return userId != null && userId.equals(currentPlayerId);
	}

	/**
	 * 단어가 이미 사용되었는지 확인
	 */
	public boolean isWordUsed(String word) {
		return usedWords != null && usedWords.contains(word.toLowerCase());
	}

	/**
	 * 단어 추가
	 */
	public void addUsedWord(String word, String definition) {
		if (usedWords == null) {
			usedWords = new ArrayList<>();
		}
		usedWords.add(word.toLowerCase());

		if (definition != null) {
			if (wordDefinitions == null) {
				wordDefinitions = new HashMap<>();
			}
			wordDefinitions.put(word.toLowerCase(), definition);
		}
	}

	/**
	 * 플레이어 탈락 처리
	 */
	public void eliminatePlayer(String userId) {
		if (activePlayers != null) {
			activePlayers.remove(userId);
		}
		if (eliminatedPlayers == null) {
			eliminatedPlayers = new ArrayList<>();
		}
		if (!eliminatedPlayers.contains(userId)) {
			eliminatedPlayers.add(userId);
		}
	}

	/**
	 * 다음 플레이어 ID 반환
	 */
	public String getNextPlayerId() {
		if (activePlayers == null || activePlayers.isEmpty()) {
			return null;
		}
		if (activePlayers.size() == 1) {
			return activePlayers.get(0); // 마지막 1명 = 승자
		}
		if (currentPlayerId == null) {
			return activePlayers.get(0);
		}
		int currentIndex = activePlayers.indexOf(currentPlayerId);
		if (currentIndex == -1) {
			return activePlayers.get(0);
		}
		return activePlayers.get((currentIndex + 1) % activePlayers.size());
	}

	/**
	 * 점수 추가
	 */
	public void addScore(String userId, int points) {
		if (scores == null) {
			scores = new HashMap<>();
		}
		scores.merge(userId, points, Integer::sum);
	}

	/**
	 * 게임 종료 조건 확인 (1명만 남음)
	 */
	public boolean isGameOver() {
		return activePlayers == null || activePlayers.size() <= 1;
	}

	/**
	 * 승자 반환
	 */
	public String getWinner() {
		if (activePlayers != null && activePlayers.size() == 1) {
			return activePlayers.get(0);
		}
		return null;
	}

	/**
	 * 시간 제한 계산 (라운드에 따라 점점 빨라짐)
	 * Round 1-2: 15초, Round 3-4: 13초, Round 5-6: 11초, Round 7-8: 9초, Round 9+: 8초
	 */
	public static int calculateTimeLimit(int round) {
		return Math.max(8, 15 - ((round - 1) / 2) * 2);
	}

	/**
	 * 점수 계산 (빠른 응답 + 긴 단어 보너스)
	 */
	public static int calculateScore(long responseTimeMs, int wordLength, int timeLimit) {
		int baseScore = 10;

		// 시간 보너스 (빠를수록 높음)
		int remainingSeconds = timeLimit - (int)(responseTimeMs / 1000);
		int timeBonus = Math.max(0, remainingSeconds);

		// 단어 길이 보너스 (5글자 이상부터)
		int lengthBonus = Math.max(0, (wordLength - 4) * 2);

		return baseScore + timeBonus + lengthBonus;
	}
}
