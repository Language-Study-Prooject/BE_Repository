package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
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
	
	public ChatRoomQueryService() {
		this.roomRepository = new ChatRoomRepository();
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
}
