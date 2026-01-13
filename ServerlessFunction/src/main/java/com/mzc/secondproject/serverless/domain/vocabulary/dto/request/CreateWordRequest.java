package com.mzc.secondproject.serverless.domain.vocabulary.dto.request;

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
public class CreateWordRequest {
	
	@NotBlank(message = "is required")
	@Size(max = 100, message = "must be at most 100 characters")
	private String english;
	
	@NotBlank(message = "is required")
	@Size(max = 100, message = "must be at most 100 characters")
	private String korean;
	
	@Size(max = 500, message = "must be at most 500 characters")
	private String example;
	
	@Builder.Default
	private String level = "BEGINNER";
	
	@Builder.Default
	private String category = "DAILY";
}
