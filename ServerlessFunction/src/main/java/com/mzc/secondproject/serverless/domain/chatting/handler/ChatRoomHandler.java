package com.mzc.secondproject.serverless.domain.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.common.validation.BeanValidator;
import com.mzc.secondproject.serverless.domain.chatting.dto.request.CreateRoomRequest;
import com.mzc.secondproject.serverless.domain.chatting.dto.request.JoinRoomRequest;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.JoinRoomResponse;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.RoomListItem;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.RoomParticipant;
import com.mzc.secondproject.serverless.domain.chatting.exception.ChattingErrorCode;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.service.ChatRoomCommandService;
import com.mzc.secondproject.serverless.domain.chatting.service.ChatRoomQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ChatRoomHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(ChatRoomHandler.class);
	
	private final ChatRoomCommandService commandService;
	private final ChatRoomQueryService queryService;
	private final HandlerRouter router;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public ChatRoomHandler() {
		this(new ChatRoomCommandService(), new ChatRoomQueryService());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public ChatRoomHandler(ChatRoomCommandService commandService, ChatRoomQueryService queryService) {
		this.commandService = commandService;
		this.queryService = queryService;
		this.router = initRouter();
	}
	
	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.postAuth("/rooms", this::createRoom),
				Route.getAuth("/rooms", this::getRooms),
				Route.getAuth("/rooms/{roomId}", this::getRoom),
				Route.postAuth("/rooms/{roomId}/join", this::joinRoom),
				Route.postAuth("/rooms/{roomId}/leave", this::leaveRoom),
				Route.deleteAuth("/rooms/{roomId}", this::deleteRoom)
		);
	}
	
	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}
	
	private APIGatewayProxyResponseEvent createRoom(APIGatewayProxyRequestEvent request, String userId) {
		CreateRoomRequest req = ResponseGenerator.gson().fromJson(request.getBody(), CreateRoomRequest.class);

		return BeanValidator.validateAndExecute(req, dto -> {
			String level = dto.getLevel() != null ? dto.getLevel() : "beginner";
			Integer maxMembers = dto.getMaxMembers() != null ? dto.getMaxMembers() : 6;
			Boolean isPrivate = dto.getIsPrivate() != null ? dto.getIsPrivate() : false;

			ChatRoom room = commandService.createRoom(
					dto.getName(), dto.getDescription(), level, maxMembers, isPrivate, dto.getPassword(), userId,
					dto.getType(), dto.getGameType(), dto.getGameSettings());

			// hostNickname 포함하여 응답
			String hostNickname = queryService.getHostNickname(room);
			RoomListItem roomItem = RoomListItem.from(room, hostNickname);

			return ResponseGenerator.created("Room created", roomItem);
		});
	}
	
	private APIGatewayProxyResponseEvent getRooms(APIGatewayProxyRequestEvent request, String userId) {
		Map<String, String> queryParams = request.getQueryStringParameters();

		String level = queryParams != null ? queryParams.get("level") : null;
		String joined = queryParams != null ? queryParams.get("joined") : null;
		String cursor = queryParams != null ? queryParams.get("cursor") : null;
		String type = queryParams != null ? queryParams.get("type") : null;
		String gameType = queryParams != null ? queryParams.get("gameType") : null;
		String status = queryParams != null ? queryParams.get("status") : null;

		int limit = 10;
		if (queryParams != null && queryParams.get("limit") != null) {
			limit = Math.min(Integer.parseInt(queryParams.get("limit")), 20);
		}

		PaginatedResult<ChatRoom> roomPage = queryService.getRooms(level, limit, cursor, type, gameType, status);
		List<ChatRoom> rooms = roomPage.items();

		if ("true".equals(joined)) {
			rooms = queryService.filterByJoinedUser(rooms, userId);
		}

		// hostNickname 포함하여 RoomListItem으로 변환
		List<RoomListItem> roomItems = rooms.stream()
				.map(room -> {
					String hostNickname = queryService.getHostNickname(room);
					return RoomListItem.from(room, hostNickname);
				})
				.toList();

		Map<String, Object> result = new HashMap<>();
		result.put("rooms", roomItems);
		result.put("nextCursor", roomPage.nextCursor());
		result.put("hasMore", roomPage.hasMore());

		return ResponseGenerator.ok("Rooms retrieved", result);
	}
	
	private APIGatewayProxyResponseEvent getRoom(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		Optional<ChatRoom> optRoom = queryService.getRoom(roomId);
		if (optRoom.isEmpty()) {
			return ResponseGenerator.fail(ChattingErrorCode.ROOM_NOT_FOUND);
		}

		ChatRoom room = optRoom.get();
		room.setPassword(null);

		// 참가자 정보와 방장 닉네임 추가
		List<RoomParticipant> participants = queryService.getParticipantsWithNicknames(room);
		String hostNickname = queryService.getHostNickname(room);

		Map<String, Object> result = new HashMap<>();
		result.put("room", room);
		result.put("participants", participants);
		result.put("hostNickname", hostNickname);

		return ResponseGenerator.ok("Room retrieved", result);
	}
	
	private APIGatewayProxyResponseEvent joinRoom(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");
		JoinRoomRequest req = ResponseGenerator.gson().fromJson(request.getBody(), JoinRoomRequest.class);
		
		String password = req != null ? req.getPassword() : null;
		JoinRoomResponse response = commandService.joinRoom(roomId, userId, password);
		response.room().setPassword(null);
		return ResponseGenerator.ok("Joined room", response);
	}
	
	private APIGatewayProxyResponseEvent leaveRoom(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");
		
		ChatRoomCommandService.LeaveResult result = commandService.leaveRoom(roomId, userId);
		if (result.deleted()) {
			return ResponseGenerator.ok("Room deleted", null);
		}
		result.room().setPassword(null);
		return ResponseGenerator.ok("Left room", result.room());
	}
	
	private APIGatewayProxyResponseEvent deleteRoom(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");
		
		commandService.deleteRoom(roomId, userId);
		return ResponseGenerator.ok("Room deleted", null);
	}
}
