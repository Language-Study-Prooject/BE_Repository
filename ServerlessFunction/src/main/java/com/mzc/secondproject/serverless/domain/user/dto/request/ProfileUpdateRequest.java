package com.mzc.secondproject.serverless.domain.user.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequest {
	
	private String nickname;
	private String level;
	private String profileUrl;
}
