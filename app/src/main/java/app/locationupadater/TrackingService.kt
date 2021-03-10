package app.locationupadater

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * @author dvphu on 10,March,2021
 */
interface TrackingService {
    @GET("tracking.php")
    suspend fun tracking(
        @Query("user") user: String?,
        @Query("list") list: String?,
        @Query("gps") gps: String?
    )
}