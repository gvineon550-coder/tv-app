package com.gvineon550coder.tvapp.data

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface ApiService {

    @GET("api/feeds/autowidget/2")
    suspend fun getChannels(
        @Query("client") client: String = "wdp",
        @Query("show_hidden_videos") showHidden: Boolean = true,
        @Query("show_user_hidden_videos") showUserHidden: Boolean = true,
        @Query("origin__type") originType: String = "rst,rspa"
    ): AutoWidgetResponse

    @GET("api/play/options/{id}")
    suspend fun getPlayOptions(@Path("id") id: String): PlayOptionsResponse

    @GET
    suspend fun getRaw(@Url url: String): ResponseBody
}
