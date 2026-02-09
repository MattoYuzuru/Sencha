package com.sencha.sencha.server

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration

class S3Service(config: S3Config) {
    private val presigner = S3Presigner.builder()
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(config.accessKey, config.secretKey)
        ))
        .region(Region.of(config.region))
        .endpointOverride(config.endpoint)
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(config.forcePathStyle)
                .build()
        )
        .build()

    private val bucket = config.bucket
    private val presignDuration = Duration.ofMinutes(config.presignMinutes)

    fun presignUpload(key: String, mime: String): PresignResponse {
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(mime)
            .build()
        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(presignDuration)
            .putObjectRequest(request)
            .build()
        val presigned = presigner.presignPutObject(presignRequest)
        return presigned.toResponse(key)
    }

    fun presignDownload(key: String): PresignResponse {
        val request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(presignDuration)
            .getObjectRequest(request)
            .build()
        val presigned = presigner.presignGetObject(presignRequest)
        return presigned.toResponse(key)
    }
}

private fun software.amazon.awssdk.services.s3.presigner.model.PresignedRequest.toResponse(key: String): PresignResponse {
    val httpRequest = httpRequest()
    return PresignResponse(
        url = httpRequest.url().toString(),
        method = httpRequest.method().name,
        headers = httpRequest.headers().mapValues { it.value.joinToString(",") },
        key = key,
        expiresAtEpochMillis = expiration().toEpochMilli(),
    )
}
