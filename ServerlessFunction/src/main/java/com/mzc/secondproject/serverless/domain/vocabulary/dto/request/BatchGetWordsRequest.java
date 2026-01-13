package com.mzc.secondproject.serverless.domain.vocabulary.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchGetWordsRequest {
	
	@NotEmpty(message = "is required")
	private List<String> wordIds;
}
