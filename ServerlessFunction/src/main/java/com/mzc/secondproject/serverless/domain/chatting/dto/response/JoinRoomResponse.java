package com.mzc.secondproject.serverless.domain.chatting.dto.response;

import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 채팅방 입장 응답
 * 방 정보와 WebSocket 연결용 토큰 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomResponse {
    private ChatRoom room;
    private String roomToken;
    private Long tokenExpiresAt;
}
