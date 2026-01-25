package com.mzc.secondproject.serverless.domain.chatting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomParticipant {
	private String userId;
	private String nickname;
	private Boolean isHost;
}
