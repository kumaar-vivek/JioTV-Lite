package com.example.jiotvservice.api;

import com.example.jiotvservice.model.AuthModels;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface AuthApiService {
    @Headers({
           "User-Agent: okhttp/4.2.2",
            "Host: jiotvapi.media.jio.com",
            "Content-Type: application/json",
            "Appname: RJIL_JioTV",
            "Os: android",
            "Devicetype: phone"
    })
    @POST("userservice/apis/v1/loginotp/send")
    Call<AuthModels.OtpSendResponse> sendOtp(@Body AuthModels.OtpRequest request);

    @Headers({
            "User-Agent: okhttp/4.2.2",
            "Host: jiotvapi.media.jio.com",
            "Content-Type: application/json",
            "Appname: RJIL_JioTV",
            "Os: android",
            "Devicetype: phone"
    })
    @POST("userservice/apis/v1/loginotp/verify")
    Call<AuthModels.AuthResponse> verifyOtp(@Body AuthModels.OtpVerifyRequest request);

    @Headers({
            "User-Agent: okhttp/4.2.2",
            "Host: jiotvapi.media.jio.com",
            "Content-Type: application/json"
    })
    @POST("userservice/apis/v1/refreshtoken")
    Call<AuthModels.AuthResponse> refreshAuthToken(@Body AuthModels.RefreshAuthTokenRequest request);

    @Headers({
            "User-Agent: okhttp/4.2.2",
            "Content-Type: application/json"
    })
    @POST
    Call<AuthModels.AuthResponse> refreshAccessToken(@retrofit2.http.Url String url, @Body AuthModels.RefreshAuthTokenRequest request);
}
