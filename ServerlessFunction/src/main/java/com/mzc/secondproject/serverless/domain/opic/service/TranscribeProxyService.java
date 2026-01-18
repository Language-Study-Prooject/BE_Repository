package com.mzc.secondproject.serverless.domain.opic.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mzc.secondproject.serverless.domain.opic.exception.OPIcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 개인 AWS 계정의 Transcribe Proxy API 호출 서비스
 * Cross-Account로 Transcribe 기능 사용
 */
public class TranscribeProxyService {

    private static final Logger logger = LoggerFactory.getLogger(TranscribeProxyService.class);
    private static final Gson gson = new Gson();

    private final String proxyUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public TranscribeProxyService() {
        this.proxyUrl = System.getenv("TRANSCRIBE_PROXY_URL");
        this.apiKey = System.getenv("TRANSCRIBE_API_KEY");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (proxyUrl == null || apiKey == null) {
            logger.warn("TRANSCRIBE_PROXY_URL or TRANSCRIBE_API_KEY is not set");
        }
    }

    // 테스트용 생성자
    public TranscribeProxyService(String proxyUrl, String apiKey) {
        this.proxyUrl = proxyUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 음성 파일을 텍스트로 변환
     *
     * @param audioBase64 Base64 인코딩된 음성 데이터
     * @param sessionId   세션 ID (작업 추적용)
     * @return 변환된 텍스트 결과
     */
    public TranscribeResult transcribe(String audioBase64, String sessionId) {
        return transcribe(audioBase64, sessionId, "en-US");
    }

    /**
     * 음성 파일을 텍스트로 변환 (언어 지정)
     *
     * @param audioBase64  Base64 인코딩된 음성 데이터
     * @param sessionId    세션 ID (작업 추적용)
     * @param languageCode 언어 코드 (en-US, ko-KR 등)
     * @return 변환된 텍스트 결과
     */
    public TranscribeResult transcribe(String audioBase64, String sessionId, String languageCode) {
        logger.info("Transcribe 요청 시작 - sessionId: {}, language: {}", sessionId, languageCode);

        try {
            // 요청 바디 생성
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("audio_data", audioBase64);
            requestBody.addProperty("session_id", sessionId);
            requestBody.addProperty("language_code", languageCode);

            // HTTP 요청 생성
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(proxyUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", apiKey)
                    .timeout(Duration.ofSeconds(120))  // Transcribe 처리 시간 고려
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            long startTime = System.currentTimeMillis();

            // API 호출
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Transcribe Proxy 응답 - status: {}, 소요시간: {}ms",
                    response.statusCode(), elapsed);

            // 응답 처리
            if (response.statusCode() != 200) {
                logger.error("Transcribe 실패 - status: {}, body: {}",
                        response.statusCode(), response.body());
                throw new OPIcException.TranscribeException("Transcribe 실패: " + response.statusCode());
            }

            // JSON 파싱
            JsonObject resultJson = JsonParser.parseString(response.body()).getAsJsonObject();

            String transcript = resultJson.get("transcript").getAsString();
            String jobName = resultJson.get("job_name").getAsString();
            double confidence = resultJson.get("confidence").getAsDouble();

            logger.info("Transcribe 완료 - jobName: {}, confidence: {}", jobName, confidence);
            logger.debug("Transcript: {}", transcript);

            return new TranscribeResult(transcript, jobName, confidence);

        } catch (OPIcException.TranscribeException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Transcribe 호출 중 오류 발생", e);
            throw new OPIcException.TranscribeException("음성 변환 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Transcribe 결과 레코드
     */
    public record TranscribeResult(
            String transcript,
            String jobName,
            double confidence
    ) {}

}