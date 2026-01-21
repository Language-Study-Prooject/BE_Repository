package com.mzc.secondproject.serverless.domain.chatting.dto.response;

import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.model.GameSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 방 목록 조회 시 사용되는 응답 DTO
 * ChatRoom + hostNickname 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomListItem {
	private String roomId;
	private String name;
	private String description;
	private String level;
	private Integer currentMembers;
	private Integer maxMembers;
	private Boolean isPrivate;
	private String createdBy;
	private String createdAt;
	private String lastMessageAt;
	private String type;
	private String gameType;
	private GameSettings gameSettings;
	private String status;
	private String hostId;
	private String hostNickname;
	private List<RoomParticipant> participants;

	/**
	 * ChatRoom과 hostNickname으로 RoomListItem 생성
	 */
	public static RoomListItem from(ChatRoom room, String hostNickname) {
		return RoomListItem.builder()
				.roomId(room.getRoomId())
				.name(room.getName())
				.description(room.getDescription())
				.level(room.getLevel())
				.currentMembers(room.getCurrentMembers())
				.maxMembers(room.getMaxMembers())
				.isPrivate(room.getIsPrivate())
				.createdBy(room.getCreatedBy())
				.createdAt(room.getCreatedAt())
				.lastMessageAt(room.getLastMessageAt())
				.type(room.getType())
				.gameType(room.getGameType())
				.gameSettings(room.getGameSettings())
				.status(room.getStatus())
				.hostId(room.getHostId())
				.hostNickname(hostNickname)
				.build();
	}

	/**
	 * ChatRoom, hostNickname, participants로 RoomListItem 생성
	 */
	public static RoomListItem from(ChatRoom room, String hostNickname, List<RoomParticipant> participants) {
		RoomListItem item = from(room, hostNickname);
		item.setParticipants(participants);
		return item;
	}
}
