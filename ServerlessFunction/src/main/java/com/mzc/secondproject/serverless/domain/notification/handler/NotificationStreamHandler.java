package com.mzc.secondproject.serverless.domain.notification.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.util.JsonUtil;
import com.mzc.secondproject.serverless.domain.notification.config.NotificationConfig;
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

		logger.info("SSE connection started: userId={}, requestId={}", userId, context.getAwsRequestId());

		try (BufferedOutputStream bufferedOutput = new BufferedOutputStream(output)) {
			streamNotifications(bufferedOutput, userId);
		} catch (Exception e) {
			logger.error("SSE stream error: userId={}", userId, e);
		}
	}

	private void streamNotifications(BufferedOutputStream output, String userId) throws IOException {
		writeSSEHeaders(output);
		sendHeartbeat(output);

		long startTime = System.currentTimeMillis();

		while (!isTimeoutReached(startTime)) {
			List<Message> messages = pollMessages();

			for (Message message : messages) {
				if (isMessageForUser(message, userId)) {
					sendSSEEvent(output, message.body());
					deleteMessage(message);
				}
			}

			if (messages.isEmpty()) {
				sendHeartbeat(output);
			}

			sleep();
		}

		sendStreamEndEvent(output);
		logger.info("SSE connection ended: userId={} (timeout)", userId);
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
		String heartbeat = JsonUtil.toJson(Map.of(
				"type", NotificationConfig.EVENT_HEARTBEAT,
				"timestamp", System.currentTimeMillis()
		));
		sendSSEEvent(output, heartbeat);
	}

	private void sendStreamEndEvent(OutputStream output) throws IOException {
		String endEvent = JsonUtil.toJson(Map.of(
				"type", NotificationConfig.EVENT_STREAM_END,
				"message", "Connection timeout"
		));
		sendSSEEvent(output, endEvent);
	}

	private void sendErrorResponse(OutputStream output, int statusCode, String message) throws IOException {
		String response = JsonUtil.toJson(Map.of(
				"statusCode", statusCode,
				"body", JsonUtil.toJson(Map.of("error", message))
		));
		output.write(response.getBytes(StandardCharsets.UTF_8));
		output.flush();
	}

	private List<Message> pollMessages() {
		if (!NotificationConfig.isQueueConfigured()) {
			return List.of();
		}

		try {
			ReceiveMessageRequest request = ReceiveMessageRequest.builder()
					.queueUrl(NotificationConfig.queueUrl())
					.maxNumberOfMessages(NotificationConfig.SSE_MAX_MESSAGES_PER_POLL)
					.waitTimeSeconds(NotificationConfig.SSE_WAIT_TIME_SECONDS)
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
					.queueUrl(NotificationConfig.queueUrl())
					.receiptHandle(message.receiptHandle())
					.build());
		} catch (Exception e) {
			logger.warn("Failed to delete message: {}", e.getMessage());
		}
	}

	private boolean isTimeoutReached(long startTime) {
		return (System.currentTimeMillis() - startTime) > NotificationConfig.SSE_MAX_DURATION_MS;
	}

	private void sleep() {
		try {
			Thread.sleep(NotificationConfig.SSE_POLL_INTERVAL_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
