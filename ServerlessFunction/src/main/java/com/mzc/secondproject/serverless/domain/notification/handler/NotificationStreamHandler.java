package com.mzc.secondproject.serverless.domain.notification.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * SSE(Server-Sent Events) 알림 스트리밍 Lambda Handler
 * Lambda Function URL with Response Streaming을 사용하여 실시간 알림 제공
 *
 * 클라이언트 연결 예시:
 * const eventSource = new EventSource('https://{function-url}/?userId={userId}');
 * eventSource.onmessage = (event) => console.log(JSON.parse(event.data));
 */
public class NotificationStreamHandler implements RequestStreamHandler {

	private static final Logger logger = LoggerFactory.getLogger(NotificationStreamHandler.class);
	private static final String QUEUE_URL = EnvConfig.get("NOTIFICATION_QUEUE_URL");
	private static final int POLL_INTERVAL_MS = 1000;
	private static final int MAX_STREAM_DURATION_MS = 840000; // 14분 (Lambda 15분 제한 고려)

	private final SqsClient sqsClient;

	public NotificationStreamHandler() {
		this.sqsClient = AwsClients.sqs();
	}

	public NotificationStreamHandler(SqsClient sqsClient) {
		this.sqsClient = sqsClient;
	}

	@Override
	public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
		Map<String, Object> event = parseEvent(input);
		String userId = extractUserId(event);

		if (userId == null || userId.isBlank()) {
			sendErrorResponse(output, 400, "userId query parameter is required");
			return;
		}

		logger.info("SSE connection started for userId: {}", userId);

		try (BufferedOutputStream bufferedOutput = new BufferedOutputStream(output)) {
			writeSSEHeaders(bufferedOutput);
			sendHeartbeat(bufferedOutput);

			long startTime = System.currentTimeMillis();

			while (!isTimeoutReached(startTime)) {
				List<Message> messages = pollMessages(userId);

				for (Message message : messages) {
					if (isMessageForUser(message, userId)) {
						sendSSEEvent(bufferedOutput, message.body());
						deleteMessage(message);
					}
				}

				if (messages.isEmpty()) {
					sendHeartbeat(bufferedOutput);
				}

				sleep(POLL_INTERVAL_MS);
			}

			sendSSEEvent(bufferedOutput, "{\"type\":\"STREAM_END\",\"message\":\"Connection timeout\"}");
			logger.info("SSE connection ended for userId: {} (timeout)", userId);

		} catch (Exception e) {
			logger.error("SSE stream error for userId: {}", userId, e);
		}
	}

	private Map<String, Object> parseEvent(InputStream input) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
			return JsonUtil.fromJson(sb.toString(), Map.class);
		}
	}

	@SuppressWarnings("unchecked")
	private String extractUserId(Map<String, Object> event) {
		Object queryParams = event.get("queryStringParameters");
		if (queryParams instanceof Map) {
			Object userId = ((Map<String, Object>) queryParams).get("userId");
			return userId != null ? userId.toString() : null;
		}
		return null;
	}

	private void writeSSEHeaders(OutputStream output) throws IOException {
		String headers = "HTTP/1.1 200 OK\r\n" +
				"Content-Type: text/event-stream\r\n" +
				"Cache-Control: no-cache\r\n" +
				"Connection: keep-alive\r\n" +
				"Access-Control-Allow-Origin: *\r\n" +
				"\r\n";
		output.write(headers.getBytes(StandardCharsets.UTF_8));
		output.flush();
	}

	private void sendSSEEvent(OutputStream output, String data) throws IOException {
		String event = "data: " + data + "\n\n";
		output.write(event.getBytes(StandardCharsets.UTF_8));
		output.flush();
	}

	private void sendHeartbeat(OutputStream output) throws IOException {
		sendSSEEvent(output, "{\"type\":\"HEARTBEAT\",\"timestamp\":" + System.currentTimeMillis() + "}");
	}

	private void sendErrorResponse(OutputStream output, int statusCode, String message) throws IOException {
		String response = JsonUtil.toJson(Map.of(
				"statusCode", statusCode,
				"body", JsonUtil.toJson(Map.of("error", message))
		));
		output.write(response.getBytes(StandardCharsets.UTF_8));
		output.flush();
	}

	private List<Message> pollMessages(String userId) {
		try {
			ReceiveMessageRequest request = ReceiveMessageRequest.builder()
					.queueUrl(QUEUE_URL)
					.maxNumberOfMessages(10)
					.waitTimeSeconds(1)
					.messageAttributeNames("userId", "type")
					.build();

			return sqsClient.receiveMessage(request).messages();
		} catch (Exception e) {
			logger.warn("Failed to poll messages: {}", e.getMessage());
			return List.of();
		}
	}

	private boolean isMessageForUser(Message message, String targetUserId) {
		try {
			Map<String, Object> body = JsonUtil.fromJson(message.body(), Map.class);
			String messageUserId = (String) body.get("userId");
			return targetUserId.equals(messageUserId);
		} catch (Exception e) {
			return false;
		}
	}

	private void deleteMessage(Message message) {
		try {
			sqsClient.deleteMessage(DeleteMessageRequest.builder()
					.queueUrl(QUEUE_URL)
					.receiptHandle(message.receiptHandle())
					.build());
		} catch (Exception e) {
			logger.warn("Failed to delete message: {}", e.getMessage());
		}
	}

	private boolean isTimeoutReached(long startTime) {
		return (System.currentTimeMillis() - startTime) > MAX_STREAM_DURATION_MS;
	}

	private void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
