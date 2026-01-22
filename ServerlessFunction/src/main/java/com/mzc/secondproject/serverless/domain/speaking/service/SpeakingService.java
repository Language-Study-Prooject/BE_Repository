package com.mzc.secondproject.serverless.domain.speaking.service;

import com.google.gson.*;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.service.PollyService;
import com.mzc.secondproject.serverless.domain.opic.service.TranscribeProxyService;
import com.mzc.secondproject.serverless.domain.speaking.model.SpeakingSession;
import com.mzc.secondproject.serverless.domain.speaking.repository.SpeakingSessionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI와 대화하기 서비스
 * 음성 입력 → STT → Bedrock → TTS → 음성 출력
 */
public class SpeakingService {

    private static final Logger logger = LoggerFactory.getLogger(SpeakingService.class);
    private static final Gson gson = new GsonBuilder().create();

    private static final String MODEL_ID = "anthropic.claude-3-haiku-20240307-v1:0";
    private static final int MAX_TOKENS = 500;
    private static final int MAX_HISTORY_SIZE = 10; // 최근 10턴만 유지

    private final TranscribeProxyService transcribeService;
    private final PollyService pollyService;
    private final SpeakingSessionRepository sessionRepository;

    /**
     * 세션 생성 또는 조회
     */
    public SpeakingSession getOrCreateSession(String sessionId, String userId, String level) {
        if (sessionId != null && !sessionId.isEmpty()) {
            return sessionRepository.findBySessionId(sessionId)
                    .orElseGet(() -> createNewSession(userId, level));
        }
        return createNewSession(userId, level);
    }

    /**
     * 새 세션 생성
     */
    private SpeakingSession createNewSession(String userId, String level) {
        String newSessionId = UUID.randomUUID().toString();
        SpeakingSession session = SpeakingSession.create(newSessionId, userId, level);
        sessionRepository.save(session);
        logger.info("New speaking session created: sessionId={}, userId={}", newSessionId, userId);
        return session;
    }

    public SpeakingService() {
        this.transcribeService = new TranscribeProxyService();
        this.pollyService = new PollyService(
                EnvConfig.getRequired("BUCKET_NAME"),
                "speaking/voice/"
        );
        this.sessionRepository = new SpeakingSessionRepository();
    }

    /**
     * 음성 입력 처리 (전체 플로우)
     */
    public SpeakingResponse processVoiceInput(String sessionId, String userId, String audioBase64, String level) {
        logger.info("Processing voice input for sessionId: {}", sessionId);

        // 세션 조회 또는 생성
        SpeakingSession session = getOrCreateSession(sessionId, userId, level);

        String targetLevel = session.getTargetLevel();

        // STT: 음성 → 텍스트 (Transcribe Proxy 사용)
        logger.info("Step 1: Transcribing audio...");
        TranscribeProxyService.TranscribeResult sttResult = transcribeService.transcribe(
                audioBase64,
                sessionId,
                "en-US"
        );
        String userText = sttResult.transcript();
        logger.info("Transcription complete: {} (confidence: {})", userText, sttResult.confidence());

        // 대화 히스토리 로드
        List<Message> history = parseHistory(session.getConversationHistory());

        // Bedrock: AI 응답 생성
        logger.info("Step 2: Generating AI response...");
        String aiResponse = generateAiResponse(userText, history, targetLevel);
        logger.info("AI response generated: {}", aiResponse);

        // 히스토리 업데이트 (최근 N턴만 유지)
        history.add(new Message("user", userText));
        history.add(new Message("assistant", aiResponse));
        if (history.size() > MAX_HISTORY_SIZE * 2) {
            history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_SIZE * 2, history.size()));
        }
        session.setConversationHistory(toJson(history));
        sessionRepository.update(session);

        // TTS: 텍스트 → 음성 (Polly 사용)
        logger.info("Step 3: Synthesizing speech...");
        String audioId = session.getSessionId() + "_" + System.currentTimeMillis();
        PollyService.VoiceSynthesisResult ttsResult = pollyService.synthesizeSpeech(
                audioId,
                aiResponse,
                "FEMALE"
        );
        logger.info("Speech synthesis complete: cached={}", ttsResult.isCached());

        return new SpeakingResponse(
                session.getSessionId(),
                userText,
                aiResponse,
                ttsResult.getAudioUrl(),
                sttResult.confidence()
        );
    }

    /**
     * 텍스트 입력 처리 (음성 없이 텍스트만)
     */
    public SpeakingResponse processTextInput(String sessionId, String userId, String userText, String level){
        logger.info("Processing text input for sessionId: {}", sessionId);

        // 세션 조회 또는 생성
        SpeakingSession session = getOrCreateSession(sessionId, userId, level);

        // 대화 히스토리 로드
        List<Message> history = parseHistory(session.getConversationHistory());

        // AI 응답 생성
        String aiResponse = generateAiResponse(userText, history, session.getTargetLevel());

        // 히스토리 업데이트
        history.add(new Message("user", userText));
        history.add(new Message("assistant", aiResponse));
        if (history.size() > MAX_HISTORY_SIZE * 2) {
            history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_SIZE * 2, history.size()));
        }
        session.setConversationHistory(toJson(history));
        sessionRepository.update(session);

        // TTS 생성
        String audioId = session.getSessionId() + "_" + System.currentTimeMillis();
        PollyService.VoiceSynthesisResult ttsResult = pollyService.synthesizeSpeech(
                audioId, aiResponse, "FEMALE"
        );

        return new SpeakingResponse(
                session.getSessionId(),
                userText,
                aiResponse,
                ttsResult.getAudioUrl(),
                1.0
        );
    }

    /**
     * 레벨 변경
     */
    public void updateLevel(String sessionId, String level) {
        SpeakingSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("session not found: " + sessionId));

        session.setTargetLevel(level.toUpperCase());
        sessionRepository.update(session);
        logger.info("Level updated for sessionId {}: {}", sessionId, level);
    }

    /**
     * 대화 히스토리 초기화
     */
    public void resetConversation(String sessionId) {
        SpeakingSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("session not found: " + sessionId));

        session.setConversationHistory("[]");
        sessionRepository.update(session);
        logger.info("Conversation reset for sessionId: {}", sessionId);
    }


    /**
     * Bedrock Claude 호출하여 AI 응답 생성
     */
    private String generateAiResponse(String userText, List<Message> history, String targetLevel) {
        String systemPrompt = buildSystemPrompt(targetLevel);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("anthropic_version", "bedrock-2023-05-31");
        requestBody.addProperty("max_tokens", MAX_TOKENS);
        requestBody.addProperty("system", systemPrompt);

        // 메시지 배열 구성
        JsonArray messages = new JsonArray();

        // 기존 히스토리 추가
        for (Message msg : history) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            messages.add(m);
        }

        // 현재 사용자 입력 추가
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userText);
        messages.add(userMsg);

        requestBody.add("messages", messages);

        // Bedrock 호출
        InvokeModelResponse response = AwsClients.bedrock().invokeModel(
                InvokeModelRequest.builder()
                        .modelId(MODEL_ID)
                        .contentType("application/json")
                        .body(SdkBytes.fromUtf8String(requestBody.toString()))
                        .build()
        );

        // 응답 파싱
        JsonObject result = JsonParser.parseString(
                response.body().asUtf8String()
        ).getAsJsonObject();

        return result.getAsJsonArray("content")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    /**
     * 레벨별 시스템 프롬프트 생성
     */
    private String buildSystemPrompt(String targetLevel) {
        String levelGuidance = switch (targetLevel.toUpperCase()) {
            case "BEGINNER" -> """
					- Use simple vocabulary and short sentences
					- Speak slowly and clearly
					- Use basic grammar structures
					- Provide Korean translations for difficult words in parentheses
					""";
            case "ADVANCED" -> """
					- Use sophisticated vocabulary and complex sentences
					- Include idiomatic expressions and phrasal verbs
					- Discuss abstract concepts naturally
					- Challenge the user with nuanced topics
					""";
            default -> """
					- Use moderate vocabulary appropriate for intermediate learners
					- Mix simple and compound sentences
					- Introduce useful expressions gradually
					- Balance challenge with accessibility
					""";
        };

        return String.format("""
				You are a friendly English conversation partner for Korean learners.
				Your name is "Amy" and you're an American English teacher living in Seoul.
				
				## Target Level: %s
				
				## Level-Specific Guidelines:
				%s
				
				## General Guidelines:
				- Keep responses conversational (2-4 sentences)
				- Be warm, encouraging, and supportive
				- If the user makes grammar mistakes, gently correct them naturally in your response
				- Ask follow-up questions to keep the conversation going
				- Respond in English only (except for occasional Korean translations for difficult words)
				- Match the conversation topic to the user's interests
				- Use natural filler words occasionally (well, you know, actually)
				
				## Correction Style:
				Instead of: "You said 'I go to store.' It should be 'I went to the store.'"
				Do this: "Oh, so you went to the store? That's nice! What did you buy?"
				
				Remember: Your goal is to make the user feel comfortable practicing English!
				""", targetLevel, levelGuidance);
    }

    /**
     * 히스토리 JSON 파싱
     */
    private List<Message> parseHistory(String historyJson) {
        List<Message> history = new ArrayList<>();

        if (historyJson == null || historyJson.isEmpty() || historyJson.equals("[]")) {
            return history;
        }

        try {
            JsonArray array = JsonParser.parseString(historyJson).getAsJsonArray();
            for (JsonElement el : array) {
                JsonObject obj = el.getAsJsonObject();
                history.add(new Message(
                        obj.get("role").getAsString(),
                        obj.get("content").getAsString()
                ));
            }
        } catch (Exception e) {
            logger.warn("Failed to parse history, starting fresh: {}", e.getMessage());
        }

        return history;
    }

    /**
     * 히스토리 JSON 변환
     */
    private String toJson(List<Message> history) {
        JsonArray array = new JsonArray();
        for (Message msg : history) {
            JsonObject obj = new JsonObject();
            obj.addProperty("role", msg.role());
            obj.addProperty("content", msg.content());
            array.add(obj);
        }
        return array.toString();
    }

    // ==================== Inner Classes ====================

    private record Message(String role, String content) {}

    /**
     * Speaking 응답 DTO
     */
    public record SpeakingResponse(
            String sessionId,         // 세션 ID (다음 요청에 사용)
            String userTranscript,    // 사용자가 말한 내용 (STT 결과)
            String aiText,            // AI 응답 텍스트
            String aiAudioUrl,        // AI 응답 음성 URL (Polly)
            double confidence         // STT 신뢰도comp
    ) {}
}