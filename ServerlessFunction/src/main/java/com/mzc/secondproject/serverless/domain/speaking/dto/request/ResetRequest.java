package com.mzc.secondproject.serverless.domain.speaking.dto.request;

/**
 * 대화 초기화 요청 DTO
 */
public record ResetRequest(
        String sessionId
) {
    public boolean isValid() {
        return sessionId != null && !sessionId.isEmpty();
    }
}