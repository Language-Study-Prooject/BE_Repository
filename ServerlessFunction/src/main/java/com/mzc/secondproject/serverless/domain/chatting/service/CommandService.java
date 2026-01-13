package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.domain.chatting.dto.response.CommandResult;
import com.mzc.secondproject.serverless.domain.chatting.enums.MessageType;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 슬래시 명령어 처리 서비스
 */
public class CommandService {

	private static final Logger logger = LoggerFactory.getLogger(CommandService.class);

	private final ConnectionRepository connectionRepository;
	private final ChatRoomRepository chatRoomRepository;

	public CommandService() {
		this.connectionRepository = new ConnectionRepository();
		this.chatRoomRepository = new ChatRoomRepository();
	}

	/**
	 * 명령어 처리
	 * @param content 메시지 내용
	 * @param roomId 채팅방 ID
	 * @param userId 사용자 ID
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
	 * /member - 현재 접속자 목록 조회
	 */
	private CommandResult handleMemberCommand(String roomId) {
		List<Connection> connections = connectionRepository.findByRoomId(roomId);

		if (connections.isEmpty()) {
			return CommandResult.success(MessageType.SYSTEM_COMMAND, "현재 접속자가 없습니다.");
		}

		String memberList = connections.stream()
				.map(Connection::getUserId)
				.collect(Collectors.joining(", "));

		String message = String.format("현재 접속자 (%d명): %s", connections.size(), memberList);
		return CommandResult.success(MessageType.SYSTEM_COMMAND, message, connections.size());
	}

	/**
	 * /start - 게임 시작
	 */
	private CommandResult handleStartCommand(String roomId, String userId) {
		List<Connection> connections = connectionRepository.findByRoomId(roomId);

		if (connections.size() < 2) {
			return CommandResult.error("최소 2명 이상 접속해야 게임을 시작할 수 있습니다. (현재: " + connections.size() + "명)");
		}

		Optional<ChatRoom> optRoom = chatRoomRepository.findById(roomId);
		if (optRoom.isEmpty()) {
			return CommandResult.error("채팅방을 찾을 수 없습니다.");
		}

		ChatRoom room = optRoom.get();

		// 이미 게임 중인지 확인
		if (room.getGameStatus() != null && !"NONE".equals(room.getGameStatus()) && !"FINISHED".equals(room.getGameStatus())) {
			return CommandResult.error("이미 게임이 진행 중입니다.");
		}

		// TODO: GameService.startGame() 호출 (Story #223에서 구현)
		return CommandResult.success(MessageType.GAME_START, "게임이 곧 시작됩니다! 준비하세요.");
	}

	/**
	 * /stop - 게임 중단
	 */
	private CommandResult handleStopCommand(String roomId, String userId) {
		Optional<ChatRoom> optRoom = chatRoomRepository.findById(roomId);
		if (optRoom.isEmpty()) {
			return CommandResult.error("채팅방을 찾을 수 없습니다.");
		}

		ChatRoom room = optRoom.get();

		// 게임 진행 중인지 확인
		if (room.getGameStatus() == null || "NONE".equals(room.getGameStatus()) || "FINISHED".equals(room.getGameStatus())) {
			return CommandResult.error("진행 중인 게임이 없습니다.");
		}

		// 권한 확인: 게임 시작한 사람 또는 방장
		boolean isOwner = userId.equals(room.getCreatedBy());
		boolean isGameStarter = userId.equals(room.getGameStartedBy());

		if (!isOwner && !isGameStarter) {
			return CommandResult.error("게임을 중단할 권한이 없습니다. (방장 또는 게임 시작자만 가능)");
		}

		// TODO: GameService.stopGame() 호출 (Story #223에서 구현)
		return CommandResult.success(MessageType.GAME_END, "게임이 중단되었습니다.");
	}

	/**
	 * /score - 현재 점수 조회
	 */
	private CommandResult handleScoreCommand(String roomId) {
		Optional<ChatRoom> optRoom = chatRoomRepository.findById(roomId);
		if (optRoom.isEmpty()) {
			return CommandResult.error("채팅방을 찾을 수 없습니다.");
		}

		ChatRoom room = optRoom.get();

		if (room.getGameStatus() == null || "NONE".equals(room.getGameStatus())) {
			return CommandResult.error("진행 중인 게임이 없습니다.");
		}

		// TODO: 점수 포맷팅 (Story #225에서 구현)
		if (room.getScores() == null || room.getScores().isEmpty()) {
			return CommandResult.success(MessageType.SCORE_UPDATE, "아직 점수가 없습니다.");
		}

		StringBuilder sb = new StringBuilder("📊 현재 점수:\n");
		room.getScores().entrySet().stream()
				.sorted((a, b) -> b.getValue().compareTo(a.getValue()))
				.forEach(entry -> sb.append(String.format("  %s: %d점\n", entry.getKey(), entry.getValue())));

		return CommandResult.success(MessageType.SCORE_UPDATE, sb.toString(), room.getScores());
	}

	/**
	 * /skip - 라운드 스킵 (출제자만)
	 */
	private CommandResult handleSkipCommand(String roomId, String userId) {
		Optional<ChatRoom> optRoom = chatRoomRepository.findById(roomId);
		if (optRoom.isEmpty()) {
			return CommandResult.error("채팅방을 찾을 수 없습니다.");
		}

		ChatRoom room = optRoom.get();

		if (!"PLAYING".equals(room.getGameStatus())) {
			return CommandResult.error("게임이 진행 중이 아닙니다.");
		}

		if (!userId.equals(room.getCurrentDrawerId())) {
			return CommandResult.error("출제자만 라운드를 스킵할 수 있습니다.");
		}

		// TODO: GameService.skipRound() 호출 (Story #223에서 구현)
		return CommandResult.success(MessageType.ROUND_END, "라운드가 스킵되었습니다. 정답: " + room.getCurrentWord());
	}

	/**
	 * /hint - 힌트 제공 (출제자만)
	 */
	private CommandResult handleHintCommand(String roomId, String userId) {
		Optional<ChatRoom> optRoom = chatRoomRepository.findById(roomId);
		if (optRoom.isEmpty()) {
			return CommandResult.error("채팅방을 찾을 수 없습니다.");
		}

		ChatRoom room = optRoom.get();

		if (!"PLAYING".equals(room.getGameStatus())) {
			return CommandResult.error("게임이 진행 중이 아닙니다.");
		}

		if (!userId.equals(room.getCurrentDrawerId())) {
			return CommandResult.error("출제자만 힌트를 제공할 수 있습니다.");
		}

		// 힌트 사용 여부 체크
		if (Boolean.TRUE.equals(room.getHintUsed())) {
			return CommandResult.error("이번 라운드에서 이미 힌트를 사용했습니다.");
		}

		String currentWord = room.getCurrentWord();
		if (currentWord == null || currentWord.isEmpty()) {
			return CommandResult.error("제시어가 설정되지 않았습니다.");
		}

		// 첫 글자 힌트
		String hint = currentWord.charAt(0) + "○".repeat(currentWord.length() - 1);

		// TODO: hintUsed 플래그 업데이트 (Story #223에서 구현)
		return CommandResult.success(MessageType.HINT, "💡 힌트: " + hint);
	}

	/**
	 * /help - 도움말
	 */
	private CommandResult handleHelpCommand() {
		String helpMessage = """
				📖 사용 가능한 명령어:
				  /member - 현재 접속자 목록
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
