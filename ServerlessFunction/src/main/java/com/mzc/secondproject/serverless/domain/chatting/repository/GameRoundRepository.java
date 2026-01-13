package com.mzc.secondproject.serverless.domain.chatting.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.chatting.model.GameRound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 게임 라운드 저장소
 */
public class GameRoundRepository {

	private static final Logger logger = LoggerFactory.getLogger(GameRoundRepository.class);
	private static final String TABLE_NAME = System.getenv("CHAT_TABLE_NAME");

	private final DynamoDbTable<GameRound> table;

	public GameRoundRepository() {
		this.table = AwsClients.dynamoDbEnhanced().table(TABLE_NAME, TableSchema.fromBean(GameRound.class));
	}

	public GameRound save(GameRound gameRound) {
		logger.info("Saving game round: roomId={}, round={}", gameRound.getRoomId(), gameRound.getRoundNumber());
		table.putItem(gameRound);
		return gameRound;
	}

	public Optional<GameRound> findByRoomIdAndRound(String roomId, Integer roundNumber) {
		Key key = Key.builder()
				.partitionValue("ROOM#" + roomId + "#GAME")
				.sortValue("ROUND#" + roundNumber)
				.build();

		GameRound round = table.getItem(key);
		return Optional.ofNullable(round);
	}

	/**
	 * 특정 게임의 모든 라운드 조회
	 */
	public List<GameRound> findByRoomId(String roomId) {
		QueryConditional queryConditional = QueryConditional
				.keyEqualTo(Key.builder()
						.partitionValue("ROOM#" + roomId + "#GAME")
						.build());

		QueryEnhancedRequest request = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.build();

		return table.query(request).stream()
				.flatMap(page -> page.items().stream())
				.collect(Collectors.toList());
	}

	/**
	 * 특정 게임의 모든 라운드 삭제
	 */
	public void deleteByRoomId(String roomId) {
		List<GameRound> rounds = findByRoomId(roomId);
		for (GameRound round : rounds) {
			Key key = Key.builder()
					.partitionValue(round.getPk())
					.sortValue(round.getSk())
					.build();
			table.deleteItem(key);
		}
		logger.info("Deleted {} rounds for roomId={}", rounds.size(), roomId);
	}
}
