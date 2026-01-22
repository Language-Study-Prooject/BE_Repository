package com.mzc.secondproject.serverless.domain.chatting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 실시간 점수 업데이트 메시지 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreUpdateMessage {
	private String domain;
	private String messageType;
	private String roomId;
	private String scorerId;
	private Integer scoreGained;
	private Integer totalScore;
	private List<RankEntry> ranking;
	private Integer currentRound;
	private Integer totalRounds;
	private String timestamp;
	
	public static ScoreUpdateMessage from(String roomId, String scorerId, int scoreGained,
	                                      Map<String, Integer> scores, int currentRound, int totalRounds) {
		List<RankEntry> ranking = buildRanking(scores);
		
		return ScoreUpdateMessage.builder()
				.domain("game")
				.messageType("SCORE_UPDATE")
				.roomId(roomId)
				.scorerId(scorerId)
				.scoreGained(scoreGained)
				.totalScore(scores.getOrDefault(scorerId, 0))
				.ranking(ranking)
				.currentRound(currentRound)
				.totalRounds(totalRounds)
				.timestamp(java.time.Instant.now().toString())
				.build();
	}
	
	private static List<RankEntry> buildRanking(Map<String, Integer> scores) {
		if (scores == null || scores.isEmpty()) {
			return List.of();
		}
		
		List<Map.Entry<String, Integer>> sorted = scores.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.toList();
		
		return java.util.stream.IntStream.range(0, sorted.size())
				.mapToObj(i -> RankEntry.builder()
						.rank(i + 1)
						.userId(sorted.get(i).getKey())
						.score(sorted.get(i).getValue())
						.change(0)
						.build())
				.toList();
	}
	
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RankEntry {
		private Integer rank;
		private String userId;
		private Integer score;
		private Integer change;
	}
}
