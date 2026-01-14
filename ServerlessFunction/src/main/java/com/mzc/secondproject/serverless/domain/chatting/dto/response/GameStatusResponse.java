package com.mzc.secondproject.serverless.domain.chatting.dto.response;

import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;

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
		Integer roundTimeLimit,
		List<String> drawerOrder,
		Map<String, Integer> scores,
		Boolean hintUsed,
		List<String> correctGuessers
) {
	public static GameStatusResponse from(ChatRoom room, List<String> drawerOrder) {
		return new GameStatusResponse(
				room.getGameStatus(),
				room.getCurrentRound(),
				room.getTotalRounds(),
				room.getCurrentDrawerId(),
				room.getRoundStartTime(),
				room.getRoundTimeLimit(),
				drawerOrder != null ? drawerOrder : room.getDrawerOrder(),
				room.getScores(),
				room.getHintUsed(),
				room.getCorrectGuessers()
		);
	}
}
