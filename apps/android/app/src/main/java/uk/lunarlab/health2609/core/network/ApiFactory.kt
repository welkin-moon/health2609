package uk.lunarlab.health2609.core.network

import java.util.concurrent.TimeUnit
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.gson.GsonConverterFactory
import uk.lunarlab.health2609.BuildConfig

object DemoIdentity {
    @Volatile
    var schoolId: String = "demo-school"

    const val participantId: String = "demo-student"
}

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long = 0
)

object ApiFactory {
    @Volatile
    var customBaseUrl: String? = null

    val currentBaseUrl: String
        get() {
            val custom = customBaseUrl?.trim()
            if (!custom.isNullOrBlank()) {
                return if (custom.endsWith("/")) custom else "$custom/"
            }
            val defaultUrl = BuildConfig.API_BASE_URL.trim()
            return if (defaultUrl.endsWith("/")) defaultUrl else "$defaultUrl/"
        }

    fun formatErrorMessage(error: Throwable): String {
        if (error is HttpException) {
            val code = runCatching {
                val body = error.response()?.errorBody()?.string() ?: "{}"
                JsonParser.parseString(body).asJsonObject.get("error")?.asString
            }.getOrNull()
            return when {
                code == "queue_full" -> "识别服务正忙，请稍后重试。"
                error.code() == 429 -> "服务正忙，请稍后重试。"
                code == "agy_timeout" -> "照片识别超时，请重试。"
                error.code() in setOf(408, 504, 524) -> "服务响应超时，请重试。"
                code == "agy_auth_failed" -> "识别服务暂时无法使用，请稍后重试。"
                code in setOf("agy_output_invalid", "agy_schema_invalid", "agy_invalid_json") -> "这次未能完成识别，请重试或手动记录。"
                code == "membership_not_found" -> "暂时无法读取学校信息，请到设置中选择学校。"
                error.code() == 413 -> "照片过大，请换一张较小的照片。"
                error.code() in setOf(401, 403) -> "服务暂时无法访问，请检查连接后重试。"
                error.code() >= 500 -> "服务暂时不可用，请稍后重试。"
                else -> "这次操作未完成，请检查填写内容后重试。"
            }
        }
        val raw = error.message ?: ""
        return when {
            error is java.net.UnknownHostException ||
            raw.contains("Unable to resolve host", ignoreCase = true) ||
            raw.contains("No address associated with hostname", ignoreCase = true) ->
                "暂时连不上服务，请检查网络后重试。"
            error is java.net.SocketTimeoutException || raw.contains("timeout", ignoreCase = true) ->
                "连接服务器超时，请检查网络连接后重试。"
            error is java.net.ConnectException ||
            raw.contains("Failed to connect", ignoreCase = true) ||
            raw.contains("Connection refused", ignoreCase = true) ->
                "暂时连不上服务，请检查网络或服务器地址。"
            raw.contains("HTTP 502", ignoreCase = true) || raw.contains("Bad Gateway", ignoreCase = true) ->
                "识别服务暂时不可用，请稍后重试。"
            raw.contains("HTTP 401", ignoreCase = true) || raw.contains("HTTP 403", ignoreCase = true) ->
                "服务暂时无法访问，请检查连接后重试。"
            else ->
                "这次操作未完成，请稍后重试。"
        }
    }

    fun isValidBaseUrl(value: String): Boolean = value.toHttpUrlOrNull() != null

    suspend fun testConnection(targetUrl: String): ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val cleanUrl = if (targetUrl.endsWith("/")) targetUrl else "$targetUrl/"
                val testHttpUrl = cleanUrl.toHttpUrlOrNull()
                    ?: return@withContext ConnectionTestResult(false, "URL 地址格式无效")

                val request = Request.Builder()
                    .url(testHttpUrl.resolve("healthz") ?: testHttpUrl)
                    .get()
                    .build()

                val okClient = OkHttpClient.Builder()
                    .connectTimeout(6, TimeUnit.SECONDS)
                    .readTimeout(6, TimeUnit.SECONDS)
                    .build()

                okClient.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - start
                    if (response.isSuccessful) {
                        ConnectionTestResult(true, "连接正常 (延时 ${latency}ms)", latency)
                    } else {
                        ConnectionTestResult(false, "服务器响应 HTTP ${response.code}", latency)
                    }
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - start
                ConnectionTestResult(false, formatErrorMessage(e), latency)
            }
        }
    }

    fun create(): HealthApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                var request = chain.request()

                // Dynamically route to currentBaseUrl
                val activeTargetUrl = currentBaseUrl.toHttpUrlOrNull()
                if (activeTargetUrl != null) {
                    val newUrl = request.url.newBuilder()
                        .scheme(activeTargetUrl.scheme)
                        .host(activeTargetUrl.host)
                        .port(activeTargetUrl.port)
                        .build()
                    request = request.newBuilder().url(newUrl).build()
                }

                val finalRequest = request.newBuilder()
                    .header("x-demo-school", DemoIdentity.schoolId)
                    .header("x-demo-participant", DemoIdentity.participantId)
                    .header("x-demo-role", "student")
                    .build()
                chain.proceed(finalRequest)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HealthApi::class.java)
    }
}
