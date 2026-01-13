package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.chatting.exception.ChattingException;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ChatRoomService {
	
	private static final Logger logger = LoggerFactory.getLogger(ChatRoomService.class);
	
	private final ChatRoomRepository roomRepository;
	
	public ChatRoomService() {
		this.roomRepository = new ChatRoomRepository();
	}
	
	public ChatRoom createRoom(String name, String description, String level, Integer maxMembers,
	                           Boolean isPrivate, String password, String createdBy) {
		String roomId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		
		ChatRoom room = ChatRoom.builder()
				.pk("ROOM#" + roomId)
				.sk("METADATA")
				.gsi1pk("ROOMS")
				.gsi1sk(level + "#" + now)
				.roomId(roomId)
				.name(name)
				.description(description)
				.level(level)
				.currentMembers(1)
				.maxMembers(maxMembers)
				.isPrivate(isPrivate)
				.password(isPrivate && password != null ? BCrypt.hashpw(password, BCrypt.gensalt()) : null)
				.createdBy(createdBy)
				.createdAt(now)
				.lastMessageAt(now)
				.memberIds(new ArrayList<>(List.of(createdBy)))
				.build();
		
		roomRepository.save(room);
		logger.info("Created room: {}", roomId);
		
		return room;
	}
	
	public Optional<ChatRoom> getRoom(String roomId) {
		return roomRepository.findById(roomId);
	}
	
	public PaginatedResult<ChatRoom> getRooms(String level, int limit, String cursor) {
		if (level != null && !level.isEmpty()) {
			return roomRepository.findByLevelWithPagination(level, limit, cursor);
		}
		return roomRepository.findAllWithPagination(limit, cursor);
	}
	
	public List<ChatRoom> filterByJoinedUser(List<ChatRoom> rooms, String userId) {
		return rooms.stream()
				.filter(room -> room.getMemberIds() != null && room.getMemberIds().contains(userId))
				.toList();
	}
	
	public ChatRoom joinRoom(String roomId, String userId, String password) {
		Optional<ChatRoom> optRoom = roomRepository.findById(roomId);
		if (optRoom.isEmpty()) {
			throw ChattingException.roomNotFound(roomId);
		}
		
		ChatRoom room = optRoom.get();
		
		if (room.getIsPrivate()) {
			if (password == null || room.getPassword() == null || !BCrypt.checkpw(password, room.getPassword())) {
				throw ChattingException.roomInvalidPassword(roomId);
			}
		}
		
		if (room.getCurrentMembers() >= room.getMaxMembers()) {
			throw ChattingException.roomFull(roomId, room.getMaxMembers());
		}
		
		if (room.getMemberIds() != null && room.getMemberIds().contains(userId)) {
			logger.info("User {} already in room {}", userId, roomId);
			return room;
		}
		
		if (room.getMemberIds() == null) {
			room.setMemberIds(new ArrayList<>());
		}
		room.getMemberIds().add(userId);
		room.setCurrentMembers(room.getCurrentMembers() + 1);
		
		roomRepository.save(room);
		logger.info("User {} joined room {}", userId, roomId);
		
		return room;
	}
	
	public LeaveResult leaveRoom(String roomId, String userId) {
		Optional<ChatRoom> optRoom = roomRepository.findById(roomId);
		if (optRoom.isEmpty()) {
			throw ChattingException.roomNotFound(roomId);
		}
		
		ChatRoom room = optRoom.get();
		
		if (room.getMemberIds() != null) {
			room.getMemberIds().remove(userId);
			room.setCurrentMembers(Math.max(0, room.getCurrentMembers() - 1));
		}
		
		if (userId.equals(room.getCreatedBy()) || room.getCurrentMembers() <= 0) {
			roomRepository.delete(roomId);
			logger.info("Room {} deleted (owner left or empty)", roomId);
			return new LeaveResult(true, null);
		}
		
		roomRepository.save(room);
		logger.info("User {} left room {}", userId, roomId);
		
		return new LeaveResult(false, room);
	}
	
	public void deleteRoom(String roomId, String userId) {
		Optional<ChatRoom> optRoom = roomRepository.findById(roomId);
		if (optRoom.isEmpty()) {
			throw ChattingException.roomNotFound(roomId);
		}
		
		ChatRoom room = optRoom.get();
		if (!userId.equals(room.getCreatedBy())) {
			throw ChattingException.roomNotOwner(userId, roomId);
		}
		
		roomRepository.delete(roomId);
		logger.info("Deleted room: {} by owner: {}", roomId, userId);
	}
	
	public record LeaveResult(boolean deleted, ChatRoom room) {
	}
}
