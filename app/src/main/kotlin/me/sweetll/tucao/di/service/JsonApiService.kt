package me.sweetll.tucao.di.service

import io.reactivex.Observable
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.business.video.model.ReplyResponse
import me.sweetll.tucao.model.json.BaseResponse
import me.sweetll.tucao.model.json.ListResponse
import retrofit2.http.*

interface JsonApiService {
    @GET(ApiConfig.VIEW_API_URL)
    fun view(@Query("hid") hid: String): Observable<BaseResponse<Video>>

    @GET(ApiConfig.LIST_API_URL)
    fun list(@Query("tid") tid: Int,
             @Query("page") pageIndex: Int,
             @Query("pagesize") pageSize: Int,
             @Query("order") order: String?): Observable<ListResponse<Video>>

    @GET(ApiConfig.SEARCH_API_URL)
    fun search(@Query("tid") tid: Int?,
               @Query("page") pageIndex: Int,
               @Query("pagesize") pageSize: Int,
               @Query("order") order: String?,
               @Query("q") keyword: String): Observable<ListResponse<Video>>

    @GET(ApiConfig.RANK_API_URL)
    fun rank(@Query("tid") tid: Int,
             @Query("date") date: Int): Observable<BaseResponse<Map<Int, Video>>>

    @GET
    @Headers("user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.110 Safari/537.36")
    fun clicli(@Url url: String): Observable<me.sweetll.tucao.business.video.model.Clicli>

    @GET(ApiConfig.REPLY_API_URL)
    fun reply(@Query("commentid") commentId: String,
              @Query("replyid") replyId: String,
              @Query("page") page: Int,
              @Query("num") num: Int): Observable<ReplyResponse>

}
