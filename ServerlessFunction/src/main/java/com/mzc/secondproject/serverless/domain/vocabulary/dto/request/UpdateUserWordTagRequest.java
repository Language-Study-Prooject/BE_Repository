package com.mzc.secondproject.serverless.domain.vocabulary.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserWordTagRequest {
	private Boolean bookmarked;
	private Boolean favorite;
	private String difficulty;
}
