package com.mzc.secondproject.serverless.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadResponse {

    private String uploadUrl;   // S3 Presigned URL (클라이언트가 PUT 요청할 URL)
    private String imageUrl;    // 업로드 완료 후 접근 가능한 이미지 URL
}
