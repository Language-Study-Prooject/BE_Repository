package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.domain.chatting.dto.response.CommandResult;
import com.mzc.secondproject.serverless.domain.chatting.enums.MessageType;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.model.GameSession;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.GameSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * 슬래시 명령어 처리 서비스
 */
public class CommandService {
	
	private static final Logger logger = LoggerFactory.getLogger(CommandService.class);

	private final ConnectionRepository connectionRepository;
	private final GameSessionRepository gameSessionRepository;
	private final GameService gameService;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public CommandService() {
		this(new ConnectionRepository(), new GameSessionRepository(), new GameService());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public CommandService(ConnectionRepository connectionRepository,
	                      GameSessionRepository gameSessionRepository,
	                      GameService gameService) {
		this.connectionRepository = connectionRepository;
		this.gameSessionRepository = gameSessionRepository;
		this.gameService = gameService;
	}
	
	/**
	 * 명령어 처리
	 *
	 * @param content 메시지 내용
	 * @param roomId  채팅방 ID
	 * @param userId  사용자 ID
	 * @return 명령어 처리 결과 (명령어가 아닌 경우 Optional.empty())
	 */
	public Optional<CommandResult> processCommand(String content, String roomId, String userId) {
		if (content == null || !content.startsWith("/")) {
			return Optional.empty();
		}
		
		String[] parts = content.trim().split("\\s+", 2);
		String command = parts[0].toLowerCase();
		
		logger.info("Processing command: {} from user: {} in room: {}", command, userId, roomId);
		
		return switch (command) {
			case "/member", "/members" -> Optional.of(handleMemberCommand(roomId));
			case "/start" -> Optional.of(handleStartCommand(roomId, userId));
			case "/stop" -> Optional.of(handleStopCommand(roomId, userId));
			case "/score" -> Optional.of(handleScoreCommand(roomId));
			case "/skip" -> Optional.of(handleSkipCommand(roomId, userId));
			case "/hint" -> Optional.of(handleHintCommand(roomId, userId));
			case "/help" -> Optional.of(handleHelpCommand());
			default -> Optional.empty();
		};
	}
	
	/**
	 * /member - 현재 접속자 수 조회
	 */
	private CommandResult handleMemberCommand(String roomId) {
		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		
		if (connections.isEmpty()) {
			return CommandResult.success(MessageType.SYSTEM_COMMAND, "현재 접속자가 없습니다.");
		}
		
		String message = String.format("현재 접속자: %d명", connections.size());
		return CommandResult.success(MessageType.SYSTEM_COMMAND, message, connections.size());
	}
	
	/**
	 * /start - 게임 시작
	 */
	private CommandResult handleStartCommand(String roomId, String userId) {
		GameService.GameStartResult result = gameService.startGame(roomId, userId);

		if (!result.success()) {
			return CommandResult.error(result.error());
		}

		String message = String.format("""
						🎮 게임 시작!
						총 %d 라운드

						라운드 1 시작!
						출제자: %s
						""",
				result.session().getTotalRounds(),
				result.session().getCurrentDrawerId());

		return CommandResult.success(MessageType.GAME_START, message, result);
	}
	
	/**
	 * /stop - 게임 중단
	 */
	private CommandResult handleStopCommand(String roomId, String userId) {
		return gameService.stopGame(roomId, userId);
	}
	
	/**
	 * /score - 현재 점수 조회
	 */
	private CommandResult handleScoreCommand(String roomId) {
		Optional<GameSession> optSession = gameSessionRepository.findActiveByRoomId(roomId);
		if (optSession.isEmpty()) {
			return CommandResult.error("진행 중인 게임이 없습니다.");
		}

		GameSession session = optSession.get();

		if (session.getScores() == null || session.getScores().isEmpty()) {
			return CommandResult.success(MessageType.SCORE_UPDATE, "아직 점수가 없습니다.");
		}

		StringBuilder sb = new StringBuilder("📊 현재 점수:\n");
		session.getScores().entrySet().stream()
				.sorted((a, b) -> b.getValue().compareTo(a.getValue()))
				.forEach(entry -> sb.append(String.format("  %s: %d점\n", entry.getKey(), entry.getValue())));

		return CommandResult.success(MessageType.SCORE_UPDATE, sb.toString(), session.getScores());
	}
	
	/**
	 * /skip - 라운드 스킵 (출제자만)
	 */
	private CommandResult handleSkipCommand(String roomId, String userId) {
		return gameService.skipRound(roomId, userId);
	}
	
	/**
	 * /hint - 힌트 제공 (출제자만)
	 */
	private CommandResult handleHintCommand(String roomId, String userId) {
		return gameService.provideHint(roomId, userId);
	}
	
	/**
	 * /help - 도움말
	 */
	private CommandResult handleHelpCommand() {
		String helpMessage = """
				📖 사용 가능한 명령어:
				  /member - 현재 접속자 수
				  /start - 게임 시작 (2명 이상)
				  /stop - 게임 중단
				  /score - 현재 점수 보기
				  /skip - 라운드 스킵 (출제자)
				  /hint - 힌트 보기 (출제자)
				  /help - 도움말
				""";
		return CommandResult.success(MessageType.SYSTEM_COMMAND, helpMessage);
	}
}
