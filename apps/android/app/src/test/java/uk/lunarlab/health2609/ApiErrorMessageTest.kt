package uk.lunarlab.health2609

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import uk.lunarlab.health2609.core.network.ApiFactory

class ApiErrorMessageTest {
    private fun error(status: Int, body: String) =
        HttpException(Response.error<Any>(status, body.toResponseBody()))

    @Test
    fun recognitionTimeoutAndQueueHaveActionableMessages() {
        assertEquals("服务响应超时，请重试。", ApiFactory.formatErrorMessage(error(524, "upstream timeout")))
        assertEquals("照片识别超时，请重试。", ApiFactory.formatErrorMessage(error(504, "{\"error\":\"agy_timeout\"}")))
        assertEquals("识别服务正忙，请稍后重试。", ApiFactory.formatErrorMessage(error(429, "{\"error\":\"queue_full\"}")))
    }

    @Test
    fun errorDoesNotExposeResponseJsonOrPromiseAnOfflineMode() {
        val message = ApiFactory.formatErrorMessage(error(502, "{\"detail\":\"internal diagnostic\"}"))
        assertFalse(message.contains("HTTP"))
        assertFalse(message.contains("internal diagnostic"))
        assertFalse(ApiFactory.formatErrorMessage(java.net.UnknownHostException()).contains("本地模式"))
    }

    @Test
    fun unavailablePhotoServiceOffersManualEntryAndPreservesProblemId() {
        assertEquals("照片识别暂时不可用，可以先手动记录。\n问题编号：photo-test-1",
            ApiFactory.formatErrorMessage(error(503, "{\"error\":\"agy_model_unavailable\",\"requestId\":\"photo-test-1\"}")))
    }
}
