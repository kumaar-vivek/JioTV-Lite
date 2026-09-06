package com.example.jiotvservice.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Url;

/**
 * JioTV content APIs.
 *
 * IMPORTANT: Do not use Java default methods in this Retrofit service.
 * Older Retrofit versions used by the project can try to parse a default
 * method as an HTTP service method and throw:
 * "HTTP method annotation is required".
 */
public interface JioTvApiService {

    String CHANNELS_ALT_URL =
            "https://jiotv.data.cdn.jio.com/apis/v3.0/getMobileChannelList/get/?" +
            "os=android&devicetype=phone&usertype=tvYR7NSNn7rymo3F";

    String PLAYBACK_URL =
            "https://jiotvapi.media.jio.com/playback/apis/v1.1/geturl?langId=6";

    String REFRESH_SSO_URL =
            "https://tv.media.jio.com/apis/v2.0/loginotp/refresh?langId=6";

    /** JioTV EPG endpoint used by the original EPG subsystem. */
    String EPG_URL =
            "https://jiotv.data.cdn.jio.com/apis/v1.3/getepg/get";

    String DEVICE_TYPE = "phone";

    @Headers({
            "Accept: application/json",
            "DeviceType: phone",
            "OS: android",
            "versioncode: 413",
            "languageid: 6",
            "usertype: JIO",
            "isott: false",
            "lbcookie: 1",
            "appkey: NzNiMDhlYzQyNjJm"
    })
    @GET("")
    Call<ResponseBody> getChannels(
            @Url String url,
            @Header("ssotoken") String ssoToken,
            @Header("accesstoken") String accessToken,
            @Header("crmid") String crmId,
            @Header("userid") String userId,
            @Header("subscriberid") String subscriberId,
            @Header("uniqueid") String uniqueId,
            @Header("usergroup") String userGroup
    );

    @Headers({
            "Accept: application/json",
            "User-Agent: okhttp/4.2.2",
            "Isott: false",
            "Versioncode: 413",
            "Devicetype: phone",
            "Lbcookie: 1",
            "Usergroup: tvYR7NSNn7rymo3F",
            "Appkey: NzNiMDhlYzQyNjJm",
            "Languageid: 6",
            "Os: android"
    })
    @FormUrlEncoded
    @POST("")
    Call<ResponseBody> getPlaybackUrl(
            @Url String url,
            @Header("Accesstoken") String accessToken,
            @Header("Channel_id") int channelId,
            @Header("Uniqueid") String uniqueId,
            @Header("Crmid") String crmId,
            @Header("Deviceid") String deviceId,
            @Header("Osversion") String osVersion,
            @Header("Subscriberid") String subscriberId,
            @Header("Userid") String userId,
            @Field("channel_id") int bodyChannelId,
            @Field("stream_type") String streamType,
            @Field("begin") String begin,
            @Field("srno") String srno
    );

    @Headers({
            "Accept: application/json",
            "DeviceType: phone",
            "OS: android",
            "versioncode: 413",
            "languageid: 6",
            "usertype: JIO",
            "isott: false",
            "lbcookie: 1",
            "appkey: NzNiMDhlYzQyNjJm"
    })
    @GET("")
    Call<ResponseBody> getEpg(
            @Url String url,
            @retrofit2.http.Query("offset") int offset,
            @retrofit2.http.Query("channel_id") int channelId
    );

    @Headers({
            "Accept-Encoding: gzip",
            "Accept: application/json"
    })

    //@GET("/apis/v2.0/loginotp/refresh")
    @GET("")
    Call<ResponseBody> callRefreshSsoToken(
            @Url String url,
            @Header("uniqueid") String uniqueid,
            @Header("ssotoken") String ssotoken,
            @Header("os") String os,
            @Header("devicetype") String devicetype,
            @Header("deviceid") String deviceid,
            @Header("versioncode") String versioncode);


}
