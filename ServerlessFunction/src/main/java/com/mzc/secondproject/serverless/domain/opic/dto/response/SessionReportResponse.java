package com.mzc.secondproject.serverless.domain.opic.dto.response;

import java.util.List;

/**
 * 세션 종합 리포트 응답 DTO
 */
public record SessionReportResponse(
        String estimatedLevel,          // 예상 레벨 (IM1, IM2 등)
        int overallScore,               // 종합 점수 (0-100)
        List<String> strengths,         // 잘한 점
        List<String> weaknesses,        // 개선할 점
        String feedback,                // 종합 피드백
        List<String> recommendations    // 학습 추천
) {}