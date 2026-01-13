package com.mzc.secondproject.serverless.domain.chatting.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {

    @NotBlank(message = "is required")
    @Size(min = 1, max = 50, message = "must be between 1 and 50 characters")
    private String name;

    @Size(max = 200, message = "must be at most 200 characters")
    private String description;

    @Builder.Default
    private String level = "beginner";

    @Min(value = 2, message = "must be at least 2")
    @Max(value = 10, message = "must be at most 10")
    @Builder.Default
    private Integer maxMembers = 6;

    @Builder.Default
    private Boolean isPrivate = false;

    private String password;
}
