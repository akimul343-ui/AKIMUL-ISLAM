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

class RemoteRepository {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    private var cachedBaseUrl: String? = null
    private var cachedApi: RemoteBackendApi? = null

    private fun apiFor(baseUrl: String): RemoteBackendApi {
        val normalized = normalizeUrl(baseUrl)
        if (cachedBaseUrl == normalized && cachedApi != null) {
            return cachedApi!!
        }
        val retrofit = Retrofit.Builder()
            .baseUrl("$normalized/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val api = retrofit.create(RemoteBackendApi::class.java)
        cachedBaseUrl = normalized
        cachedApi = api
        return api
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    suspend fun checkHealth(baseUrl: String): Result<HealthResponse> = runCatching {
        apiFor(baseUrl).getHealth()
    }

    suspend fun getFleetDevices(baseUrl: String, fleetId: String): Result<FleetDevicesResponse> = runCatching {
        apiFor(baseUrl).getFleetDevices(fleetId.trim().uppercase())
    }

    suspend fun sendCommand(
        baseUrl: String,
        request: SendCommandRequest
    ): Result<SendCommandResponse> = runCatching {
        apiFor(baseUrl).sendCommand(request)
    }

    suspend fun pollCommands(
        baseUrl: String,
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
        baseUrl: String,
        request: LocationReportRequest
    ): Result<Unit> = runCatching {
        apiFor(baseUrl).reportLocation(request)
        Unit
    }

    suspend fun getDeviceStatus(
        baseUrl: String,
        deviceId: String,
        pin: String
    ): Result<DeviceStatusResponse> = runCatching {
        apiFor(baseUrl).getDeviceStatus(deviceId.trim().uppercase(), pin.trim())
    }
}
