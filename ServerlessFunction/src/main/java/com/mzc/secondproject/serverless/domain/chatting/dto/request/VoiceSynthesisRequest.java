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
public class VoiceSynthesisRequest {
	
	@NotBlank(message = "is required")
	private String messageId;
	
	@NotBlank(message = "is required")
	private String roomId;
	
	@Builder.Default
	private String voice = "FEMALE";
}
