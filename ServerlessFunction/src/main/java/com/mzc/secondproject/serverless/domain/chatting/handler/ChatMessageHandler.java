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
import com.mzc.secondproject.serverless.domain.chatting.dto.request.SendMessageRequest;
import com.mzc.secondproject.serverless.domain.chatting.exception.ChattingErrorCode;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatMessage;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import com.mzc.secondproject.serverless.domain.chatting.service.ChatMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ChatMessageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(ChatMessageHandler.class);
	
	private final ChatMessageService chatMessageService;
	private final ChatRoomRepository chatRoomRepository;
	private final HandlerRouter router;
	
	public ChatMessageHandler() {
		this.chatMessageService = new ChatMessageService();
		this.chatRoomRepository = new ChatRoomRepository();
		this.router = initRouter();
	}
	
	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.postAuth("/rooms/{roomId}/messages", this::sendMessage),
				Route.getAuth("/rooms/{roomId}/messages/{messageId}", this::getMessage),
				Route.getAuth("/rooms/{roomId}/messages", this::getMessages)
		);
	}
	
	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}
	
	private APIGatewayProxyResponseEvent sendMessage(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");
		SendMessageRequest req = ResponseGenerator.gson().fromJson(request.getBody(), SendMessageRequest.class);
		
		return BeanValidator.validateAndExecute(req, dto -> {
			String messageType = dto.getMessageType() != null ? dto.getMessageType() : "TEXT";
			String messageId = UUID.randomUUID().toString();
			String now = Instant.now().toString();
			
			ChatMessage message = ChatMessage.builder()
					.pk("ROOM#" + roomId)
					.sk("MSG#" + now + "#" + messageId)
					.gsi1pk("USER#" + userId)
					.gsi1sk("MSG#" + now)
					.gsi2pk("MSG#" + messageId)
					.gsi2sk("ROOM#" + roomId)
					.messageId(messageId)
					.roomId(roomId)
					.userId(userId)
					.content(dto.getContent())
					.messageType(messageType)
					.createdAt(now)
					.build();
			
			ChatMessage savedMessage = chatMessageService.saveMessage(message);
			chatRoomRepository.updateLastMessageAt(roomId, now);
			
			logger.info("Message sent: {} in room: {}", messageId, roomId);
			return ResponseGenerator.created("Message sent", savedMessage);
		});
	}
	
	private APIGatewayProxyResponseEvent getMessage(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");
		String messageId = request.getPathParameters().get("messageId");
		
		Optional<ChatMessage> message = chatMessageService.getMessage(roomId, messageId);
		if (message.isEmpty()) {
			return ResponseGenerator.fail(ChattingErrorCode.MESSAGE_NOT_FOUND);
		}
		return ResponseGenerator.ok("Message retrieved", message.get());
	}
	
	private APIGatewayProxyResponseEvent getMessages(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");
		Map<String, String> queryParams = request.getQueryStringParameters();
		
		int limit = 20;
		String cursor = null;
		
		if (queryParams != null) {
			if (queryParams.get("limit") != null) {
				limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
			}
			cursor = queryParams.get("cursor");
		}
		
		PaginatedResult<ChatMessage> messagePage = chatMessageService.getMessagesByRoomWithPagination(roomId, limit, cursor);
		
		Map<String, Object> result = new HashMap<>();
		result.put("messages", messagePage.items());
		result.put("nextCursor", messagePage.nextCursor());
		result.put("hasMore", messagePage.hasMore());
		
		return ResponseGenerator.ok("Messages retrieved", result);
	}
}
