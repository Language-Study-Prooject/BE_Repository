package com.mzc.secondproject.serverless.domain.chatting.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
public class LeaveRoomRequest {
    // 토큰에서 userId를 추출하므로 별도 필드 불필요
}
