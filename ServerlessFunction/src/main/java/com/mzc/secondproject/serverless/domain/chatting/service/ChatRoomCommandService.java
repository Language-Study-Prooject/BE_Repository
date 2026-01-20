package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.domain.chatting.dto.response.JoinRoomResponse;
import com.mzc.secondproject.serverless.domain.chatting.exception.ChattingException;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.model.GameSettings;
import com.mzc.secondproject.serverless.domain.chatting.model.RoomToken;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ChatRoom 변경 전용 서비스 (CQRS Command)
 */
public class ChatRoomCommandService {
	
	private static final Logger logger = LoggerFactory.getLogger(ChatRoomCommandService.class);
	
	private final ChatRoomRepository roomRepository;
	private final RoomTokenService roomTokenService;
	
	public ChatRoomCommandService() {
		this.roomRepository = new ChatRoomRepository();
		this.roomTokenService = new RoomTokenService();
	}
	
	public ChatRoom createRoom(String name, String description, String level, Integer maxMembers,
	                           Boolean isPrivate, String password, String createdBy,
	                           String type, String gameType, GameSettings gameSettings) {
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
				.type(type != null ? type : "CHAT")
				.gameType(gameType)
				.gameSettings(gameSettings)
				.status("WAITING")
				.hostId(createdBy)
				.build();

		roomRepository.save(room);
		logger.info("Created room: {}", roomId);

		return room;
	}
	
	public JoinRoomResponse joinRoom(String roomId, String userId, String password) {
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
		
		boolean alreadyMember = room.getMemberIds() != null && room.getMemberIds().contains(userId);
		if (!alreadyMember) {
			if (room.getMemberIds() == null) {
				room.setMemberIds(new ArrayList<>());
			}
			room.getMemberIds().add(userId);
			room.setCurrentMembers(room.getCurrentMembers() + 1);
			roomRepository.save(room);
			logger.info("User {} joined room {}", userId, roomId);
		} else {
			logger.info("User {} already in room {}", userId, roomId);
		}
		
		// 토큰 발급
		RoomToken token = roomTokenService.generateToken(roomId, userId);
		
		return JoinRoomResponse.of(room, token.getToken(), token.getTtl());
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

		// 모든 참가자가 나갔으면 방 삭제
		if (room.getCurrentMembers() <= 0 ||
			(room.getMemberIds() != null && room.getMemberIds().isEmpty())) {
			roomRepository.delete(roomId);
			logger.info("Room {} deleted (empty)", roomId);
			return new LeaveResult(true, null, null);
		}

		// 방장이 나갔으면 다음 멤버에게 방장 이전
		String oldHostId = room.getHostId() != null ? room.getHostId() : room.getCreatedBy();
		String newHostId = null;

		if (userId.equals(oldHostId)) {
			// 첫 번째 남은 멤버가 새 방장
			if (room.getMemberIds() != null && !room.getMemberIds().isEmpty()) {
				newHostId = room.getMemberIds().get(0);
				room.setHostId(newHostId);
				logger.info("Host transferred from {} to {} in room {}", oldHostId, newHostId, roomId);
			}
		}

		roomRepository.save(room);
		logger.info("User {} left room {}", userId, roomId);

		return new LeaveResult(false, room, newHostId);
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
	
	public record LeaveResult(boolean deleted, ChatRoom room, String newHostId) {
	}
}
