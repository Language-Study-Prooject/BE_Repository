package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.RoomParticipant;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import com.mzc.secondproject.serverless.domain.user.model.User;
import com.mzc.secondproject.serverless.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * ChatRoom 조회 전용 서비스 (CQRS Query)
 */
public class ChatRoomQueryService {
	
	private static final Logger logger = LoggerFactory.getLogger(ChatRoomQueryService.class);
	
	private final ChatRoomRepository roomRepository;
	private final UserRepository userRepository;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public ChatRoomQueryService() {
		this(new ChatRoomRepository(), new UserRepository());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public ChatRoomQueryService(ChatRoomRepository roomRepository, UserRepository userRepository) {
		this.roomRepository = roomRepository;
		this.userRepository = userRepository;
	}
	
	public Optional<ChatRoom> getRoom(String roomId) {
		return roomRepository.findById(roomId);
	}
	
	/**
	 * 필터 조건으로 방 목록 조회 (DB 레벨 필터링)
	 * GSI1SK 포맷: {type}#{gameType}#{status}#{level}#{createdAt}
	 *
	 * @param level    레벨 필터 (beginner, intermediate, advanced)
	 * @param limit    조회 개수
	 * @param cursor   페이지네이션 커서
	 * @param type     방 타입 (CHAT, GAME)
	 * @param gameType 게임 타입 (CATCHMIND 등)
	 * @param status   방 상태 (WAITING, PLAYING, FINISHED)
	 * @return 필터링된 방 목록
	 */
	public PaginatedResult<ChatRoom> getRooms(String level, int limit, String cursor, String type, String gameType, String status) {
		// DB 레벨에서 필터링 (메모리 필터링 제거)
		return roomRepository.findByFilters(type, gameType, status, level, limit, cursor);
	}
	
	public List<ChatRoom> filterByJoinedUser(List<ChatRoom> rooms, String userId) {
		return rooms.stream()
				.filter(room -> room.getMemberIds() != null && room.getMemberIds().contains(userId))
				.toList();
	}
	
	/**
	 * 참가자 목록을 닉네임과 함께 조회
	 *
	 * @param room ChatRoom 객체
	 * @return 참가자 목록 (userId, nickname, isHost 포함)
	 */
	public List<RoomParticipant> getParticipantsWithNicknames(ChatRoom room) {
		if (room.getMemberIds() == null) return List.of();
		
		String hostId = room.getHostId() != null ? room.getHostId() : room.getCreatedBy();
		
		return room.getMemberIds().stream()
				.map(userId -> {
					String nickname = userRepository.findByCognitoSub(userId)
							.map(User::getNickname)
							.orElse(userId);  // fallback to userId if not found
					return RoomParticipant.builder()
							.userId(userId)
							.nickname(nickname)
							.isHost(userId.equals(hostId))
							.build();
				})
				.toList();
	}
	
	/**
	 * 방장 닉네임 조회
	 *
	 * @param room ChatRoom 객체
	 * @return 방장 닉네임 (없으면 userId 반환)
	 */
	public String getHostNickname(ChatRoom room) {
		String hostId = room.getHostId() != null ? room.getHostId() : room.getCreatedBy();
		return userRepository.findByCognitoSub(hostId)
				.map(User::getNickname)
				.orElse(hostId);
	}
}
