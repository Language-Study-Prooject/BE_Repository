package com.mzc.secondproject.serverless.common.dto.request.chatting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    private String userId;
    private String content;
    @Builder.Default
    private String messageType = "TEXT";
}
