package com.mzc.secondproject.serverless.domain.chatting.dto.response;

import com.mzc.secondproject.serverless.domain.chatting.model.GameSession;

import java.util.List;
import java.util.Map;

/**
 * 게임 상태 응답 DTO
 */
public record GameStatusResponse(
		String gameStatus,
		Integer currentRound,
		Integer totalRounds,
		String currentDrawerId,
		Long roundStartTime,
		Integer roundDuration,
		List<String> drawerOrder,
		Map<String, Integer> scores,
		Boolean hintUsed,
		List<String> correctGuessers
) {
	public static GameStatusResponse from(GameSession session) {
		return new GameStatusResponse(
				session.getStatus(),
				session.getCurrentRound(),
				session.getTotalRounds(),
				session.getCurrentDrawerId(),
				session.getRoundStartTime(),
				session.getRoundDuration(),
				session.getDrawerOrder(),
				session.getScores(),
				session.getHintUsed(),
				session.getCorrectGuessers()
		);
	}
}
