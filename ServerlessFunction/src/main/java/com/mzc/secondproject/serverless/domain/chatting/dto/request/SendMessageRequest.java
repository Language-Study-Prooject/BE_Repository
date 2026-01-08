package com.mzc.secondproject.serverless.domain.chatting.dto.request;

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
