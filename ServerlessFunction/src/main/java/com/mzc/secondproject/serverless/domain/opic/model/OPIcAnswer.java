package com.mzc.secondproject.serverless.domain.opic.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

/**
 * OPIc 답변 + 피드백
 */
public class OPIcAnswer {

    private String pk;                    // SESSION#sessionId
    private String sk;                    // Q#01 (질문 순서)

    private String sessionId;
    private String questionId;
    private int questionIndex;            // 질문 순서 (0, 1, 2)

    // 질문 정보 (비정규화)
    private String questionText;

    // 사용자 답변
    private String audioS3Key;            // 답변 음성 S3 키
    private String transcript;            // 음성 → 텍스트
    private double transcriptConfidence;  // 변환 신뢰도
    private int durationSeconds;          // 답변 길이 (초)

    // AI 피드백
    private String score;                 // 예상 등급 (IM1, IM2, IM3, IH, AL)
    private String grammarFeedback;       // 문법 피드백 (JSON)
    private String vocabularyFeedback;    // 어휘 피드백 (JSON)
    private String contentFeedback;       // 내용 피드백
    private String pronunciationFeedback; // 발음 피드백
    private String strengths;             // 잘한 점 (JSON array)
    private String improvements;          // 개선점 (JSON array)

    // 모범 답변
    private String sampleAnswer;          // 모범 답변 텍스트
    private String sampleAudioS3Key;      // 모범 답변 음성 S3 키

    // 메타데이터
    private AnswerStatus status;
    private int attemptCount;             // 시도 횟수
    private Instant createdAt;
    private Instant completedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    @DynamoDbAttribute("SK")
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    @DynamoDbAttribute("sessionId")
    public String getSessionId() { return sessionId; }
    public void setSessionId(String id) { this.sessionId = id; }

    @DynamoDbAttribute("questionId")
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String id) { this.questionId = id; }

    @DynamoDbAttribute("questionIndex")
    public int getQuestionIndex() { return questionIndex; }
    public void setQuestionIndex(int idx) { this.questionIndex = idx; }

    @DynamoDbAttribute("questionText")
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String text) { this.questionText = text; }

    @DynamoDbAttribute("audioS3Key")
    public String getAudioS3Key() { return audioS3Key; }
    public void setAudioS3Key(String key) { this.audioS3Key = key; }

    @DynamoDbAttribute("transcript")
    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }

    @DynamoDbAttribute("transcriptConfidence")
    public double getTranscriptConfidence() { return transcriptConfidence; }
    public void setTranscriptConfidence(double conf) { this.transcriptConfidence = conf; }

    @DynamoDbAttribute("durationSeconds")
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int duration) { this.durationSeconds = duration; }

    @DynamoDbAttribute("score")
    public String getScore() { return score; }
    public void setScore(String score) { this.score = score; }

    @DynamoDbAttribute("grammarFeedback")
    public String getGrammarFeedback() { return grammarFeedback; }
    public void setGrammarFeedback(String feedback) { this.grammarFeedback = feedback; }

    @DynamoDbAttribute("vocabularyFeedback")
    public String getVocabularyFeedback() { return vocabularyFeedback; }
    public void setVocabularyFeedback(String feedback) { this.vocabularyFeedback = feedback; }

    @DynamoDbAttribute("contentFeedback")
    public String getContentFeedback() { return contentFeedback; }
    public void setContentFeedback(String feedback) { this.contentFeedback = feedback; }

    @DynamoDbAttribute("pronunciationFeedback")
    public String getPronunciationFeedback() { return pronunciationFeedback; }
    public void setPronunciationFeedback(String feedback) { this.pronunciationFeedback = feedback; }

    @DynamoDbAttribute("strengths")
    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }

    @DynamoDbAttribute("improvements")
    public String getImprovements() { return improvements; }
    public void setImprovements(String improvements) { this.improvements = improvements; }

    @DynamoDbAttribute("sampleAnswer")
    public String getSampleAnswer() { return sampleAnswer; }
    public void setSampleAnswer(String answer) { this.sampleAnswer = answer; }

    @DynamoDbAttribute("sampleAudioS3Key")
    public String getSampleAudioS3Key() { return sampleAudioS3Key; }
    public void setSampleAudioS3Key(String key) { this.sampleAudioS3Key = key; }

    @DynamoDbAttribute("status")
    public AnswerStatus getStatus() { return status; }
    public void setStatus(AnswerStatus status) { this.status = status; }

    @DynamoDbAttribute("attemptCount")
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int count) { this.attemptCount = count; }

    @DynamoDbAttribute("createdAt")
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant time) { this.createdAt = time; }

    @DynamoDbAttribute("completedAt")
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant time) { this.completedAt = time; }

    /**
     * 답변 상태
     */
    public enum AnswerStatus {
        PENDING,      // 음성 업로드 대기
        UPLOADED,     // 음성 업로드 완료
        PROCESSING,   // Transcribe + Bedrock 처리 중
        COMPLETED,    // 피드백 완료
        FAILED        // 처리 실패
    }
}
