package com.example.jiotvservice.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface PlaybackApiService {
    @FormUrlEncoded
    @POST("playback/apis/v1.1/geturl")
    Call<ResponseBody> getPlaybackUrl(
            @Query("langId") String langId,
            @Query("userLanguages") String userLanguages,
            @Header("ssotoken") String ssoToken,
            @Header("accesstoken") String accessToken,
            @Header("crmid") String crmId,
            @Header("userid") String userId,
            @Header("subscriberid") String subscriberId,
            @Header("uniqueId") String uniqueId,
            @Header("deviceId") String deviceId,
            @Header("os") String os,
            @Header("osVersion") String osVersion,
            @Header("devicetype") String deviceType,
            @Header("usergroup") String userGroup,
            @Header("lbcookies") String lbCookies,
            @Header("appkey") String appKey,
            @Field("stream_type") String streamType,
            @Field("channel_id") String channelId,
            @Field("programId") String programId,
            @Field("showtime") String showtime,
            @Field("srno") String srno,
            @Field("begin") String begin,
            @Field("end") String end
    );
}
