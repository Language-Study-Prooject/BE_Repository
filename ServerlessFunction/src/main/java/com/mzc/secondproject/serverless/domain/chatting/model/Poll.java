package com.mzc.secondproject.serverless.domain.chatting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 채팅방 투표 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Poll {

	private String pk;          // ROOM#{roomId}
	private String sk;          // POLL#{pollId}

	private String pollId;
	private String roomId;
	private String question;
	private List<String> options;
	private Map<String, Integer> votes;      // optionIndex -> count
	private Map<String, Integer> userVotes;  // userId -> optionIndex
	private String createdBy;
	private String createdAt;
	private Boolean isActive;
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

	/**
	 * 투표 추가
	 */
	public boolean addVote(String userId, int optionIndex) {
		if (optionIndex < 0 || optionIndex >= options.size()) {
			return false;
		}

		// 이미 투표했는지 확인
		if (userVotes.containsKey(userId)) {
			return false;
		}

		userVotes.put(userId, optionIndex);
		votes.merge(String.valueOf(optionIndex), 1, Integer::sum);
		return true;
	}

	/**
	 * 사용자가 이미 투표했는지 확인
	 */
	public boolean hasVoted(String userId) {
		return userVotes != null && userVotes.containsKey(userId);
	}

	/**
	 * 총 투표 수
	 */
	public int getTotalVotes() {
		if (votes == null) return 0;
		return votes.values().stream().mapToInt(Integer::intValue).sum();
	}
}
