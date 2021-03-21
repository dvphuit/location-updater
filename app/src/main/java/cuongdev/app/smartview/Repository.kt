package cuongdev.app.smartview
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
/**
 * @author dvphu on 10,March,2021
 */
object Repository {
    //    private val BASE_URL:String = "http://catminh.biz/"
    fun makeRetrofitService(baseUrl: String): TrackingService {
        val  httpClient = OkHttpClient.Builder()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient.build())
            .build().create(TrackingService::class.java)
    }
}