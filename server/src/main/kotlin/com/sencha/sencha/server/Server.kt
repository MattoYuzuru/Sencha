package com.sencha.sencha.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.callid.CallId
import io.ktor.server.callid.callId
import io.ktor.server.callid.callIdMdc
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

fun main() {
    val config = loadConfig()
    val database = Database(config.dbPath)
    config.registrationCode?.let { database.ensureRegistrationCode(it) }
    val authService = AuthService(database, config)
    val s3Service = S3Service(config.s3)

    embeddedServer(Netty, port = config.port) {
        configureServer(config, database, authService, s3Service)
    }.start(wait = true)
}

private fun Application.configureServer(
    config: ServerConfig,
    database: Database,
    authService: AuthService,
    s3Service: S3Service,
) {
    val auditLogger = LoggerFactory.getLogger("Audit")

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        )
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        replyToHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { it.isNotBlank() }
    }
    install(CallLogging) {
        callIdMdc("requestId")
    }
    install(StatusPages) {
        exception<AuthException> { call, cause ->
            auditLogger.info("auth_failure requestId=${call.callId} error=${cause.message}")
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized", call.callId))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request", call.callId))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Server error", call.callId))
            environment.log.error("Unhandled error", cause)
        }
    }
    install(Authentication) {
        bearer("auth-bearer") {
            authenticate { credential ->
                val session = authService.authenticate(credential.token)
                session?.let { SessionPrincipal(it.userId, it.deviceId) }
            }
            challenge { _, _ ->
                auditLogger.info("auth_failure requestId=${call.callId} reason=invalid_token")
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized", call.callId))
            }
        }
    }

    routing {
        get("/v1/health") {
            call.respond(HealthResponse(status = "ok", version = config.version))
        }
        post("/v1/auth/register") {
            val request = call.receive<RegisterDeviceRequest>()
            val response = authService.registerDevice(request)
            call.respond(response)
        }
        authenticate("auth-bearer") {
            get("/v1/auth/whoami") {
                val principal = call.principal<SessionPrincipal>() ?: throw AuthException("Unauthorized")
                call.respond(WhoAmIResponse(userId = principal.userId, deviceId = principal.deviceId))
            }
            post("/v1/events/batch") {
                val principal = call.principal<SessionPrincipal>() ?: throw AuthException("Unauthorized")
                val request = call.receive<EventBatchRequest>()
                if (request.events.any { it.deviceId != principal.deviceId }) {
                    throw IllegalArgumentException("Device mismatch")
                }
                val accepted = database.insertEvents(principal.userId, request.events)
                call.respond(EventBatchResponse(accepted = accepted))
            }
            get("/v1/events/since") {
                val principal = call.principal<SessionPrincipal>() ?: throw AuthException("Unauthorized")
                val cursor = call.request.queryParameters["cursor"]?.toLongOrNull() ?: 0L
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 200
                val records = database.fetchEventsSince(principal.userId, cursor, limit)
                val nextCursor = records.lastOrNull()?.cursor ?: cursor
                call.respond(
                    EventsSinceResponse(
                        events = records.map { it.event },
                        nextCursor = nextCursor,
                    )
                )
            }
            post("/v1/blobs/presign") {
                val principal = call.principal<SessionPrincipal>() ?: throw AuthException("Unauthorized")
                val request = call.receive<PresignRequest>()
                val key = if (request.operation == PresignOperation.UPLOAD) {
                    buildBlobKey(
                        userId = principal.userId,
                        chatId = request.chatId,
                        blobId = request.blobId,
                        sha256 = request.sha256,
                        extension = request.extension,
                    )
                } else {
                    database.findBlobKey(principal.userId, request.blobId)
                        ?: throw IllegalArgumentException("Blob not found")
                }
                val response = when (request.operation) {
                    PresignOperation.UPLOAD -> {
                        database.upsertBlob(principal.userId, request, key)
                        s3Service.presignUpload(key, request.mime)
                    }
                    PresignOperation.DOWNLOAD -> {
                        s3Service.presignDownload(key)
                    }
                }
                auditLogger.info(
                    "presign requestId=${call.callId} userId=${principal.userId} op=${request.operation} key=$key"
                )
                call.respond(response)
            }
            get("/v1/nodes") {
                val principal = call.principal<SessionPrincipal>() ?: throw AuthException("Unauthorized")
                val nodes = database.listNodes(principal.userId)
                call.respond(NodesResponse(nodes = nodes))
            }
            post("/v1/nodes") {
                val principal = call.principal<SessionPrincipal>() ?: throw AuthException("Unauthorized")
                val request = call.receive<NodeUpsertRequest>()
                val node = database.upsertNode(principal.userId, request)
                call.respond(node)
            }
        }
    }
}

private data class SessionPrincipal(
    val userId: String,
    val deviceId: String,
) : Principal
