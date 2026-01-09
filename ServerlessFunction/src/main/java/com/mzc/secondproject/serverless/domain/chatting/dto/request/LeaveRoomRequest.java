package com.mzc.secondproject.serverless.domain.chatting.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRoomRequest {

    @NotBlank(message = "is required")
    private String userId;
}
