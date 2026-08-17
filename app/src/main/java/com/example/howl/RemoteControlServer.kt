package com.example.howl

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

const val CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

fun generateApiKey(length: Int = 12): String {
    return buildString(length) {
        repeat(length) {
            append(CROCKFORD_BASE32[Random.nextInt(CROCKFORD_BASE32.length)])
        }
    }
}

// --- Data Classes ---

@Serializable
data class WsAuthRequest(val api_key: String)

@Serializable
data class WsCommandRequest(val endpoint: String, val params: JsonElement = JsonNull)

@Serializable
data class WsResponse(
    val status: Int,
    val endpoint: String,
    val body: JsonElement
)

@Serializable
data class StartPlayerRequest(val from: Float? = null)

@Serializable
data class SeekRequest(val position: Float)

@Serializable
data class LoadFunscriptRequest(
    val title: String = "",
    val loop: Boolean = false,
    val play: Boolean = false,
    val funscript: String
)

@Serializable
data class LoadHwlRequest(
    val title: String = "",
    val loop: Boolean = true,
    val play: Boolean = false,
    val hwl: String
)

@Serializable
data class StartStreamRequest(
    val pulse_rate_hz: Int = 10,
    val buffer_size: Int = 3,
    val interpolate: Boolean = true,
    val title: String = ""
)

@Serializable
data class StreamPulseRequest(
    val pulses: List<Pulse>
)

@Serializable
data class SetPowerRequest(
    val power_a: Int? = null,
    val power_b: Int? = null
)

@Serializable
data class IncrementPowerRequest(
    val channel: Int,
    val step: Int = 0
)

@Serializable
data class DecrementPowerRequest(
    val channel: Int,
    val step: Int = 0
)

@Serializable
data class SetMuteRequest(
    val value: Boolean? = null,
)

@Serializable
data class SetAutoIncreaseRequest(
    val value: Boolean? = null,
)

@Serializable
data class SetSwapChannelsRequest(
    val value: Boolean? = null,
)

@Serializable
data class LoadActivityRequest(
    val name: String,
    val play: Boolean = false,
)

@Serializable
data class ActivityInfo(
    val name: String,
    val display_name: String
)

@Serializable
data class AvailableActivitiesResponse(
    val activities: List<ActivityInfo>
)

@Serializable
data class MainOptionsStatusResponse(
    val power_a: Int,
    val power_b: Int,
    val power_a_limit: Int,
    val power_b_limit: Int,
    val mute: Boolean,
    val auto_increase_power: Boolean,
    val swap_channels: Boolean
)

@Serializable
data class PlayerStatusResponse(
    val playing: Boolean,
    val position: Float,
    val title: String,
    val duration: Float
)

@Serializable
data class StatusResponse(
    val options: MainOptionsStatusResponse,
    val player: PlayerStatusResponse
)

@Serializable
object EmptyResponse

@Serializable
data class ErrorResponse(
    val error: ErrorBody
)

@Serializable
data class ErrorBody(
    val message: String
)

// --- Handler Result Wrapper ---

/**
 * Wraps the result of an API request to allow the handler to dictate
 * both the HTTP status code and the response body.
 */
data class HandlerResult(
    val status: HttpStatusCode,
    val body: Any
)

// --- Request Handler Class ---

class RequestHandler {
    private val json = Json { ignoreUnknownKeys = true }
    private var activeStreamSource: StreamSource? = null
    private var streamMonitorJob: Job? = null
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val TAG = "RequestHandler"
    }

    /**
     * Central dispatcher for all API endpoints.
     * Both HTTP and WebSocket servers can call this method by passing the endpoint name
     * and the raw JSON parameters.
     */
    suspend fun handleRequest(endpoint: String, params: JsonElement): HandlerResult {
        return try {
            when (endpoint) {
                "status" -> HandlerResult(HttpStatusCode.OK, buildStatusResponse())
                "start_player" -> handleStartPlayer(json.decodeFromJsonElement(StartPlayerRequest.serializer(), params))
                "seek" -> handleSeek(json.decodeFromJsonElement(SeekRequest.serializer(), params))
                "stop_player" -> handleStopPlayer()
                "load_funscript" -> handleLoadFunscript(json.decodeFromJsonElement(LoadFunscriptRequest.serializer(), params))
                "load_hwl" -> handleLoadHwl(json.decodeFromJsonElement(LoadHwlRequest.serializer(), params))
                "start_stream" -> handleStartStream(json.decodeFromJsonElement(StartStreamRequest.serializer(), params))
                "stop_stream" -> handleStopStream()
                "stream_pulse" -> handleStreamPulse(json.decodeFromJsonElement(StreamPulseRequest.serializer(), params))
                "set_power" -> handleSetPower(json.decodeFromJsonElement(SetPowerRequest.serializer(), params))
                "increment_power" -> handleIncrementPower(json.decodeFromJsonElement(IncrementPowerRequest.serializer(), params))
                "decrement_power" -> handleDecrementPower(json.decodeFromJsonElement(DecrementPowerRequest.serializer(), params))
                "set_mute" -> handleSetMute(json.decodeFromJsonElement(SetMuteRequest.serializer(), params))
                "set_swap_channels" -> handleSetSwapChannels(json.decodeFromJsonElement(SetSwapChannelsRequest.serializer(), params))
                "set_auto_increase" -> handleSetAutoIncrease(json.decodeFromJsonElement(SetAutoIncreaseRequest.serializer(), params))
                "available_activities" -> handleAvailableActivities()
                "load_activity" -> handleLoadActivity(json.decodeFromJsonElement(LoadActivityRequest.serializer(), params))
                else -> HandlerResult(HttpStatusCode.NotFound, ErrorResponse(ErrorBody("Unknown endpoint: $endpoint")))
            }
        } catch (e: SerializationException) {
            // Catch deserialization errors (e.g. missing required fields, wrong types)
            HLog.w(TAG, "Invalid parameters for endpoint $endpoint", e)
            HandlerResult(HttpStatusCode.BadRequest, ErrorResponse(ErrorBody("Invalid parameters")))
        } catch (e: Exception) {
            // Catch any other unexpected errors
            HLog.w(TAG, "Error handling request for endpoint $endpoint", e)
            HandlerResult(HttpStatusCode.InternalServerError, ErrorResponse(ErrorBody("Internal server error")))
        }
    }

    fun buildStatusResponse(): StatusResponse {
        val options = MainOptions.state.value
        val playerState = Player.playerState.value
        val playerPosition = Player.playerPosition.value

        val optionsStatus = MainOptionsStatusResponse(
            power_a = options.channelAPower,
            power_b = options.channelBPower,
            power_a_limit = Prefs.powerLimitA.value,
            power_b_limit = Prefs.powerLimitB.value,
            mute = options.globalMute,
            auto_increase_power = options.autoIncreasePower,
            swap_channels = options.swapChannels
        )

        val playerStatus = PlayerStatusResponse(
            playing = playerState.isPlaying,
            position = playerPosition.toFloat(),
            title = playerState.activePulseSource?.displayName?.value ?: "",
            duration = playerState.activePulseSource?.duration?.toFloat() ?: 0.0f,
        )

        return StatusResponse(
            options = optionsStatus,
            player = playerStatus
        )
    }

    private suspend fun handleStartPlayer(request: StartPlayerRequest): HandlerResult {
        HLog.i(TAG, "Handling command start_player(${request.from})")
        withContext(Dispatchers.Main) {
            Player.startPlayer(request.from?.toDouble())
        }
        return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
    }

    private suspend fun handleSeek(request: SeekRequest): HandlerResult {
        HLog.i(TAG, "Handling command seek(${request.position})")
        withContext(Dispatchers.Main) {
            Player.seek(request.position.toDouble())
        }
        return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
    }

    private suspend fun handleStopPlayer(): HandlerResult {
        HLog.i(TAG, "Handling command stop_player")
        withContext(Dispatchers.Main) {
            Player.stopPlayer()
        }
        return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
    }

    private suspend fun handleLoadFunscript(request: LoadFunscriptRequest): HandlerResult {
        HLog.i(
            TAG,
            "Handling command load_funscript(title='${request.title}', loop=${request.loop}, play=${request.play}, funscript length=${request.funscript.length})"
        )
        try {
            val pulseSource = FunscriptPulseSource()
            pulseSource.loadFromString(request.funscript, request.title)
            withContext(Dispatchers.Main) {
                Player.switchPulseSource(pulseSource)
                if (request.play) Player.startPlayer()
            }
            return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
        } catch (_: BadFileException) {
            HLog.w(TAG, "Failed to load funscript: invalid funscript file")
            return HandlerResult(HttpStatusCode.BadRequest, ErrorResponse(ErrorBody("Invalid funscript file")))
        } catch (e: Exception) {
            HLog.w(TAG, "Unexpected error loading funscript", e)
            return HandlerResult(HttpStatusCode.InternalServerError, ErrorResponse(ErrorBody("Internal server error")))
        }
    }

    private suspend fun handleLoadHwl(request: LoadHwlRequest): HandlerResult {
        val hwlData = Base64.getDecoder().decode(request.hwl)
        HLog.i(
            TAG,
            "Handling command load_hwl(title='${request.title}', loop=${request.loop}, play=${request.play}, hwl size=${hwlData.size} bytes)"
        )

        try {
            val pulseSource = HWLPulseSource()
            pulseSource.loadFromBytes(data = hwlData, title = request.title, loop = request.loop)

            withContext(Dispatchers.Main) {
                Player.switchPulseSource(pulseSource)
                if (request.play) Player.startPlayer()
            }

            return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
        } catch (_: BadFileException) {
            HLog.w(TAG, "Failed to load HWL: invalid HWL file")
            return HandlerResult(HttpStatusCode.BadRequest, ErrorResponse(ErrorBody("Invalid HWL file")))
        } catch (e: Exception) {
            HLog.w(TAG, "Unexpected error loading HWL", e)
            return HandlerResult(HttpStatusCode.InternalServerError, ErrorResponse(ErrorBody("Internal server error")))
        }
    }

    private suspend fun handleStartStream(request: StartStreamRequest): HandlerResult {
        HLog.i(
            TAG,
            "Handling command start_stream(pulse_rate_hz=${request.pulse_rate_hz}, " +
                    "buffer_size=${request.buffer_size}, interpolate=${request.interpolate}, " +
                    "title=${request.title})"
        )

        // Validate pulse rate against StreamSource constraints
        if (request.pulse_rate_hz < StreamSource.MIN_PULSE_RATE_HZ ||
            request.pulse_rate_hz > StreamSource.MAX_PULSE_RATE_HZ) {
            return HandlerResult(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorBody("pulse_rate_hz must be between ${StreamSource.MIN_PULSE_RATE_HZ} and ${StreamSource.MAX_PULSE_RATE_HZ}"))
            )
        }

        // Validate buffer size against StreamSource constraints
        if (request.buffer_size < StreamSource.MIN_BUFFER_SIZE ||
            request.buffer_size > StreamSource.MAX_BUFFER_SIZE) {
            return HandlerResult(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorBody("buffer_size must be between ${StreamSource.MIN_BUFFER_SIZE} and ${StreamSource.MAX_BUFFER_SIZE}"))
            )
        }

        val streamSource = StreamSource(
            pulseRateHz = request.pulse_rate_hz,
            bufferSize = request.buffer_size,
            interpolate = request.interpolate,
            title = request.title.ifEmpty { "Streaming" },
        )
        activeStreamSource = streamSource

        withContext(Dispatchers.Main) {
            Player.switchPulseSource(streamSource)
            Player.startPlayer()
        }

        startStreamMonitor()

        return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
    }

    private fun startStreamMonitor() {
        streamMonitorJob?.cancel()
        streamMonitorJob = monitorScope.launch {
            while (isActive) {
                delay(1000.milliseconds) // Check every second
                val source = activeStreamSource
                if (source != null && source.isTimedOut()) {
                    HLog.i(TAG, "Stream timed out, terminating.")
                    streamMonitorJob = null
                    cleanupStream()
                    break
                }
            }
        }
    }

    private suspend fun cleanupStream() {
        val streamSource = activeStreamSource ?: return
        activeStreamSource = null

        withContext(Dispatchers.Main) {
            // If the player is still using it as the active source, switch the player pulse source to null
            if (Player.playerState.value.activePulseSource == streamSource) {
                Player.switchPulseSource(null)
            }
        }
    }

    private suspend fun handleStopStream(): HandlerResult {
        HLog.i(TAG, "Handling command stop_stream")
        streamMonitorJob?.cancel()
        streamMonitorJob = null
        cleanupStream()
        return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
    }

    private fun handleStreamPulse(request: StreamPulseRequest): HandlerResult {
        val source = activeStreamSource
            ?: return HandlerResult(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorBody("No active stream"))
            )

        source.addPulses(request.pulses)

        // Return lightweight empty object instead of the full status
        return HandlerResult(HttpStatusCode.OK, EmptyResponse)
    }

    private fun handleSetPower(request: SetPowerRequest): HandlerResult {
        HLog.i(TAG, "Handling command set_power(power_a=${request.power_a}, power_b=${request.power_b})")
        if (request.power_a != null) MainOptions.setChannelPower(0, request.power_a)
        if (request.power_b != null) MainOptions.setChannelPower(1, request.power_b)
        return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
    }

    private fun handleIncrementPower(request: IncrementPowerRequest): HandlerResult {
        HLog.i(TAG, "Handling command increment_power(channel=${request.channel}, step=${request.step})")
        MainOptions.incrementChannelPower(request.channel, request.step)
        return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
    }

    private fun handleDecrementPower(request: DecrementPowerRequest): HandlerResult {
        HLog.i(TAG, "Handling command decrement_power(channel=${request.channel}, step=${request.step})")
        MainOptions.decrementChannelPower(request.channel, request.step)
        return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
    }

    private fun handleSetMute(request: SetMuteRequest): HandlerResult {
        HLog.i(TAG, "Handling command set_mute(value=${request.value})")
        val newMuteState = request.value ?: !MainOptions.state.value.globalMute
        MainOptions.setGlobalMute(newMuteState)
        return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
    }

    private fun handleSetSwapChannels(request: SetSwapChannelsRequest): HandlerResult {
        HLog.i(TAG, "Handling command set_swap_channels(value=${request.value})")
        val newSwapState = request.value ?: !MainOptions.state.value.swapChannels
        MainOptions.setSwapChannels(newSwapState)
        return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
    }

    private fun handleSetAutoIncrease(request: SetAutoIncreaseRequest): HandlerResult {
        HLog.i(TAG, "Handling command set_auto_increase(value=${request.value})")
        val newAutoIncreaseState = request.value ?: !MainOptions.state.value.autoIncreasePower
        MainOptions.setAutoIncreasePower(newAutoIncreaseState)
        return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
    }

    private fun handleAvailableActivities(): HandlerResult {
        HLog.i(TAG, "Handling command available_activities")
        val activities = ActivityType.entries.map { type ->
            ActivityInfo(name = type.name, display_name = type.displayName)
        }.sortedBy { it.display_name.lowercase() }

        return HandlerResult(HttpStatusCode.OK, AvailableActivitiesResponse(activities))
    }

    private suspend fun handleLoadActivity(request: LoadActivityRequest): HandlerResult {
        HLog.i(TAG, "Handling command load_activity(name='${request.name}', play=${request.play})")

        try {
            val activityType = ActivityType.valueOf(request.name)
            withContext(Dispatchers.Main) {
                if (!request.play) Player.stopPlayer()
                ActivityHost.setCurrentActivity(activityType)
                Player.switchPulseSource(ActivityHost)
                if (request.play) Player.startPlayer()
            }
            return HandlerResult(HttpStatusCode.OK, buildStatusResponse())
        } catch (_: IllegalArgumentException) {
            HLog.w(TAG, "Failed to load activity: unknown activity name '${request.name}'")
            return HandlerResult(HttpStatusCode.BadRequest, ErrorResponse(ErrorBody("Unknown activity: ${request.name}")))
        }
    }
}

// --- Remote Control Server ---

object RemoteControlServer {
    const val SERVER_PORT = 4695
    const val WS_SERVER_PORT = 4696
    const val TAG = "RemoteControlServer"

    private var restServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var wsServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    // Instantiate the centralised request handler
    private val requestHandler = RequestHandler()
    private val json = Json { ignoreUnknownKeys = true }

    fun initialise() {
        if (Prefs.remoteAPIKey.value == "changeme") {
            Prefs.remoteAPIKey.value = generateApiKey()
            Prefs.remoteAPIKey.save()
        }

        if (Prefs.remoteAccess.value) {
            start()
        }
    }

    fun startRestServer(port: Int = SERVER_PORT) {
        if (restServer != null) return
        HLog.v(TAG,"Starting REST server")

        restServer = embeddedServer(CIO, port = port, watchPaths = emptyList()) {
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
                allowCredentials = true
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Options)
            }
            install(ContentNegotiation) {
                json()
            }
            install(Authentication) {
                bearer("auth-bearer") {
                    realm = "Howl Remote Server"
                    authenticate { tokenCredential ->
                        if (tokenCredential.token == Prefs.remoteAPIKey.value) {
                            UserIdPrincipal("api_user")
                        } else {
                            null
                        }
                    }
                }
            }
            routing {
                authenticate("auth-bearer") {
                    post("/status") {
                        val result = requestHandler.handleRequest("status", JsonNull)
                        call.respond(result.status, result.body)
                    }
                    post("/stop_player") {
                        val result = requestHandler.handleRequest("stop_player", JsonNull)
                        call.respond(result.status, result.body)
                    }
                    post("/available_activities") {
                        val result = requestHandler.handleRequest("available_activities", JsonNull)
                        call.respond(result.status, result.body)
                    }
                    post("/start_player") {
                        val result = requestHandler.handleRequest("start_player", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                    post("/seek") {
                        val result = requestHandler.handleRequest("seek", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                    post("/load_funscript") {
                        val result = requestHandler.handleRequest("load_funscript", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                    post("/load_hwl") {
                        val result = requestHandler.handleRequest("load_hwl", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                    post("/start_stream") {
                        val result = requestHandler.handleRequest("start_stream", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                    post("/stop_stream") {
                        val result = requestHandler.handleRequest("stop_stream", JsonNull)
                        call.respond(result.status, result.body)
                    }
                    post("/stream_pulse") {
                        val result = requestHandler.handleRequest("stream_pulse", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                    post("/set_power") {
                        val result = requestHandler.handleRequest("set_power", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                    post("/increment_power") {
                        val result = requestHandler.handleRequest("increment_power", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                    post("/decrement_power") {
                        val result = requestHandler.handleRequest("decrement_power", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                    post("/set_mute") {
                        val result = requestHandler.handleRequest("set_mute", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                    post("/set_swap_channels") {
                        val result = requestHandler.handleRequest("set_swap_channels", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                    post("/set_auto_increase") {
                        val result = requestHandler.handleRequest("set_auto_increase", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                    post("/load_activity") {
                        val result = requestHandler.handleRequest("load_activity", call.receive<JsonElement>())
                        call.respond(result.status, result.body)
                    }
                }
            }
        }
        restServer?.start(wait = false)
    }

    fun startWsServer(port: Int = WS_SERVER_PORT) {
        if (wsServer != null) return
        HLog.v(TAG,"Starting websocket server")

        wsServer = embeddedServer(CIO, port = port, watchPaths = emptyList()) {
            install(WebSockets) {
                pingPeriod = 5.seconds
                timeout = 5.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                webSocket("/ws") {
                    var authenticated = false

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                if (!authenticated) {
                                    // First message must be authentication
                                    try {
                                        val authRequest = json.decodeFromString(WsAuthRequest.serializer(), text)
                                        if (authRequest.api_key == Prefs.remoteAPIKey.value) {
                                            authenticated = true
                                            val successBody = JsonPrimitive("Authenticated")
                                            val response = WsResponse(200, "auth", successBody)
                                            send(Frame.Text(json.encodeToString(WsResponse.serializer(), response)))
                                        } else {
                                            val errorBody = json.encodeToJsonElement(ErrorResponse.serializer(), ErrorResponse(ErrorBody("Invalid API key")))
                                            val response = WsResponse(401, "auth", errorBody)
                                            send(Frame.Text(json.encodeToString(WsResponse.serializer(), response)))
                                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid API key"))
                                            return@webSocket
                                        }
                                    } catch (e: Exception) {
                                        val errorBody = json.encodeToJsonElement(ErrorResponse.serializer(), ErrorResponse(ErrorBody("Invalid authentication message format")))
                                        val response = WsResponse(400, "auth", errorBody)
                                        send(Frame.Text(json.encodeToString(WsResponse.serializer(), response)))
                                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid auth format"))
                                        return@webSocket
                                    }
                                } else {
                                    // Subsequent messages are commands
                                    try {
                                        val command = json.decodeFromString(WsCommandRequest.serializer(), text)
                                        val result = requestHandler.handleRequest(command.endpoint, command.params)

                                        // Map the dynamic 'Any' body type to its specific serialization implementation
                                        val bodyElement = when (val body = result.body) {
                                            is StatusResponse -> json.encodeToJsonElement(StatusResponse.serializer(), body)
                                            is AvailableActivitiesResponse -> json.encodeToJsonElement(AvailableActivitiesResponse.serializer(), body)
                                            is ErrorResponse -> json.encodeToJsonElement(ErrorResponse.serializer(), body)
                                            is EmptyResponse -> json.encodeToJsonElement(EmptyResponse.serializer(), body)
                                            else -> JsonNull
                                        }

                                        val response = WsResponse(result.status.value, command.endpoint, bodyElement)
                                        send(Frame.Text(json.encodeToString(WsResponse.serializer(), response)))
                                    } catch (e: Exception) {
                                        HLog.w(TAG, "Error processing WS command", e)
                                        val errorBody = json.encodeToJsonElement(ErrorResponse.serializer(), ErrorResponse(ErrorBody("Invalid command format")))
                                        val response = WsResponse(400, "error", errorBody)
                                        send(Frame.Text(json.encodeToString(WsResponse.serializer(), response)))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        HLog.w(TAG, "WS connection error", e)
                    }
                }
            }
        }
        wsServer?.start(wait = false)
    }

    fun start() {
        startRestServer()
        startWsServer()
    }

    fun stop() {
        HLog.v(TAG, "Stopping remote control server")
        restServer?.stop(1000, 2000)
        restServer = null

        HLog.v(TAG, "Stopping websocket remote control server")
        wsServer?.stop(1000, 2000)
        wsServer = null
    }
}
