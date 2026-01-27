package com.mzc.secondproject.serverless.domain.opic.service;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.opic.dto.response.SessionReportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ses.model.*;

/**
* AWS SES를 이용한 이메일 발송 서비스
*/
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private static final String SENDER_EMAIL = System.getenv("SES_SENDER_EMAIL");
    private static final String APP_NAME = "English Study";

    /**
     * OPIc 세션 리포트 이메일 발송
     */
    public void sendOPIcReportEmail(String recipientEmail, String userName, SessionReportResponse report) {
        logger.info("OPIc 리포트 이메일 발송: to={}", recipientEmail);

        String subject = String.format("[%s] OPIc 스피킹 테스트 결과 - %s 등급",
                APP_NAME, report.estimatedLevel());

        String htmlBody = buildOPIcReportHtml(userName, report);
        String textBody = buildOPIcReportText(userName, report);

        sendEmail(recipientEmail, subject, htmlBody, textBody);
    }

    /**
     * 이메일 발송 (HTML + Text)
     */
    private void sendEmail(String to, String subject, String htmlBody, String textBody) {
        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .source(SENDER_EMAIL)
                    .destination(Destination.builder()
                            .toAddresses(to)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .charset("UTF-8")
                                    .data(subject)
                                    .build())
                            .body(Body.builder()
                                    .html(Content.builder()
                                            .charset("UTF-8")
                                            .data(htmlBody)
                                            .build())
                                    .text(Content.builder()
                                            .charset("UTF-8")
                                            .data(textBody)
                                            .build())
                                    .build())
                            .build())
                    .build();

            SendEmailResponse response = AwsClients.ses().sendEmail(request);
            logger.info("이메일 발송 성공: messageId={}", response.messageId());

        } catch (SesException e) {
            logger.error("이메일 발송 실패: {}", e.getMessage(), e);
            // 이메일 실패해도 세션 완료는 진행되도록 예외를 던지지 않음
        }
    }

    /**
     * OPIc 리포트 HTML 템플릿
     */
    private String buildOPIcReportHtml(String userName, SessionReportResponse report) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html><head><meta charset='UTF-8'></head>");
        html.append("<body style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif; ");
        html.append("max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f5f5f5;'>");

        // Header
        html.append("<div style='background: linear-gradient(135deg, #f59e0b 0%, #fbbf24 100%); ");
        html.append("padding: 30px; border-radius: 16px 16px 0 0; text-align: center;'>");
        html.append("<h1 style='color: white; margin: 0; font-size: 24px;'>🎯 OPIc 스피킹 테스트 결과</h1>");
        html.append("</div>");

        // Main Content
        html.append("<div style='background: white; padding: 30px; border-radius: 0 0 16px 16px;'>");

        // Greeting
        html.append("<p style='font-size: 16px; color: #333;'>안녕하세요, <strong>")
                .append(userName != null ? userName : "학습자")
                .append("</strong>님!</p>");
        html.append("<p style='color: #666;'>OPIc 스피킹 테스트 결과를 알려드립니다.</p>");

        // Score Cards
        html.append("<div style='display: flex; gap: 16px; margin: 24px 0;'>");

        // Estimated Level
        html.append("<div style='flex: 1; background: ")
                .append(getLevelColor(report.estimatedLevel()))
                .append("; padding: 20px; border-radius: 12px; text-align: center;'>");
        html.append("<p style='margin: 0; color: white; font-size: 14px;'>예상 등급</p>");
        html.append("<p style='margin: 8px 0 0 0; color: white; font-size: 36px; font-weight: 800;'>")
                .append(report.estimatedLevel()).append("</p>");
        html.append("</div>");

        // Overall Score
        html.append("<div style='flex: 1; background: ")
                .append(getScoreColor(report.overallScore()))
                .append("; padding: 20px; border-radius: 12px; text-align: center;'>");
        html.append("<p style='margin: 0; color: white; font-size: 14px;'>종합 점수</p>");
        html.append("<p style='margin: 8px 0 0 0; color: white; font-size: 36px; font-weight: 800;'>")
                .append(report.overallScore()).append("</p>");
        html.append("</div>");

        html.append("</div>");

        // Feedback
        html.append("<div style='background: #f0fdf4; padding: 20px; border-radius: 12px; margin-bottom: 20px;'>");
        html.append("<h3 style='color: #059669; margin: 0 0 12px 0;'>📝 종합 피드백</h3>");
        html.append("<p style='color: #333; line-height: 1.8; margin: 0;'>")
                .append(report.feedback()).append("</p>");
        html.append("</div>");

        // Strengths
        html.append("<div style='background: #eff6ff; padding: 20px; border-radius: 12px; margin-bottom: 20px;'>");
        html.append("<h3 style='color: #3b82f6; margin: 0 0 12px 0;'>💪 잘한 점</h3>");
        html.append("<ul style='margin: 0; padding-left: 20px; color: #333;'>");
        for (String strength : report.strengths()) {
            html.append("<li style='margin-bottom: 8px;'>").append(strength).append("</li>");
        }
        html.append("</ul>");
        html.append("</div>");

        // Weaknesses
        html.append("<div style='background: #fff7ed; padding: 20px; border-radius: 12px; margin-bottom: 20px;'>");
        html.append("<h3 style='color: #f97316; margin: 0 0 12px 0;'>📈 개선할 점</h3>");
        html.append("<ul style='margin: 0; padding-left: 20px; color: #333;'>");
        for (String weakness : report.weaknesses()) {
            html.append("<li style='margin-bottom: 8px;'>").append(weakness).append("</li>");
        }
        html.append("</ul>");
        html.append("</div>");

        // Recommendations
        html.append("<div style='background: #f5f3ff; padding: 20px; border-radius: 12px; margin-bottom: 20px;'>");
        html.append("<h3 style='color: #8b5cf6; margin: 0 0 12px 0;'>💡 학습 추천</h3>");
        html.append("<ol style='margin: 0; padding-left: 20px; color: #333;'>");
        for (String rec : report.recommendations()) {
            html.append("<li style='margin-bottom: 8px;'>").append(rec).append("</li>");
        }
        html.append("</ol>");
        html.append("</div>");

        // CTA Button
        html.append("<div style='text-align: center; margin-top: 30px;'>");
        html.append("<a href='https://your-app-url.com/reports' ");
        html.append("style='display: inline-block; background: linear-gradient(135deg, #8b5cf6 0%, #a78bfa 100%); ");
        html.append("color: white; padding: 14px 32px; border-radius: 12px; text-decoration: none; font-weight: 600;'>");
        html.append("전체 리포트 보기</a>");
        html.append("</div>");

        // Footer
        html.append("<hr style='border: none; border-top: 1px solid #eee; margin: 30px 0;'>");
        html.append("<p style='color: #999; font-size: 12px; text-align: center;'>");
        html.append("본 이메일은 English Study 서비스에서 자동으로 발송되었습니다.<br>");
        html.append("© 2025 English Study. All rights reserved.</p>");

        html.append("</div>");
        html.append("</body></html>");

        return html.toString();
    }

    /**
     * OPIc 리포트 텍스트 버전 (HTML 미지원 이메일 클라이언트용)
     */
    private String buildOPIcReportText(String userName, SessionReportResponse report) {
        StringBuilder text = new StringBuilder();

        text.append("OPIc 스피킹 테스트 결과\n");
        text.append("================================\n\n");

        text.append("안녕하세요, ").append(userName != null ? userName : "학습자").append("님!\n");
        text.append("OPIc 스피킹 테스트 결과를 알려드립니다.\n\n");

        text.append("결과 요약\n");
        text.append("------------\n");
        text.append("예상 등급: ").append(report.estimatedLevel()).append("\n");
        text.append("종합 점수: ").append(report.overallScore()).append("점\n\n");

        text.append("종합 피드백\n");
        text.append("------------\n");
        text.append(report.feedback()).append("\n\n");

        text.append("잘한 점\n");
        text.append("------------\n");
        for (String strength : report.strengths()) {
            text.append("• ").append(strength).append("\n");
        }
        text.append("\n");

        text.append("개선할 점\n");
        text.append("------------\n");
        for (String weakness : report.weaknesses()) {
            text.append("• ").append(weakness).append("\n");
        }
        text.append("\n");

        text.append("학습 추천\n");
        text.append("------------\n");
        int i = 1;
        for (String rec : report.recommendations()) {
            text.append(i++).append(". ").append(rec).append("\n");
        }
        text.append("\n");

        text.append("================================\n");
        text.append("© 2025 English Study\n");

        return text.toString();
    }

    /**
     * 레벨별 색상 반환
     */
    private String getLevelColor(String level) {
        return switch (level) {
            case "NL", "NM", "NH" -> "#6b7280";
            case "IL" -> "#22c55e";
            case "IM1" -> "#10b981";
            case "IM2" -> "#3b82f6";
            case "IM3" -> "#8b5cf6";
            case "IH" -> "#f97316";
            case "AL" -> "#ef4444";
            default -> "#3b82f6";
        };
    }

    /**
     * 점수별 색상 반환
     */
    private String getScoreColor(int score) {
        if (score >= 90) return "#059669";
        if (score >= 70) return "#3b82f6";
        if (score >= 50) return "#f97316";
        return "#ef4444";
    }
}











































