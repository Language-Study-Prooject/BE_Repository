package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.domain.chatting.dto.response.CommandResult;
import com.mzc.secondproject.serverless.domain.chatting.enums.MessageType;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.model.Poll;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.PollRepository;
import com.mzc.secondproject.serverless.domain.user.model.User;
import com.mzc.secondproject.serverless.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 슬래시 명령어 처리 서비스
 */
public class CommandService {

	private static final Logger logger = LoggerFactory.getLogger(CommandService.class);

	private final ConnectionRepository connectionRepository;
	private final PollRepository pollRepository;
	private final UserRepository userRepository;
	private final Random random;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public CommandService() {
		this(new ConnectionRepository(), new PollRepository(), new UserRepository());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public CommandService(ConnectionRepository connectionRepository,
	                      PollRepository pollRepository,
	                      UserRepository userRepository) {
		this.connectionRepository = connectionRepository;
		this.pollRepository = pollRepository;
		this.userRepository = userRepository;
		this.random = new Random();
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
		String args = parts.length > 1 ? parts[1] : "";

		logger.info("Processing command: {} from user: {} in room: {}", command, userId, roomId);

		return switch (command) {
			// 기본 명령어
			case "/help" -> Optional.of(handleHelpCommand());
			case "/member", "/members" -> Optional.of(handleMembersCommand(roomId));
			case "/leave" -> Optional.of(handleLeaveCommand(roomId, userId));
			case "/clear" -> Optional.of(handleClearCommand(roomId, userId));

			// 재미 명령어
			case "/dice" -> Optional.of(handleDiceCommand(roomId, userId));
			case "/coin" -> Optional.of(handleCoinCommand(roomId, userId));
			case "/random" -> Optional.of(handleRandomCommand(roomId, userId, args));

			// 투표 명령어
			case "/poll" -> Optional.of(handlePollCommand(roomId, userId, args));
			case "/vote" -> Optional.of(handleVoteCommand(roomId, userId, args));
			case "/endpoll" -> Optional.of(handleEndPollCommand(roomId, userId));

			default -> Optional.empty();
		};
	}

	// ========== 기본 명령어 ==========

	/**
	 * /help - 도움말
	 */
	private CommandResult handleHelpCommand() {
		String helpMessage = """
				📖 사용 가능한 명령어:

				[기본]
				  /members - 현재 접속자 목록
				  /leave - 채팅방 나가기
				  /clear - 내 채팅 내역 삭제

				[재미]
				  /dice - 주사위 굴리기 (1-6)
				  /coin - 동전 던지기
				  /random [옵션1] [옵션2] ... - 랜덤 선택

				[투표]
				  /poll [질문] | [옵션1] | [옵션2] | ... - 투표 생성
				  /vote [번호] - 투표하기
				  /endpoll - 투표 종료 (생성자만)
				""";
		return CommandResult.success(MessageType.SYSTEM_COMMAND, helpMessage);
	}

	/**
	 * /members - 접속자 목록
	 */
	private CommandResult handleMembersCommand(String roomId) {
		List<Connection> connections = connectionRepository.findByRoomId(roomId);

		if (connections.isEmpty()) {
			return CommandResult.success(MessageType.SYSTEM_COMMAND, "현재 접속자가 없습니다.");
		}

		// 닉네임 조회
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("👥 현재 접속자: %d명\n", connections.size()));

		for (Connection conn : connections) {
			String nickname = userRepository.findByCognitoSub(conn.getUserId())
					.map(User::getNickname)
					.orElse(conn.getUserId());
			sb.append(String.format("  • %s\n", nickname));
		}

		Map<String, Object> data = new HashMap<>();
		data.put("count", connections.size());
		data.put("members", connections.stream()
				.map(c -> {
					Map<String, String> member = new HashMap<>();
					member.put("userId", c.getUserId());
					member.put("nickname", userRepository.findByCognitoSub(c.getUserId())
							.map(User::getNickname).orElse(c.getUserId()));
					return member;
				})
				.collect(Collectors.toList()));

		return CommandResult.success(MessageType.SYSTEM_COMMAND, sb.toString(), data);
	}

	/**
	 * /leave - 채팅방 나가기
	 */
	private CommandResult handleLeaveCommand(String roomId, String userId) {
		String nickname = userRepository.findByCognitoSub(userId)
				.map(User::getNickname)
				.orElse(userId);

		Map<String, Object> data = new HashMap<>();
		data.put("userId", userId);
		data.put("nickname", nickname);
		data.put("action", "leave");

		return CommandResult.success(MessageType.LEAVE_ROOM,
				String.format("👋 %s님이 퇴장합니다.", nickname), data);
	}

	/**
	 * /clear - 내 채팅 내역 삭제
	 */
	private CommandResult handleClearCommand(String roomId, String userId) {
		Map<String, Object> data = new HashMap<>();
		data.put("userId", userId);
		data.put("action", "clear");

		return CommandResult.success(MessageType.CLEAR_CHAT,
				"🗑️ 채팅 내역 삭제를 요청했습니다.", data);
	}

	// ========== 재미 명령어 ==========

	/**
	 * /dice - 주사위 굴리기
	 */
	private CommandResult handleDiceCommand(String roomId, String userId) {
		int result = random.nextInt(6) + 1;

		String nickname = userRepository.findByCognitoSub(userId)
				.map(User::getNickname)
				.orElse(userId);

		String emoji = switch (result) {
			case 1 -> "⚀";
			case 2 -> "⚁";
			case 3 -> "⚂";
			case 4 -> "⚃";
			case 5 -> "⚄";
			case 6 -> "⚅";
			default -> "🎲";
		};

		Map<String, Object> data = new HashMap<>();
		data.put("userId", userId);
		data.put("nickname", nickname);
		data.put("result", result);
		data.put("type", "dice");

		return CommandResult.success(MessageType.SYSTEM_COMMAND,
				String.format("🎲 %s님이 주사위를 굴렸습니다: %s %d", nickname, emoji, result), data);
	}

	/**
	 * /coin - 동전 던지기
	 */
	private CommandResult handleCoinCommand(String roomId, String userId) {
		boolean isHeads = random.nextBoolean();
		String result = isHeads ? "앞면 (Heads)" : "뒷면 (Tails)";
		String emoji = isHeads ? "🪙" : "💿";

		String nickname = userRepository.findByCognitoSub(userId)
				.map(User::getNickname)
				.orElse(userId);

		Map<String, Object> data = new HashMap<>();
		data.put("userId", userId);
		data.put("nickname", nickname);
		data.put("result", isHeads ? "heads" : "tails");
		data.put("type", "coin");

		return CommandResult.success(MessageType.SYSTEM_COMMAND,
				String.format("%s %s님이 동전을 던졌습니다: %s", emoji, nickname, result), data);
	}

	/**
	 * /random [옵션1] [옵션2] ... - 랜덤 선택
	 */
	private CommandResult handleRandomCommand(String roomId, String userId, String args) {
		if (args.isBlank()) {
			return CommandResult.error("사용법: /random [옵션1] [옵션2] [옵션3] ...");
		}

		String[] options = args.split("\\s+");
		if (options.length < 2) {
			return CommandResult.error("최소 2개 이상의 옵션이 필요합니다.");
		}

		String selected = options[random.nextInt(options.length)];

		String nickname = userRepository.findByCognitoSub(userId)
				.map(User::getNickname)
				.orElse(userId);

		Map<String, Object> data = new HashMap<>();
		data.put("userId", userId);
		data.put("nickname", nickname);
		data.put("options", Arrays.asList(options));
		data.put("selected", selected);
		data.put("type", "random");

		return CommandResult.success(MessageType.SYSTEM_COMMAND,
				String.format("🎯 %s님의 랜덤 선택: %s\n(후보: %s)",
						nickname, selected, String.join(", ", options)), data);
	}

	// ========== 투표 명령어 ==========

	/**
	 * /poll [질문] | [옵션1] | [옵션2] | ... - 투표 생성
	 */
	private CommandResult handlePollCommand(String roomId, String userId, String args) {
		// 이미 진행 중인 투표가 있는지 확인
		Optional<Poll> activePoll = pollRepository.findActiveByRoomId(roomId);
		if (activePoll.isPresent()) {
			return CommandResult.error("이미 진행 중인 투표가 있습니다. /endpoll로 종료 후 새 투표를 만드세요.");
		}

		if (args.isBlank()) {
			return CommandResult.error("사용법: /poll [질문] | [옵션1] | [옵션2] | ...");
		}

		String[] parts = args.split("\\|");
		if (parts.length < 3) {
			return CommandResult.error("질문과 최소 2개의 옵션이 필요합니다. (구분자: |)");
		}

		String question = parts[0].trim();
		List<String> options = new ArrayList<>();
		for (int i = 1; i < parts.length; i++) {
			String option = parts[i].trim();
			if (!option.isEmpty()) {
				options.add(option);
			}
		}

		if (options.size() < 2) {
			return CommandResult.error("최소 2개의 옵션이 필요합니다.");
		}

		if (options.size() > 10) {
			return CommandResult.error("옵션은 최대 10개까지 가능합니다.");
		}

		// 투표 생성
		String pollId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		long ttl = Instant.now().plusSeconds(24 * 60 * 60).getEpochSecond(); // 24시간

		Map<String, Integer> votes = new HashMap<>();
		for (int i = 0; i < options.size(); i++) {
			votes.put(String.valueOf(i), 0);
		}

		Poll poll = Poll.builder()
				.pk("ROOM#" + roomId)
				.sk("POLL#" + pollId)
				.pollId(pollId)
				.roomId(roomId)
				.question(question)
				.options(options)
				.votes(votes)
				.userVotes(new HashMap<>())
				.createdBy(userId)
				.createdAt(now)
				.isActive(true)
				.ttl(ttl)
				.build();

		pollRepository.save(poll);

		String nickname = userRepository.findByCognitoSub(userId)
				.map(User::getNickname)
				.orElse(userId);

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("📊 %s님이 투표를 시작했습니다!\n\n", nickname));
		sb.append(String.format("❓ %s\n\n", question));
		for (int i = 0; i < options.size(); i++) {
			sb.append(String.format("  %d. %s\n", i + 1, options.get(i)));
		}
		sb.append("\n💬 /vote [번호]로 투표하세요!");

		Map<String, Object> data = new HashMap<>();
		data.put("pollId", pollId);
		data.put("question", question);
		data.put("options", options);
		data.put("createdBy", userId);
		data.put("creatorNickname", nickname);

		logger.info("Poll created: pollId={}, roomId={}, question={}", pollId, roomId, question);

		return CommandResult.success(MessageType.POLL_CREATE, sb.toString(), data);
	}

	/**
	 * /vote [번호] - 투표하기
	 */
	private CommandResult handleVoteCommand(String roomId, String userId, String args) {
		Optional<Poll> optPoll = pollRepository.findActiveByRoomId(roomId);
		if (optPoll.isEmpty()) {
			return CommandResult.error("진행 중인 투표가 없습니다.");
		}

		Poll poll = optPoll.get();

		if (poll.hasVoted(userId)) {
			return CommandResult.error("이미 투표하셨습니다.");
		}

		int optionIndex;
		try {
			optionIndex = Integer.parseInt(args.trim()) - 1; // 1-based to 0-based
		} catch (NumberFormatException e) {
			return CommandResult.error("사용법: /vote [번호] (예: /vote 1)");
		}

		if (optionIndex < 0 || optionIndex >= poll.getOptions().size()) {
			return CommandResult.error(String.format("1~%d 사이의 번호를 입력하세요.", poll.getOptions().size()));
		}

		// 투표 추가
		poll.addVote(userId, optionIndex);
		pollRepository.save(poll);

		String nickname = userRepository.findByCognitoSub(userId)
				.map(User::getNickname)
				.orElse(userId);

		String selectedOption = poll.getOptions().get(optionIndex);

		// 현재 투표 현황 생성
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("✅ %s님이 '%s'에 투표했습니다!\n\n", nickname, selectedOption));
		sb.append(String.format("📊 현재 현황 (총 %d표):\n", poll.getTotalVotes()));
		for (int i = 0; i < poll.getOptions().size(); i++) {
			int voteCount = poll.getVotes().getOrDefault(String.valueOf(i), 0);
			String bar = "█".repeat(Math.min(voteCount, 10));
			sb.append(String.format("  %d. %s: %s %d표\n",
					i + 1, poll.getOptions().get(i), bar, voteCount));
		}

		Map<String, Object> data = new HashMap<>();
		data.put("pollId", poll.getPollId());
		data.put("voterId", userId);
		data.put("voterNickname", nickname);
		data.put("selectedOption", optionIndex);
		data.put("selectedOptionText", selectedOption);
		data.put("votes", poll.getVotes());
		data.put("totalVotes", poll.getTotalVotes());

		logger.info("Vote recorded: pollId={}, userId={}, option={}", poll.getPollId(), userId, optionIndex);

		return CommandResult.success(MessageType.POLL_VOTE, sb.toString(), data);
	}

	/**
	 * /endpoll - 투표 종료
	 */
	private CommandResult handleEndPollCommand(String roomId, String userId) {
		Optional<Poll> optPoll = pollRepository.findActiveByRoomId(roomId);
		if (optPoll.isEmpty()) {
			return CommandResult.error("진행 중인 투표가 없습니다.");
		}

		Poll poll = optPoll.get();

		if (!poll.getCreatedBy().equals(userId)) {
			return CommandResult.error("투표 생성자만 종료할 수 있습니다.");
		}

		poll.setIsActive(false);
		pollRepository.save(poll);

		// 최종 결과 계산
		int maxVotes = 0;
		List<String> winners = new ArrayList<>();
		for (int i = 0; i < poll.getOptions().size(); i++) {
			int voteCount = poll.getVotes().getOrDefault(String.valueOf(i), 0);
			if (voteCount > maxVotes) {
				maxVotes = voteCount;
				winners.clear();
				winners.add(poll.getOptions().get(i));
			} else if (voteCount == maxVotes && voteCount > 0) {
				winners.add(poll.getOptions().get(i));
			}
		}

		String nickname = userRepository.findByCognitoSub(userId)
				.map(User::getNickname)
				.orElse(userId);

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("🏁 %s님이 투표를 종료했습니다!\n\n", nickname));
		sb.append(String.format("❓ %s\n\n", poll.getQuestion()));
		sb.append(String.format("📊 최종 결과 (총 %d표):\n", poll.getTotalVotes()));

		for (int i = 0; i < poll.getOptions().size(); i++) {
			int voteCount = poll.getVotes().getOrDefault(String.valueOf(i), 0);
			String bar = "█".repeat(Math.min(voteCount, 10));
			String medal = (voteCount == maxVotes && maxVotes > 0) ? "🏆 " : "   ";
			sb.append(String.format("%s%d. %s: %s %d표\n",
					medal, i + 1, poll.getOptions().get(i), bar, voteCount));
		}

		if (!winners.isEmpty()) {
			sb.append(String.format("\n🎉 우승: %s", String.join(", ", winners)));
		} else {
			sb.append("\n투표가 없습니다.");
		}

		Map<String, Object> data = new HashMap<>();
		data.put("pollId", poll.getPollId());
		data.put("question", poll.getQuestion());
		data.put("options", poll.getOptions());
		data.put("votes", poll.getVotes());
		data.put("totalVotes", poll.getTotalVotes());
		data.put("winners", winners);

		logger.info("Poll ended: pollId={}, totalVotes={}", poll.getPollId(), poll.getTotalVotes());

		return CommandResult.success(MessageType.POLL_END, sb.toString(), data);
	}
}
