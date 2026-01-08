package com.mzc.secondproject.serverless.common.dto.request.chatting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {
    private String name;
    private String description;
    @Builder.Default
    private String level = "beginner";
    @Builder.Default
    private Integer maxMembers = 6;
    @Builder.Default
    private Boolean isPrivate = false;
    private String password;
    private String createdBy;
}
