package com.mzc.secondproject.serverless.domain.chatting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.List;

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

	// 게임 세션 참조 (게임 상태는 GameSession으로 분리됨)
	private String activeGameSessionId; // 현재 진행중인 게임 세션 ID (nullable)

	private String type;           // CHAT, GAME (기본값: CHAT)
	private String gameType;       // CATCHMIND (nullable, GAME 타입일 때만)
	private GameSettings gameSettings;  // 게임 설정 (nullable)
	private String status;         // WAITING, PLAYING, FINISHED (기본값: WAITING)
	private String hostId;         // 방장 userId (createdBy와 별도 관리)

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
