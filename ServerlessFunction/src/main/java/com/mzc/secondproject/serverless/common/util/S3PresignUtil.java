package com.mzc.secondproject.serverless.common.util;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * S3 Presigned URL 유틸리티
 */
public class S3PresignUtil {

	private static final S3Presigner presigner = AwsClients.s3Presigner();
	private static final String BUCKET_NAME = System.getenv("BUCKET_NAME");
	private static final Duration DEFAULT_DURATION = Duration.ofHours(24);

	// 캐시 (키: S3 key, 값: presigned URL, 만료시간)
	private static final Map<String, CachedUrl> urlCache = new ConcurrentHashMap<>();

	/**
	 * S3 객체에 대한 presigned GET URL 생성 (24시간 유효, 캐시 사용)
	 */
	public static String getPresignedUrl(String key) {
		return getPresignedUrl(key, DEFAULT_DURATION);
	}

	/**
	 * S3 객체에 대한 presigned GET URL 생성 (캐시 사용)
	 */
	public static String getPresignedUrl(String key, Duration duration) {
		// 캐시 확인
		CachedUrl cached = urlCache.get(key);
		if (cached != null && !cached.isExpired()) {
			return cached.url;
		}

		// 새 presigned URL 생성
		GetObjectRequest getObjectRequest = GetObjectRequest.builder()
				.bucket(BUCKET_NAME)
				.key(key)
				.build();

		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
				.signatureDuration(duration)
				.getObjectRequest(getObjectRequest)
				.build();

		PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
		String url = presignedRequest.url().toString();

		// 캐시에 저장 (만료 시간 1시간 전까지 유효)
		long expiresAt = System.currentTimeMillis() + duration.toMillis() - Duration.ofHours(1).toMillis();
		urlCache.put(key, new CachedUrl(url, expiresAt));

		return url;
	}

	/**
	 * 배지 이미지 presigned URL 생성
	 */
	public static String getBadgeImageUrl(String imageFile) {
		return getPresignedUrl("badges/" + imageFile);
	}

	private record CachedUrl(String url, long expiresAt) {
		boolean isExpired() {
			return System.currentTimeMillis() > expiresAt;
		}
	}
}
