package com.mzc.secondproject.serverless.domain.chatting.dto.response;

import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;

/**
 * 채팅방 입장 응답
 * 방 정보와 WebSocket 연결용 토큰 포함
 */
public record JoinRoomResponse(
		ChatRoom room,
		String roomToken,
		Long tokenExpiresAt
) {
	public static JoinRoomResponse of(ChatRoom room, String roomToken, Long tokenExpiresAt) {
		return new JoinRoomResponse(room, roomToken, tokenExpiresAt);
	}
}
