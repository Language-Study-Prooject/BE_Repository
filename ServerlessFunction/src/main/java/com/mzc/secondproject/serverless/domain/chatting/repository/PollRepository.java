package com.mzc.secondproject.serverless.domain.chatting.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.chatting.model.Poll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Optional;

/**
 * Poll Repository
 */
public class PollRepository {

	private static final Logger logger = LoggerFactory.getLogger(PollRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("CHAT_TABLE_NAME");

	private final DynamoDbTable<Poll> table;

	public PollRepository() {
		this.table = AwsClients.dynamoDbEnhanced().table(TABLE_NAME, TableSchema.fromBean(Poll.class));
	}

	public PollRepository(DynamoDbTable<Poll> table) {
		this.table = table;
	}

	public void save(Poll poll) {
		table.putItem(poll);
		logger.debug("Saved poll: {}", poll.getPollId());
	}

	public Optional<Poll> findById(String roomId, String pollId) {
		Key key = Key.builder()
				.partitionValue("ROOM#" + roomId)
				.sortValue("POLL#" + pollId)
				.build();
		Poll poll = table.getItem(key);
		return Optional.ofNullable(poll);
	}

	/**
	 * 방의 활성 투표 조회
	 */
	public Optional<Poll> findActiveByRoomId(String roomId) {
		return table.query(QueryConditional.sortBeginsWith(
						Key.builder()
								.partitionValue("ROOM#" + roomId)
								.sortValue("POLL#")
								.build()))
				.items()
				.stream()
				.filter(poll -> Boolean.TRUE.equals(poll.getIsActive()))
				.findFirst();
	}

	public void delete(String roomId, String pollId) {
		Key key = Key.builder()
				.partitionValue("ROOM#" + roomId)
				.sortValue("POLL#" + pollId)
				.build();
		table.deleteItem(key);
		logger.debug("Deleted poll: {}", pollId);
	}
}
