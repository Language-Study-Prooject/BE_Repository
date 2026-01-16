package com.mzc.secondproject.serverless.domain.user.dto.response;

import com.mzc.secondproject.serverless.domain.user.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
	
	private String userId;
	private String email;
	private String nickname;
	private String level;
	private String profileUrl;
	private String createdAt;
	private String updatedAt;
	
	/**
	 * User 엔티티 → ProfileResponse 변환
	 */
	public static ProfileResponse from(User user) {
		return ProfileResponse.builder()
				.userId(user.getCognitoSub())
				.email(user.getEmail())
				.nickname(user.getNickname())
				.level(user.getLevel())
				.profileUrl(user.getProfileUrl())
				.createdAt(user.getCreatedAt())
				.updatedAt(user.getUpdatedAt())
				.build();
	}
}
