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
public class CreateWordGroupRequest {
	
	@NotBlank(message = "is required")
	@Size(min = 1, max = 50, message = "must be between 1 and 50 characters")
	private String groupName;
	
	@Size(max = 200, message = "must be at most 200 characters")
	private String description;
}
