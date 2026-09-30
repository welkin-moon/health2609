package uk.lunarlab.health2609.core.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import uk.lunarlab.health2609.BuildConfig

object DemoIdentity {
    @Volatile
    var schoolId: String = "demo-school"

    const val participantId: String = "demo-student"
}

object ApiFactory {
    fun create(): HealthApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header("x-demo-school", DemoIdentity.schoolId)
                    .header("x-demo-participant", DemoIdentity.participantId)
                    .header("x-demo-role", "student")
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HealthApi::class.java)
    }
}
