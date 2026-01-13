package com.mzc.secondproject.serverless.domain.chatting.dto.request;

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
public class SendMessageRequest {
	
	@NotBlank(message = "is required")
	@Size(max = 1000, message = "must be at most 1000 characters")
	private String content;
	
	@Builder.Default
	private String messageType = "TEXT";
}
