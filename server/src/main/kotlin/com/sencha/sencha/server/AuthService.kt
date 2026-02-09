package com.sencha.sencha.server

import java.time.Instant

class AuthService(
    private val database: Database,
    private val config: ServerConfig,
) {
    fun registerDevice(request: RegisterDeviceRequest): RegisterDeviceResponse {
        val userId = database.consumeRegistrationCode(request.code)
            ?: throw AuthException("Invalid or used registration code")
        val publicKeyBytes = runCatching { hexToBytes(request.devicePublicKeyHex) }
            .getOrElse { throw AuthException("Invalid public key") }
        val expectedDeviceId = sha256Hex(publicKeyBytes)
        if (expectedDeviceId != request.deviceId) {
            throw AuthException("Device id mismatch")
        }
        database.upsertDevice(
            deviceId = request.deviceId,
            userId = userId,
            publicKeyHex = request.devicePublicKeyHex,
            name = request.deviceName,
        )
        val expiresAt = Instant.now().plusSeconds(config.sessionTtlHours * 3600).toEpochMilli()
        val token = database.createSession(userId, request.deviceId, expiresAt)
        return RegisterDeviceResponse(
            userId = userId,
            deviceId = request.deviceId,
            sessionToken = token,
        )
    }

    fun authenticate(token: String): SessionRecord? = database.findSession(token)
}

class AuthException(message: String) : RuntimeException(message)
