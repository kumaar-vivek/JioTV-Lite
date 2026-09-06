package com.example.jiotvservice.tv;

import android.content.Context;
import android.media.tv.TvContract;
import com.example.jiotvservice.util.Logger;
import com.example.jiotvservice.api.ApiProvider;
import com.example.jiotvservice.api.JioTvApiService;
import com.example.jiotvservice.api.RawResponseReader;
import com.example.jiotvservice.model.AuthModels;
import com.example.jiotvservice.model.Channel;
import com.example.jiotvservice.epg.EpgProgram;
import com.example.jiotvservice.epg.EpgRepository;
import com.example.jiotvservice.session.SessionManager;
import java.util.Collections;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class JioTvSyncAdapter {
    public interface SyncCallback {
        void onSuccess(int count);
        void onError(String message);
    }

    private JioTvSyncAdapter() {
        Logger.d("MYJIO", "JioTvSyncAdapter constructor called");
    }

    public static void syncChannels(Context context, String inputId, SyncCallback callback) {
        Logger.d("MYJIO", "syncChannels called");
        SessionManager session = new SessionManager(context);
        String ssoToken = session.getSsoToken();
        String accessToken = session.getAccessToken();
        String subscriberId = session.getSubscriberId();
        String uniqueId = session.getUniqueId();

        if (ssoToken == null || ssoToken.isEmpty() || accessToken == null || accessToken.isEmpty()) {
            if (callback != null) callback.onError("User not authenticated.");
            return;
        }

        ApiProvider.get().getChannels(
                JioTvApiService.CHANNELS_ALT_URL,
                ssoToken,
                accessToken,
                subscriberId,
                subscriberId,
                subscriberId,
                uniqueId,
                "tvYR7NSNn7rymo3F"
        ).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Logger.d("MYJIO", "syncChannels onResponse: code=" + response.code() + ", isSuccessful=" + response.isSuccessful());
                AuthModels.ChannelListResponse body = null;
                if (response.isSuccessful()) {
                    try {
                        RawResponseReader.Result rawResult =
                                RawResponseReader.readChunked(response.body());
                        Logger.d("MYJIO", "syncChannels raw body: length=" +
                                rawResult.getBody().length() +
                                ", chunks=" + rawResult.getChunks());
                        String raw = rawResult.getBody();
                        body = AuthModels.ChannelListResponse.fromRawJson(raw);
                    } catch (Exception e) {
                        Logger.e("MYJIO", "syncChannels parse failed", e);
                        if (callback != null) callback.onError("Channel sync parse failed.");
                        return;
                    }
                }

                if (response.isSuccessful() && body != null && body.getResult() != null) {
                    List<Channel> channels = body.getResult();
                    Collections.sort(channels, (c1, c2) -> Integer.compare(c1.getChannelNumber(), c2.getChannelNumber()));
                    Logger.d("MYJIO", "syncChannels success: count=" + channels.size());
                    TvContractUtils.replaceChannels(context, inputId, channels);
                    syncPrograms(context, inputId, channels, callback);
                } else {
                    Logger.e("MYJIO", "syncChannels error: code=" + response.code());
                    if (callback != null) callback.onError("Channel sync failed: HTTP " + response.code());
                }
            }
            @Override public void onFailure(Call<ResponseBody> call, Throwable t) {
                Logger.e("MYJIO", "syncChannels onFailure: " + t.getMessage(), t);
                if (callback != null) callback.onError(t.getMessage() == null ? "Network error" : t.getMessage());
            }
        });
    }

    /** Populate Android TV's TvProvider program table so Google Live Channels can
     * display JioTV EPG data. The app UI EPG repository alone is not visible to
     * the system Live Channels application. */
    private static void syncPrograms(Context context, String inputId, List<Channel> channels, SyncCallback callback) {
        if (channels == null || channels.isEmpty()) {
            if (callback != null) callback.onSuccess(0);
            return;
        }

        final EpgRepository repository = new EpgRepository(context);
        final java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(6);
        final java.util.concurrent.atomic.AtomicInteger remaining =
                new java.util.concurrent.atomic.AtomicInteger(channels.size());
        final java.util.concurrent.atomic.AtomicInteger programsInserted =
                new java.util.concurrent.atomic.AtomicInteger(0);

        for (Channel channel : channels) {
            executor.execute(() -> {
                try {
                    android.net.Uri channelUri = findChannelUri(context, inputId, channel.getChannelId());
                    if (channelUri == null) {
                        if (remaining.decrementAndGet() == 0) {
                            executor.shutdown();
                            repository.shutdown();
                            if (callback != null) callback.onSuccess(programsInserted.get());
                        }
                        return;
                    }
                    long providerChannelId = android.content.ContentUris.parseId(channelUri);

                    repository.getPrograms(channel.getChannelId(), 0, new EpgRepository.Callback() {
                        @Override public void onSuccess(List<EpgProgram> programs) {
                            try {
                                TvContractUtils.replacePrograms(context, providerChannelId, programs);
                                programsInserted.addAndGet(programs.size());
                            } finally {
                                if (remaining.decrementAndGet() == 0) {
                                    executor.shutdown();
                                    repository.shutdown();
                                    if (callback != null) callback.onSuccess(programsInserted.get());
                                }
                            }
                        }

                        @Override public void onFailure(String message) {
                            Logger.w("MYJIO", "EPG sync failed for CH " + channel.getChannelId() + ": " + message);
                            if (remaining.decrementAndGet() == 0) {
                                executor.shutdown();
                                repository.shutdown();
                                if (callback != null) callback.onSuccess(programsInserted.get());
                            }
                        }
                    });
                } catch (Exception e) {
                    Logger.e("MYJIO", "EPG sync exception for CH " + channel.getChannelId(), e);
                    if (remaining.decrementAndGet() == 0) {
                        executor.shutdown();
                        repository.shutdown();
                        if (callback != null) callback.onSuccess(programsInserted.get());
                    }
                }
            });
        }
    }

    private static android.net.Uri findChannelUri(Context context, String inputId, int jioChannelId) {
        String[] projection = {
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA
        };
        try (android.database.Cursor cursor = context.getContentResolver().query(
                TvContract.buildChannelsUriForInput(inputId), projection, null, null, null)) {
            if (cursor != null) {
                int idIndex = cursor.getColumnIndex(TvContract.Channels._ID);
                int dataIndex = cursor.getColumnIndex(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA);
                while (cursor.moveToNext()) {
                    if (dataIndex >= 0 && String.valueOf(jioChannelId).equals(cursor.getString(dataIndex))) {
                        long rowId = cursor.getLong(idIndex);
                        return TvContract.buildChannelUri(rowId);
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("MYJIO", "Unable to locate TV provider channel " + jioChannelId, e);
        }
        return null;
    }

}
