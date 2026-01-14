package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.common.config.RoomTokenConfig;
import com.mzc.secondproject.serverless.domain.chatting.model.RoomToken;
import com.mzc.secondproject.serverless.domain.chatting.repository.RoomTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 채팅방 입장 토큰 서비스
 * REST API에서 토큰 발급, WebSocket 연결 시 토큰 검증
 */
public class RoomTokenService {
	
	private static final Logger logger = LoggerFactory.getLogger(RoomTokenService.class);
	
	private final RoomTokenRepository tokenRepository;
	
	public RoomTokenService() {
		this.tokenRepository = new RoomTokenRepository();
	}
	
	/**
	 * 채팅방 입장 토큰 생성
	 */
	public RoomToken generateToken(String roomId, String userId) {
		String token = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		long ttl = Instant.now().getEpochSecond() + RoomTokenConfig.tokenTtlSeconds();
		
		RoomToken roomToken = RoomToken.builder()
				.pk("TOKEN#" + token)
				.sk("METADATA")
				.token(token)
				.roomId(roomId)
				.userId(userId)
				.createdAt(now)
				.ttl(ttl)
				.build();
		
		tokenRepository.save(roomToken);
		logger.info("Generated room token for user: {} in room: {}", userId, roomId);
		
		return roomToken;
	}
	
	/**
	 * 토큰 검증 및 정보 조회
	 *
	 * @return 유효한 토큰이면 RoomToken, 그렇지 않으면 empty
	 */
	public Optional<RoomToken> validateToken(String token) {
		if (token == null || token.isEmpty()) {
			logger.warn("Token is null or empty");
			return Optional.empty();
		}
		
		Optional<RoomToken> optToken = tokenRepository.findByToken(token);
		
		if (optToken.isEmpty()) {
			logger.warn("Token not found: {}", token);
			return Optional.empty();
		}
		
		RoomToken roomToken = optToken.get();
		
		// TTL 만료 체크 (DynamoDB TTL 삭제 전 유예 기간 대비)
		if (roomToken.getTtl() != null && roomToken.getTtl() < Instant.now().getEpochSecond()) {
			logger.warn("Token expired: {}", token);
			return Optional.empty();
		}
		
		logger.info("Token validated for user: {} in room: {}", roomToken.getUserId(), roomToken.getRoomId());
		return Optional.of(roomToken);
	}
	
	/**
	 * 토큰 삭제 (사용 후 또는 명시적 삭제)
	 */
	public void deleteToken(String token) {
		tokenRepository.delete(token);
		logger.info("Deleted room token: {}", token);
	}
}
