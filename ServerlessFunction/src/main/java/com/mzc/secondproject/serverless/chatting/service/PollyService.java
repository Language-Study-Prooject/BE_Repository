package com.mzc.secondproject.serverless.chatting.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.VoiceId;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;

public class PollyService {

    private static final Logger logger = LoggerFactory.getLogger(PollyService.class);

    private final PollyClient pollyClient;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;

    public PollyService() {
        this.pollyClient = PollyClient.builder().build();
        this.s3Client = S3Client.builder().build();
        this.s3Presigner = S3Presigner.builder().build();
        this.bucketName = System.getenv("CHAT_BUCKET_NAME");
    }

    /**
     * 메시지 ID 기반으로 음성 합성 (캐시 지원)
     * S3에 파일이 있으면 바로 URL 반환, 없으면 Polly 변환 후 저장
     */
    public VoiceSynthesisResult synthesizeSpeechForMessage(String messageId, String text, String voice) {
        String s3Key = generateS3Key(messageId, voice);

        // 캐시 확인: S3에 이미 존재하는지 체크
        if (existsInS3(s3Key)) {
            logger.info("Cache hit: {}", s3Key);
            String presignedUrl = getPresignedUrl(s3Key);
            return new VoiceSynthesisResult(s3Key, presignedUrl, true);
        }

        // 캐시 미스: Polly 변환 후 S3 저장
        logger.info("Cache miss: synthesizing and saving to {}", s3Key);
        synthesizeAndSave(text, voice, s3Key);
        String presignedUrl = getPresignedUrl(s3Key);
        return new VoiceSynthesisResult(s3Key, presignedUrl, false);
    }

    /**
     * S3 키로 Pre-signed URL 생성
     */
    public String getPresignedUrl(String s3Key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * S3에 파일 존재 여부 확인
     */
    public boolean existsInS3(String s3Key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /**
     * Polly로 음성 변환 후 지정된 S3 키로 저장
     */
    private void synthesizeAndSave(String text, String voice, String s3Key) {
        VoiceId voiceId = resolveVoiceId(voice);

        try {
            SynthesizeSpeechRequest request = SynthesizeSpeechRequest.builder()
                    .text(text)
                    .voiceId(voiceId)
                    .engine("neural")
                    .outputFormat(OutputFormat.MP3)
                    .build();

            InputStream audioStream = pollyClient.synthesizeSpeech(request);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int bytesRead;
            while ((bytesRead = audioStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            byte[] audioBytes = buffer.toByteArray();

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Key)
                            .contentType("audio/mpeg")
                            .build(),
                    RequestBody.fromBytes(audioBytes)
            );

            logger.info("Saved audio to S3: {}", s3Key);
        } catch (Exception e) {
            logger.error("Error synthesizing speech", e);
            throw new RuntimeException("Failed to synthesize speech", e);
        }
    }

    /**
     * 메시지 ID와 음성 타입으로 S3 키 생성
     */
    public String generateS3Key(String messageId, String voice) {
        String voiceSuffix = "MALE".equalsIgnoreCase(voice) ? "male" : "female";
        return "voice/" + messageId + "_" + voiceSuffix + ".mp3";
    }

    /**
     * 음성 합성 결과
     */
    public static class VoiceSynthesisResult {
        private final String s3Key;
        private final String audioUrl;
        private final boolean cached;

        public VoiceSynthesisResult(String s3Key, String audioUrl, boolean cached) {
            this.s3Key = s3Key;
            this.audioUrl = audioUrl;
            this.cached = cached;
        }

        public String getS3Key() { return s3Key; }
        public String getAudioUrl() { return audioUrl; }
        public boolean isCached() { return cached; }
    }

    private VoiceId resolveVoiceId(String voice) {
        if ("MALE".equalsIgnoreCase(voice)) {
            return VoiceId.MATTHEW;  // 미국 영어 남성 (Neural 지원)
        }
        return VoiceId.JOANNA;  // 미국 영어 여성 (Neural 지원, 기본값)
    }
}
