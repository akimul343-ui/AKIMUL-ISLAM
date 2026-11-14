package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface RemoteBackendApi {
    @GET("health")
    suspend fun getHealth(): HealthResponse

    @GET("api/devices")
    suspend fun getFleetDevices(@Query("fleet_id") fleetId: String): FleetDevicesResponse

    @POST("api/command")
    suspend fun sendCommand(@Body request: SendCommandRequest): SendCommandResponse

    @GET("api/poll/{deviceId}")
    suspend fun pollCommands(
        @Path("deviceId") deviceId: String,
        @Query("pin") pin: String,
        @Query("device_name") deviceName: String,
        @Query("device_alias") deviceAlias: String,
        @Query("fleet_id") fleetId: String,
        @Query("guard_armed") guardArmed: Boolean,
        @Query("is_device_owner") isDeviceOwner: Boolean,
        @Query("battery_percent") batteryPercent: Int
    ): PollCommandsResponse

    @POST("api/location")
    suspend fun reportLocation(@Body request: LocationReportRequest): Map<String, Any>

    @GET("api/status/{deviceId}")
    suspend fun getDeviceStatus(
        @Path("deviceId") deviceId: String,
        @Query("pin") pin: String
    ): DeviceStatusResponse
}

/**
 * Singleton Retrofit client bound directly to the hardcoded Render Flask server URL
 * in [Constants.RENDER_BASE_URL].
 */
object ApiClient {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    val baseUrl: String = normalizeUrl(Constants.RENDER_BASE_URL)

    val api: RemoteBackendApi by lazy {
        Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RemoteBackendApi::class.java)
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return normalizeUrl(Constants.RENDER_BASE_URL)
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}

class RemoteRepository {
    private fun apiFor(baseUrl: String = Constants.RENDER_BASE_URL): RemoteBackendApi {
        val normalized = ApiClient.normalizeUrl(baseUrl)
        if (normalized == ApiClient.baseUrl) {
            return ApiClient.api
        }
        return ApiClient.api
    }

    suspend fun checkHealth(baseUrl: String = Constants.RENDER_BASE_URL): Result<HealthResponse> = runCatching {
        apiFor(baseUrl).getHealth()
    }

    suspend fun getFleetDevices(
        baseUrl: String = Constants.RENDER_BASE_URL,
        fleetId: String
    ): Result<FleetDevicesResponse> = runCatching {
        apiFor(baseUrl).getFleetDevices(fleetId.trim().uppercase())
    }

    suspend fun sendCommand(
        baseUrl: String = Constants.RENDER_BASE_URL,
        request: SendCommandRequest
    ): Result<SendCommandResponse> = runCatching {
        apiFor(baseUrl).sendCommand(request)
    }

    suspend fun pollCommands(
        baseUrl: String = Constants.RENDER_BASE_URL,
        deviceId: String,
        pin: String,
        deviceName: String,
        deviceAlias: String,
        fleetId: String,
        guardArmed: Boolean,
        isDeviceOwner: Boolean,
        batteryPercent: Int
    ): Result<PollCommandsResponse> = runCatching {
        apiFor(baseUrl).pollCommands(
            deviceId = deviceId.trim().uppercase(),
            pin = pin.trim(),
            deviceName = deviceName,
            deviceAlias = deviceAlias,
            fleetId = fleetId.trim().uppercase(),
            guardArmed = guardArmed,
            isDeviceOwner = isDeviceOwner,
            batteryPercent = batteryPercent
        )
    }

    suspend fun reportLocation(
        baseUrl: String = Constants.RENDER_BASE_URL,
        request: LocationReportRequest
    ): Result<Unit> = runCatching {
        apiFor(baseUrl).reportLocation(request)
        Unit
    }

    suspend fun getDeviceStatus(
        baseUrl: String = Constants.RENDER_BASE_URL,
        deviceId: String,
        pin: String
    ): Result<DeviceStatusResponse> = runCatching {
        apiFor(baseUrl).getDeviceStatus(deviceId.trim().uppercase(), pin.trim())
    }
}
