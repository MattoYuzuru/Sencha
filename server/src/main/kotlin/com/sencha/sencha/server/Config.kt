package com.sencha.sencha.server

import java.net.URI


data class ServerConfig(
    val port: Int,
    val dbPath: String,
    val sessionTtlHours: Long,
    val s3: S3Config,
    val registrationCode: String?,
    val version: String,
)

data class S3Config(
    val endpoint: URI,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val forcePathStyle: Boolean,
    val presignMinutes: Long,
)

fun loadConfig(): ServerConfig {
    val port = env("SENCHA_PORT")?.toIntOrNull() ?: 8080
    val dbPath = env("SENCHA_DB_PATH") ?: "./sencha.db"
    val sessionTtlHours = env("SENCHA_SESSION_TTL_HOURS")?.toLongOrNull() ?: 720
    val s3Endpoint = env("SENCHA_S3_ENDPOINT") ?: "http://localhost:9000"
    val s3Region = env("SENCHA_S3_REGION") ?: "us-east-1"
    val s3Bucket = env("SENCHA_S3_BUCKET") ?: "sencha"
    val s3AccessKey = env("SENCHA_S3_ACCESS_KEY") ?: ""
    val s3SecretKey = env("SENCHA_S3_SECRET_KEY") ?: ""
    val forcePathStyle = env("SENCHA_S3_FORCE_PATH_STYLE")?.toBooleanStrictOrNull() ?: true
    val presignMinutes = env("SENCHA_S3_PRESIGN_MINUTES")?.toLongOrNull() ?: 15
    val registrationCode = env("SENCHA_REGISTRATION_CODE")
    val version = env("SENCHA_VERSION") ?: "dev"

    require(s3AccessKey.isNotBlank()) { "SENCHA_S3_ACCESS_KEY is required" }
    require(s3SecretKey.isNotBlank()) { "SENCHA_S3_SECRET_KEY is required" }

    return ServerConfig(
        port = port,
        dbPath = dbPath,
        sessionTtlHours = sessionTtlHours,
        s3 = S3Config(
            endpoint = URI.create(s3Endpoint),
            region = s3Region,
            bucket = s3Bucket,
            accessKey = s3AccessKey,
            secretKey = s3SecretKey,
            forcePathStyle = forcePathStyle,
            presignMinutes = presignMinutes,
        ),
        registrationCode = registrationCode,
        version = version,
    )
}

private fun env(name: String): String? = System.getenv(name)
