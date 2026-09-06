package com.example.jiotvservice.data;

import android.content.Context;
import com.example.jiotvservice.api.ApiProvider;
import com.example.jiotvservice.epg.EpgProgram;
import com.example.jiotvservice.api.PlaybackApiService;
import com.example.jiotvservice.api.PlaybackRequestClock;
import com.example.jiotvservice.auth.DeviceInfoFactory;
import com.example.jiotvservice.model.AuthModels;
import com.example.jiotvservice.session.SessionManager;
import com.example.jiotvservice.util.Logger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlaybackRemoteDataSource {
    private static final String TAG = "PlaybackDS";
    private final Context context;
    private final SessionManager session;
    private final PlaybackApiService api;

    public interface PlaybackUrlCallback {
        void onSuccess(AuthModels.PlaybackUrlResponse response);
        void onFailure(String error);
    }

    public PlaybackRemoteDataSource(Context context, SessionManager session) {
        this.context = context.getApplicationContext();
        this.session = session;
        this.api = ApiProvider.createService(PlaybackApiService.class);
    }

    public void getPlaybackUrl(int channelId, PlaybackUrlCallback callback) {
        String ssoToken = session.getSsoToken();
        String accessToken = session.getAccessToken();
        String subscriberId = session.getSubscriberId();
        String uniqueId = session.getUniqueId();
        String lbCookies = session.getLbCookie();
        String deviceId = DeviceInfoFactory.getAndroidId(context);
        String osVersion = DeviceInfoFactory.getOsVersion();

        PlaybackRequestClock.Stamp stamp = PlaybackRequestClock.now();
        String strChannelId = String.valueOf(channelId);
        String srno = stamp.srno; // This is already yyyyMMdd in PlaybackRequestClock

        Call<ResponseBody> call = api.getPlaybackUrl(
                "6", "6", // langId, userLanguages
                ssoToken, accessToken,
                subscriberId, subscriberId, subscriberId,
                uniqueId, deviceId, "android", osVersion,
                "phone", "tvYR7NSNn7rymo3F",
                lbCookies, "NzNiMDhlYzQyNjJm", // appKey
                "Seek", strChannelId, "", // streamType, channelId, programId
                "", srno, // showtime, srno
                stamp.begin, "" // begin, end
        );

        Logger.d(TAG, "ON-DEMAND REQUEST: CH " + channelId);
        Logger.d(TAG, ">> " + call.request().method() + " " + call.request().url());
        okhttp3.Headers headers = call.request().headers();
        for (int i = 0; i < headers.size(); i++) {
            Logger.d(TAG, ">> " + headers.name(i) + ": " + headers.value(i));
        }

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                try {
                    Logger.d(TAG, "<< HTTP " + r.code());
                    String raw = r.isSuccessful() ? r.body().string() : r.errorBody().string();
                    Logger.d(TAG, "<< Body: " + raw);
                    
                    if (r.isSuccessful()) {
                        AuthModels.PlaybackUrlResponse body = AuthModels.PlaybackUrlResponse.fromRawJson(raw);
                        body.setRequestContext(strChannelId, srno);
                        callback.onSuccess(body);
                    } else {
                        callback.onFailure("HTTP " + r.code() + ": " + raw);
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to parse playback response", e);
                    callback.onFailure(e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                Logger.e(TAG, "Network failure during playback URL request", t);
                callback.onFailure(t.getMessage());
            }
        });
    }
    /**
     * EPG-aware playback request. Mirrors the original application's important
     * distinction between Live and catch-up/Seek requests.
     */
    public void getPlaybackUrl(int channelId, EpgProgram program, PlaybackUrlCallback callback) {
        long now = System.currentTimeMillis() / 1000L;
        boolean current = program != null && program.isCurrent(now);
        boolean future = program != null && program.isFuture(now);

        if (future) {
            callback.onFailure("This program has not started yet.");
            return;
        }

        String streamType = current ? "Live" : "Seek";
        String channel = String.valueOf(channelId);
        String programId = program == null ? "" : program.getProgramId();
        String showtime = program == null ? "" : program.getShowtime();
        String srno = program == null ? "" : program.getSerialNo();

        String begin = "";
        String end = "";
        if (program != null) {
            begin = formatEpochGmt(program.getStartEpoch());
            end = formatEpochGmt(program.getEndEpoch());
        }

        String ssoToken = session.getSsoToken();
        String accessToken = session.getAccessToken();
        String subscriberId = session.getSubscriberId();
        String uniqueId = session.getUniqueId();
        String lbCookies = session.getLbCookie();
        String deviceId = DeviceInfoFactory.getAndroidId(context);
        String osVersion = DeviceInfoFactory.getOsVersion();

        Call<ResponseBody> call = api.getPlaybackUrl(
                "6", "6",
                ssoToken, accessToken,
                subscriberId, subscriberId, subscriberId,
                uniqueId, deviceId, "android", osVersion,
                "phone", "tvYR7NSNn7rymo3F",
                lbCookies, "NzNiMDhlYzQyNjJm",
                streamType, channel, programId,
                showtime, srno, begin, end
        );

        Logger.d(TAG, "EPG PLAYBACK REQUEST: CH " + channel +
                " streamType=" + streamType +
                " programId=" + programId +
                " begin=" + begin + " end=" + end);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                try {
                    String raw = r.isSuccessful()
                            ? (r.body() == null ? "" : r.body().string())
                            : (r.errorBody() == null ? "" : r.errorBody().string());

                    if (r.isSuccessful()) {
                        AuthModels.PlaybackUrlResponse body =
                                AuthModels.PlaybackUrlResponse.fromRawJson(raw);
                        body.setRequestContext(channel, srno);
                        callback.onSuccess(body);
                    } else {
                        callback.onFailure("HTTP " + r.code() + ": " + raw);
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to parse EPG playback response", e);
                    callback.onFailure(e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                Logger.e(TAG, "EPG playback network failure", t);
                callback.onFailure(t.getMessage() == null ? "Playback network error" : t.getMessage());
            }
        });
    }

    private static String formatEpochGmt(long epochSeconds) {
        if (epochSeconds <= 0) return "";
        long millis = epochSeconds > 100000000000L ? epochSeconds : epochSeconds * 1000L;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        return fmt.format(new Date(millis));
    }

}
