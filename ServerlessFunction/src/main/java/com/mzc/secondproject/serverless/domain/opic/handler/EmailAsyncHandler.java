package com.mzc.secondproject.serverless.domain.opic.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.google.gson.Gson;
import com.mzc.secondproject.serverless.domain.opic.service.EmailService;

public class EmailAsyncHandler implements RequestHandler<SNSEvent, Void> {
    private final EmailService emailService = new EmailService();
    private final Gson gson = new Gson();

    @Override
    public Void handleRequest(SNSEvent event, Context context) {
        for (SNSEvent.SNSRecord record : event.getRecords()) {
            String messageBody = record.getSNS().getMessage();
            processMessage(messageBody);
        }
        return null;
    }

    private void processMessage(String body) {
        // 메시지 파싱 및 타입 확인 (OPIC_REPORT_EMAIL)
        // emailService.sendOPIcReportEmail 호출
    }
}