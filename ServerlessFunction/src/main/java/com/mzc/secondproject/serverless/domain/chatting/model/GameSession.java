package com.mzc.secondproject.serverless.domain.chatting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 게임 세션 모델
 * ChatRoom에서 분리된 게임 상태 관리용 독립 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class GameSession {

	private String pk;              // GAME#{gameSessionId}
	private String sk;              // METADATA
	private String gsi1pk;          // ROOM#{roomId}
	private String gsi1sk;          // GAME#{createdAt}

	private String gameSessionId;
	private String roomId;
	private String gameType;        // "catchmind"

	// 게임 상태
	private String status;          // NONE, WAITING, PLAYING, ROUND_END, FINISHED
	private String startedBy;
	private Long startedAt;
	private Long endedAt;

	// 라운드 정보
	private Integer currentRound;
	private Integer totalRounds;
	private String currentDrawerId;
	private String currentWordId;
	private String currentWord;        // 한국어 뜻
	private String currentWordEnglish; // 영어 단어 (정답 체크용)
	private Long roundStartTime;
	private Integer roundDuration;

	// 점수 및 플레이어
	private Map<String, Integer> scores;
	private Map<String, Integer> streaks;
	private List<String> players;
	private List<String> drawerOrder;

	// 라운드 내 상태
	private Boolean hintUsed;
	private List<String> correctGuessers;

	// 스케줄링 (게임 자동 종료용)
	private Long gameEndScheduledAt;
	private String scheduleRuleArn;

	// TTL (게임 종료 후 일정 시간 뒤 삭제)
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

	/**
	 * 게임이 활성 상태인지 확인
	 */
	public boolean isActive() {
		return "PLAYING".equals(status) || "ROUND_END".equals(status);
	}

	/**
	 * 게임 시작 가능 여부 확인
	 */
	public boolean canStart() {
		return status == null || "NONE".equals(status) || "FINISHED".equals(status);
	}

	/**
	 * 출제자 여부 확인
	 */
	public boolean isDrawer(String userId) {
		return userId != null && userId.equals(currentDrawerId);
	}

	/**
	 * 이미 정답을 맞춘 사용자인지 확인
	 */
	public boolean hasAlreadyGuessedCorrect(String userId) {
		return correctGuessers != null && correctGuessers.contains(userId);
	}

	/**
	 * 정답자 추가
	 */
	public void addCorrectGuesser(String userId) {
		if (correctGuessers == null) {
			correctGuessers = new java.util.ArrayList<>();
		}
		if (!correctGuessers.contains(userId)) {
			correctGuessers.add(userId);
		}
	}

	/**
	 * 점수 추가
	 */
	public void addScore(String userId, int points) {
		if (scores == null) {
			scores = new java.util.HashMap<>();
		}
		scores.merge(userId, points, Integer::sum);
	}

	/**
	 * 연속 정답 수 증가
	 */
	public int incrementStreak(String userId) {
		if (streaks == null) {
			streaks = new java.util.HashMap<>();
		}
		int newStreak = streaks.getOrDefault(userId, 0) + 1;
		streaks.put(userId, newStreak);
		return newStreak;
	}

	/**
	 * 연속 정답 수 리셋
	 */
	public void resetStreak(String userId) {
		if (streaks != null) {
			streaks.put(userId, 0);
		}
	}

	/**
	 * 다음 출제자 ID 반환
	 */
	public String getNextDrawerId() {
		if (drawerOrder == null || drawerOrder.isEmpty()) {
			return null;
		}
		if (currentDrawerId == null) {
			return drawerOrder.get(0);
		}
		int currentIndex = drawerOrder.indexOf(currentDrawerId);
		if (currentIndex == -1 || currentIndex >= drawerOrder.size() - 1) {
			return drawerOrder.get(0);
		}
		return drawerOrder.get(currentIndex + 1);
	}

	/**
	 * 전원이 정답을 맞췄는지 확인
	 */
	public boolean allPlayersGuessedCorrect() {
		if (players == null || correctGuessers == null) {
			return false;
		}
		// 출제자 제외한 인원이 모두 정답
		long guessersCount = players.stream()
				.filter(p -> !p.equals(currentDrawerId))
				.count();
		return correctGuessers.size() >= guessersCount;
	}
}
