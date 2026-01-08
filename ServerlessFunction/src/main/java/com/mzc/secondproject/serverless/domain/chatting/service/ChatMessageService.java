package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatMessage;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class ChatMessageService {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageService.class);

    private final ChatMessageRepository repository;

    public ChatMessageService() {
        this.repository = new ChatMessageRepository();
    }

    public ChatMessage saveMessage(ChatMessage message) {
        logger.info("Saving message: {}", message.getMessageId());
        return repository.save(message);
    }

    public Optional<ChatMessage> getMessage(String roomId, String messageId) {
        logger.info("Getting message: {} from room: {}", messageId, roomId);
        return repository.findByRoomIdAndMessageId(roomId, messageId);
    }

    public PaginatedResult<ChatMessage> getMessagesByRoomWithPagination(String roomId, int limit, String cursor) {
        logger.info("Getting messages for room: {} with limit: {}", roomId, limit);
        return repository.findByRoomIdWithPagination(roomId, limit, cursor);
    }

    public PaginatedResult<ChatMessage> getMessagesByUserWithPagination(String userId, int limit, String cursor) {
        logger.info("Getting messages for user: {} with limit: {}", userId, limit);
        return repository.findByUserIdWithPagination(userId, limit, cursor);
    }
}
