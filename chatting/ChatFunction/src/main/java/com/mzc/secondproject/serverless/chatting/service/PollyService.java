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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

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

    public String synthesizeSpeech(String text) {
        return synthesizeSpeech(text, "FEMALE");
    }

    public String synthesizeSpeech(String text, String voice) {
        VoiceId voiceId = resolveVoiceId(voice);
        logger.info("Synthesizing speech with voice: {}", voiceId);

        try {
            SynthesizeSpeechRequest request = SynthesizeSpeechRequest.builder()
                    .text(text)
                    .voiceId(voiceId)
                    .engine("neural")
                    .outputFormat(OutputFormat.MP3)
                    .build();

            InputStream audioStream = pollyClient.synthesizeSpeech(request);

            // InputStream을 byte[]로 변환
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int bytesRead;
            while ((bytesRead = audioStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            byte[] audioBytes = buffer.toByteArray();

            // S3에 저장
            String audioKey = "voice/" + UUID.randomUUID() + ".mp3";

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(audioKey)
                            .contentType("audio/mpeg")
                            .build(),
                    RequestBody.fromBytes(audioBytes)
            );

            // Pre-signed URL 생성 (1시간 유효)
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(audioKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(1))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            String presignedUrl = presignedRequest.url().toString();

            logger.info("Generated pre-signed URL for audio: {}", audioKey);
            return presignedUrl;

        } catch (Exception e) {
            logger.error("Error synthesizing speech", e);
            throw new RuntimeException("Failed to synthesize speech", e);
        }
    }

    private VoiceId resolveVoiceId(String voice) {
        if ("MALE".equalsIgnoreCase(voice)) {
            return VoiceId.MATTHEW;  // 미국 영어 남성 (Neural 지원)
        }
        return VoiceId.JOANNA;  // 미국 영어 여성 (Neural 지원, 기본값)
    }
}
