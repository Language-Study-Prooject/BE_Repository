package com.mzc.secondproject.serverless.domain.chatting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSettings {
	@Builder.Default
	private Integer maxRounds = 5;

	@Builder.Default
	private Integer roundTimeLimit = 60;

	@Builder.Default
	private Boolean autoDeleteOnEnd = false;
}
