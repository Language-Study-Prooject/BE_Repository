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

	public ChatRoomQueryService() {
		this.roomRepository = new ChatRoomRepository();
		this.userRepository = new UserRepository();
	}
	
	public Optional<ChatRoom> getRoom(String roomId) {
		return roomRepository.findById(roomId);
	}
	
	public PaginatedResult<ChatRoom> getRooms(String level, int limit, String cursor, String type, String gameType, String status) {
		PaginatedResult<ChatRoom> roomPage;
		if (level != null && !level.isEmpty()) {
			roomPage = roomRepository.findByLevelWithPagination(level, limit, cursor);
		} else {
			roomPage = roomRepository.findAllWithPagination(limit, cursor);
		}

		List<ChatRoom> rooms = roomPage.items();

		if (type != null) {
			rooms = rooms.stream().filter(r -> type.equalsIgnoreCase(r.getType())).toList();
		}
		if (gameType != null) {
			rooms = rooms.stream().filter(r -> gameType.equalsIgnoreCase(r.getGameType())).toList();
		}
		if (status != null) {
			rooms = rooms.stream().filter(r -> status.equalsIgnoreCase(r.getStatus())).toList();
		}

		return new PaginatedResult<>(rooms, roomPage.nextCursor());
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
