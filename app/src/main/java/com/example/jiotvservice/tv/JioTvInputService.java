package com.example.jiotvservice.tv;

import com.example.jiotvservice.util.Logger;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.ExoPlayer;
import com.example.jiotvservice.data.PlaybackRemoteDataSource;
import com.example.jiotvservice.auth.DeviceInfoFactory;
import com.example.jiotvservice.model.AuthModels;
import com.example.jiotvservice.player.JioPlayerManager;
import com.example.jiotvservice.session.SessionManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JioTvInputService extends TvInputService {
    @Override public Session onCreateSession(String inputId) {
        Logger.d("MYJIO", "onCreateSession called");
        return new JioTvSession(this);
    }

    private static final class JioTvSession extends TvInputService.Session {
        private final Context context;
        private final SessionManager sessionManager;
        private final JioPlayerManager playerManager;
        private final PlaybackRemoteDataSource playbackDataSource;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final ExecutorService playbackExecutor = Executors.newSingleThreadExecutor();
        private Surface surface;
        private long playbackGeneration = 0;
        private long currentPlaybackGeneration = -1;
        private java.util.List<String> playbackCandidates;
        private AuthModels.PlaybackUrlResponse currentPlaybackResponse;
        private int candidateIndex;
        private String currentLicenseUrl;
        private int retryCount = 0;
        private static final int MAX_RETRY_COUNT = 3;
        private long lastPlaybackPosition = 0;
        private Uri currentChannelUri;
        private Integer pendingChannelId;
        private boolean playerListenersAttached = false;

        JioTvSession(Context context) {
            super(context);
            Logger.d("MYJIO", "JioTvSession constructor called");
            this.context = context.getApplicationContext();
            this.sessionManager = new SessionManager(context);
            this.playerManager = new JioPlayerManager(context, sessionManager);
            this.playbackDataSource = new PlaybackRemoteDataSource(context, sessionManager);
        }

        @UnstableApi
        @Override public void onSetCaptionEnabled(boolean enabled) {
            Logger.d("MYJIO", "onSetCaptionEnabled called");
            ExoPlayer player = playerManager.getPlayer();
            player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !enabled)
                            .build());
        }

        @UnstableApi
        @Override public boolean onSetSurface(@Nullable Surface surface) {
            Logger.d("MYJIO", "onSetSurface called");
            this.surface = surface;
            playerManager.setSurface(surface);
            ExoPlayer player = playerManager.getPlayer();
            setupPlayerListeners(player);

            // Google Live Channels may call onTune() before a Surface exists.
            // Do not lose that tune request; start it as soon as the Surface arrives.
            if (surface != null && pendingChannelId != null) {
                int channelId = pendingChannelId;
                pendingChannelId = null;
                fetchAndPlay(channelId);
            }
            return true;
        }

        @UnstableApi
        @Override public void onSetStreamVolume(float volume) {
            Logger.d("MYJIO", "onSetStreamVolume called");
            playerManager.getPlayer().setVolume(Math.max(0f, Math.min(1f, volume)));
        }

        @Override public boolean onTune(Uri channelUri) {
            Logger.d("MYJIO", "onTune called");
            this.currentChannelUri = channelUri;
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
            int channelId = getChannelId(channelUri);
            if (channelId < 0) {
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                return false;
            }

            pendingChannelId = channelId;
            // Surface and tune callbacks are asynchronous. Only start playback
            // immediately when the video output is already available.
            if (surface != null) {
                pendingChannelId = null;
                fetchAndPlay(channelId);
            }
            return true;
        }

        @UnstableApi
        private void setupPlayerListeners(ExoPlayer player) {
            if (playerListenersAttached) return;
            playerListenersAttached = true;

            player.addAnalyticsListener(new AnalyticsListener() {
                @Override public void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {
                    // Advertise video only after ExoPlayer has actually rendered a frame.
                    notifyVideoAvailable();
                }
            });

            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    Logger.d("MYJIO", "TV input player state=" +
                            JioPlayerManager.playbackStateName(state));
                    if (state == Player.STATE_IDLE) {
                        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                    }
                }

                @Override public void onPlayerError(PlaybackException error) {
                    Logger.e("MYJIO", "TV input player error: " +
                            JioPlayerManager.userVisibleError(error), error);
                    
                    if (currentPlaybackGeneration != playbackGeneration) {
                        Logger.w("MYJIO", "Ignoring error for stale generation");
                        return;
                    }
                    
                    lastPlaybackPosition = player.getCurrentPosition();
                    
                    if (retryCount < MAX_RETRY_COUNT) {
                        retryCount++;
                        Logger.d("MYJIO", "TV Input attempting playback retry " + retryCount + "/" + MAX_RETRY_COUNT);
                        if (tryNextFallback()) return;
                        
                        playbackExecutor.execute(() -> {
                            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                            main.post(() -> {
                                if (currentChannelUri != null && surface != null) {
                                    int retryChannelId = getChannelId(currentChannelUri);
                                    if (retryChannelId >= 0) fetchAndPlay(retryChannelId);
                                }
                            });
                        });
                    } else {
                        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                    }
                }
            });
        }

        private int getChannelId(Uri uri) {
            Logger.d("MYJIO", "getChannelId called");
            String[] projection = {TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA};
            try (Cursor c = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (c != null && c.moveToFirst()) return Integer.parseInt(c.getString(0));
            } catch (Exception ignored) {}
            return -1;
        }

        private void fetchAndPlay(int channelId) {
            final long generation = ++playbackGeneration;
            Logger.d("MYJIO", "TV input fetchAndPlay CH=" + channelId + " gen=" + generation);

            // Use the exact same authenticated playback request as the main JioTV
            // player. The previous TV-input implementation used a reduced request
            // that omitted SSO/session fields; the server could still return HTTP
            // 200, but the resulting Widevine license request could be rejected.
            playbackDataSource.getPlaybackUrl(channelId, new PlaybackRemoteDataSource.PlaybackUrlCallback() {
                @Override public void onSuccess(AuthModels.PlaybackUrlResponse body) {
                    main.post(() -> {
                        if (generation != playbackGeneration) {
                            Logger.w("MYJIO", "Ignoring stale TV playback response gen=" + generation);
                            return;
                        }

                        String playbackUrl = body == null ? null : body.getPreferredResult();
                        String licenseUrl = body == null ? null : body.getMpdKey();
                        if (playbackUrl == null || playbackUrl.isEmpty()) {
                            Logger.e("MYJIO", "TV input playback response contains no usable URL");
                            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                            return;
                        }

                        playbackCandidates = body.getPlaybackCandidates();
                        currentPlaybackResponse = body;
                        candidateIndex = 0;
                        currentLicenseUrl = licenseUrl;
                        currentPlaybackGeneration = generation;
                        retryCount = 0;
                        lastPlaybackPosition = 0;

                        playerManager.prepare(playbackUrl, licenseUrl, body);
                    });
                }

                @Override public void onFailure(String error) {
                    main.post(() -> {
                        if (generation != playbackGeneration) return;
                        Logger.e("MYJIO", "TV input playback URL failed: " + error);
                        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                    });
                }
            });
        }

        @UnstableApi
        private boolean tryNextFallback() {
            if (playbackCandidates == null || candidateIndex + 1 >= playbackCandidates.size()) {
                return false;
            }

            candidateIndex++;
            String nextUrl = playbackCandidates.get(candidateIndex);
            Logger.d("MYJIO", "TV Input error, trying alternate URL index " + candidateIndex + ": " + nextUrl);

            String licenseUrl = nextUrl.contains(".mpd") ? currentLicenseUrl : null;
            playerManager.prepare(nextUrl, licenseUrl, currentPlaybackResponse);
            return true;
        }

        @Override public void onRelease() {
            Logger.d("MYJIO", "onRelease called");
            main.post(playerManager::release);
            playbackExecutor.shutdownNow();
            pendingChannelId = null;
            surface = null;
            currentChannelUri = null;
        }
    }
}
