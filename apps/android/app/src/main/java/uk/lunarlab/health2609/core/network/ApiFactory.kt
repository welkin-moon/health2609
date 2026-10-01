package uk.lunarlab.health2609.core.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
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
        val raw = error.message ?: ""
        return when {
            error is java.net.UnknownHostException ||
            raw.contains("Unable to resolve host", ignoreCase = true) ||
            raw.contains("No address associated with hostname", ignoreCase = true) ->
                "未能连接到健康服务（域名解析受阻），已启用本地模式。可在设置中调整服务器地址。"
            error is java.net.SocketTimeoutException || raw.contains("timeout", ignoreCase = true) ->
                "连接服务器超时，请检查网络连接后重试。"
            error is java.net.ConnectException ||
            raw.contains("Failed to connect", ignoreCase = true) ||
            raw.contains("Connection refused", ignoreCase = true) ->
                "无法连接到服务器，请确认服务地址正确且网络通常。"
            raw.contains("HTTP 502", ignoreCase = true) || raw.contains("Bad Gateway", ignoreCase = true) ->
                "云端分析网关暂时不可用 (502)，可稍后重试或切换为本地服务。"
            raw.contains("HTTP 401", ignoreCase = true) || raw.contains("HTTP 403", ignoreCase = true) ->
                "访问凭证未通过，请检查账号授权。"
            else ->
                raw.ifBlank { "操作遇到异常，请稍后重试" }
        }
    }

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
