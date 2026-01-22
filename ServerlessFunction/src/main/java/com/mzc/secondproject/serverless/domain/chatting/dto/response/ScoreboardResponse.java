package com.mzc.secondproject.serverless.domain.chatting.dto.response;

import com.mzc.secondproject.serverless.domain.chatting.model.GameSession;

import java.util.List;
import java.util.Map;

/**
 * 점수판 응답 DTO
 */
public record ScoreboardResponse(
		Map<String, Integer> scores,
		List<RankEntry> ranking,
		String gameStatus,
		Integer currentRound,
		Integer totalRounds
) {
	public static ScoreboardResponse from(GameSession session) {
		Map<String, Integer> scores = session.getScores();
		List<RankEntry> ranking = buildRanking(scores);
		
		return new ScoreboardResponse(
				scores,
				ranking,
				session.getStatus(),
				session.getCurrentRound(),
				session.getTotalRounds()
		);
	}
	
	private static List<RankEntry> buildRanking(Map<String, Integer> scores) {
		if (scores == null || scores.isEmpty()) {
			return List.of();
		}
		
		List<Map.Entry<String, Integer>> sorted = scores.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.toList();
		
		return java.util.stream.IntStream.range(0, sorted.size())
				.mapToObj(i -> new RankEntry(i + 1, sorted.get(i).getKey(), sorted.get(i).getValue()))
				.toList();
	}
	
	public record RankEntry(
			int rank,
			String userId,
			int score
	) {
	}
}
