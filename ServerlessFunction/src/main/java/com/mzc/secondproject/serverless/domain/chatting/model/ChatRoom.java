package com.mzc.secondproject.serverless.domain.chatting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class ChatRoom {
	
	private String pk;          // ROOM#{roomId}
	private String sk;          // METADATA
	private String gsi1pk;      // ROOMS
	private String gsi1sk;      // {level}#{createdAt}
	
	private String roomId;
	private String name;
	private String description;
	private String level;           // beginner, intermediate, advanced
	private Integer currentMembers;
	private Integer maxMembers;
	private Boolean isPrivate;
	private String password;        // 비밀방 비밀번호 (해시)
	private String createdBy;       // 방장 userId
	private String createdAt;
	private String lastMessageAt;
	private List<String> memberIds; // 참여 멤버 목록
	private Long ttl;

	// 게임 관련 필드
	private String gameStatus;          // NONE, WAITING, PLAYING, ROUND_END, FINISHED
	private String gameStartedBy;       // 게임 시작한 사용자 ID
	private Integer currentRound;       // 현재 라운드 (1부터 시작)
	private Integer totalRounds;        // 총 라운드 수
	private String currentDrawerId;     // 현재 출제자 userId
	private String currentWordId;       // 현재 제시어 wordId
	private String currentWord;         // 현재 제시어 (korean)
	private Long roundStartTime;        // 라운드 시작 시간 (Unix timestamp)
	private Integer roundTimeLimit;     // 라운드 제한 시간 (초)
	private List<String> drawerOrder;   // 출제 순서 (userId 목록)
	private Map<String, Integer> scores; // 사용자별 점수
	private Map<String, Integer> streaks; // 사용자별 연속 정답 수
	private Boolean hintUsed;           // 현재 라운드 힌트 사용 여부
	private List<String> correctGuessers; // 현재 라운드 정답자 목록
	
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
}
