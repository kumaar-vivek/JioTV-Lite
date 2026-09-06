package com.example.jiotvservice.player;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.example.jiotvservice.api.ApiProvider;
import com.example.jiotvservice.auth.DeviceInfoFactory;
import com.example.jiotvservice.model.AuthModels;
import com.example.jiotvservice.session.SessionManager;
import com.example.jiotvservice.util.Logger;
import android.view.Surface;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import java.util.HashMap;
import java.util.Map;
import androidx.media3.ui.PlayerView;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;

public class JioPlayerManager {
    private static final String TAG = "JioPlayerMgr";
    private final Context context;
    private final SessionManager session;
    private final OkHttpClient streamClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private PlayerView playerView;
    private Surface surface;
    private DefaultTrackSelector trackSelector;
    private Runnable onPlaybackStall;
    private long prepareGeneration = 0;
    private boolean firstFrameRendered = false;

    public interface Listener {
        void onPlaybackStall();
    }
    private Listener listener;

    public JioPlayerManager(Context context, SessionManager session) {
        this.context = context.getApplicationContext();
        this.session = session;
        // Shared CookieManager is mandatory for Broadpeak CDN session persistence
        this.streamClient = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(ApiProvider.COOKIE_MANAGER))
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        
        this.onPlaybackStall = () -> {
            if (player != null) {
                int state = player.getPlaybackState();
                boolean playing = player.getPlayWhenReady();
                Logger.w(TAG, "Watchdog Check: State=" + playbackStateName(state) + ", playing=" + playing + ", firstFrame=" + firstFrameRendered);
                
                // If it's been timeout and we haven't rendered first frame yet
                if (!firstFrameRendered && (state == Player.STATE_BUFFERING || (state == Player.STATE_READY && playing))) {
                    Logger.e(TAG, "WATCHDOG TIMEOUT: No video frames rendered within time limit. Switching to fallback.");
                    if (listener != null) listener.onPlaybackStall();
                }
            }
        };
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Binds the Android PlayerView used by the Activity. The original JioPlayer
     * attaches the current ExoPlayer instance to its player view every time a
     * player is created. This is essential because this app deliberately
     * releases/recreates the player when switching channels.
     */
    public void setPlayerView(PlayerView playerView) {
        this.playerView = playerView;
        if (this.playerView != null) {
            this.playerView.setPlayer(player);
        }
    }

    public void setSurface(Surface surface) {
        this.surface = surface;
        if (this.player != null) {
            this.player.setVideoSurface(surface);
        }
    }

    @UnstableApi
    public ExoPlayer getPlayer() {
        if (player == null) {
            DefaultRenderersFactory renderersFactory =
                    new DefaultRenderersFactory(context);

            trackSelector = new DefaultTrackSelector(context);

            DefaultLoadControl loadControl =
                    new DefaultLoadControl.Builder()
                            .setAllocator(new DefaultAllocator(true, 65536))
                            .setBufferDurationsMs(
                                    6000,
                                    30000,
                                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                                    6000)
                            .setTargetBufferBytes(-1)
                            .setPrioritizeTimeOverSizeThresholds(true)
                            .build();

            player = new ExoPlayer.Builder(context)
                    .setRenderersFactory(renderersFactory)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl)
                    .build();

            // Critical: the PlayerView must follow the newly-created player.
            if (playerView != null) {
                playerView.setPlayer(player);
            }
            if (surface != null) {
                player.setVideoSurface(surface);
            }

            setupAnalytics();
            
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    Logger.d(TAG, "PLAYER STATE: " + playbackStateName(state));
                    if (state == Player.STATE_READY && !firstFrameRendered) {
                        // Start/Renew watchdog when reaching READY if no frame yet
                        mainHandler.removeCallbacks(onPlaybackStall);
                        mainHandler.postDelayed(onPlaybackStall, 10000);
                    }
                }

                @Override public void onIsPlayingChanged(boolean isPlaying) {
                    Logger.d(TAG, "PLAYER isPlaying: " + isPlaying);
                    if (isPlaying && !firstFrameRendered) {
                        mainHandler.removeCallbacks(onPlaybackStall);
                        mainHandler.postDelayed(onPlaybackStall, 10000);
                    } else {
                        mainHandler.removeCallbacks(onPlaybackStall);
                    }
                }
                
                @Override public void onPlayerError(PlaybackException error) {
                    mainHandler.removeCallbacks(onPlaybackStall);
                    Logger.e(TAG, "PLAYER ERROR: code=" + error.errorCode + 
                            " name=" + error.getErrorCodeName() + 
                            " message=" + userVisibleError(error), error);
                    
                    boolean isLicenseFailure = error.errorCode == PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED;

                    // Force immediate fallback on known DRM hardware/handshake errors
                    boolean isDrmError = isLicenseFailure ||
                                       error.errorCode == PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED ||
                                       error.errorCode == PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED ||
                                       error.errorCode == PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR;
                    
                    if (isLicenseFailure) {
                        Logger.e(TAG, "DRM license acquisition failed. This is normally an authorization/license-server problem, not a decoder hardware failure.", error);
                    }

                    if (isDrmError && listener != null) {
                        Logger.p(TAG, "DRM Critical Error, triggering failover...");
                        listener.onPlaybackStall();
                    }
                }

                @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                    if (mediaItem != null && mediaItem.localConfiguration != null) {
                        Logger.p(TAG, "MEDIA TRANSITION: uri=" + mediaItem.localConfiguration.uri + " reason=" + reason);
                    }
                }
            });
        }
        return player;
    }

    @UnstableApi
    private void setupAnalytics() {
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override public void onDrmSessionAcquired(EventTime eventTime, int state) {
                Logger.p(TAG, "DRM INFO: Session acquired, state=" + state);
            }
            @Override public void onDrmKeysLoaded(EventTime eventTime) {
                Logger.p(TAG, "DRM SUCCESS: Decryption keys loaded successfully");
            }
            @Override public void onDrmSessionManagerError(EventTime eventTime, Exception error) {
                Logger.e(TAG, "DRM FAILURE: Handshake failed", error);
            }
            @Override public void onLoadStarted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
                Logger.p(TAG, "NETWORK INFO: Loading segment: " + loadEventInfo.uri);
            }
            @Override public void onLoadCompleted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
                Logger.p(TAG, "NETWORK SUCCESS: Segment download complete");
            }
            @Override public void onLoadError(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, java.io.IOException error, boolean wasCanceled) {
                Logger.e(TAG, "NETWORK FAILURE: Segment download failed: " + loadEventInfo.uri, error);
            }
            @Override public void onVideoSizeChanged(EventTime eventTime, androidx.media3.common.VideoSize videoSize) {
                Logger.p(TAG, "RENDER INFO: Resolution: " + videoSize.width + "x" + videoSize.height);
            }
            @Override public void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {
                Logger.p(TAG, "RENDER SUCCESS: First frame rendered successfully");
                firstFrameRendered = true;
                mainHandler.removeCallbacks(onPlaybackStall);
            }
        });
    }

    @UnstableApi
    public void prepare(String url, String licenseUrl, AuthModels.PlaybackUrlResponse response) {
        final long generation = ++prepareGeneration;
        firstFrameRendered = false;

        boolean isDash = url.contains(".mpd");
        Logger.d(TAG, "PREPARING PLAYBACK gen=" + generation + ": " + (isDash ? "DASH/WIDEVINE" : "HLS/CLEAR") + " | URL: " + url);
        if (isDash) Logger.d(TAG, "DRM LICENSE: " + licenseUrl);
        
        mainHandler.removeCallbacks(onPlaybackStall);
        mainHandler.postDelayed(onPlaybackStall, 15000); // 15s initial watchdog for manifest/DRM load
        
        mainHandler.post(() -> {
            if (generation != prepareGeneration) {
                Logger.w(TAG, "Ignoring stale prepare request gen=" + generation + " (current=" + prepareGeneration + ")");
                return;
            }

            Uri uri = Uri.parse(url);

            // Original app pattern: release the old player, then create a
            // fresh player and attach that exact instance to PlayerView.
            releaseCurrentPlayer();

            ExoPlayer p = getPlayer();
            MediaSource mediaSource = buildMediaSource(uri, licenseUrl, response);

            p.setMediaSource(mediaSource);
            p.prepare();
            p.setPlayWhenReady(true);
        });
    }

    private void releaseCurrentPlayer() {
        mainHandler.removeCallbacks(onPlaybackStall);
        if (playerView != null) {
            playerView.setPlayer(null);
        }
        if (player != null) {
            player.stop();
            player.clearMediaItems();
            player.release();
            player = null;
        }
    }

    @UnstableApi
    private MediaSource buildMediaSource(Uri uri, String licenseUrl, AuthModels.PlaybackUrlResponse response) {
        JioHttpDataSourceFactory dataSourceFactory =
                new JioHttpDataSourceFactory(context, session);

        // Match the generic JioPlayerHelper path: default media-request
        // headers are installed on the HTTP factory and the DRM headers are
        // carried by MediaItem.DrmConfiguration. Do not create a second
        // independent DrmSessionManager/HttpMediaDrmCallback here.
        Map<String, String> headers = getDrmHeaders(response, licenseUrl);
        dataSourceFactory.setDefaultRequestProperties(headers);

        int type = Util.inferContentType(uri);
        MediaItem.Builder itemBuilder = new MediaItem.Builder().setUri(uri);

        if (type == C.CONTENT_TYPE_DASH) {
            itemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD);

            if (licenseUrl != null && !licenseUrl.isEmpty()) {
                Logger.d(TAG, "CONFIGURING DRM: Widevine UUID, License: " + licenseUrl);
                itemBuilder.setDrmConfiguration(
                        new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                                .setLicenseUri(licenseUrl)
                                .setMultiSession(true)
                                .setLicenseRequestHeaders(headers)
                                .build());
            } else {
                Logger.w(TAG, "DASH URL has no license URL; treating as clear DASH");
            }

            return new DashMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(itemBuilder.build());
        }

        if (type == C.CONTENT_TYPE_HLS) {
            itemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8);
            return new HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(itemBuilder.build());
        }

        return new DefaultMediaSourceFactory(dataSourceFactory)
                .createMediaSource(itemBuilder.build());
    }

    private Map<String, String> getDrmHeaders(
            AuthModels.PlaybackUrlResponse response,
            String licenseUrl) {

        Map<String, String> headers = new HashMap<>();

        // Match the header names used by the original JioPlayerHelper.
        putIfPresent(headers, "uniqueId", session.getUniqueId());
        putIfPresent(headers, "ssotoken", session.getSsoToken());
        putIfPresent(headers, "accesstoken", session.getAccessToken());
        putIfPresent(headers, "subscriberId", session.getSubscriberId());
        putIfPresent(headers, "deviceId", DeviceInfoFactory.getAndroidId(context));
        putIfPresent(headers, "os", "android");
        putIfPresent(headers, "versioncode", "413");
        putIfPresent(headers, "osversion", Build.VERSION.RELEASE);
        putIfPresent(headers, "crmid", session.getSubscriberId());
        putIfPresent(headers, "usergroup", "tvYR7NSNn7rymo3F");
        putIfPresent(headers, "lbcookies", session.getLbCookie());
        putIfPresent(headers, "appkey", "NzNiMDhlYzQyNjJm");
        putIfPresent(headers, "devicetype", "phone");

        if (response != null) {
            putIfPresent(headers, "channelId", response.getRequestChannelId());
            putIfPresent(headers, "contentId", response.getContentIdStr());
            putIfPresent(headers, "srno", response.getRequestSrno());
            putIfPresent(headers, "nvAuthorizations", response.getNvAuthorizations());
        }

        // Some playback responses expose the content id only in the signed
        // license URL. Use it as a fallback for the same content context.
        if (!headers.containsKey("contentId") && licenseUrl != null) {
            String contentId = extractQueryParameter(licenseUrl, "content_id");
            putIfPresent(headers, "contentId", contentId);
        }

        return headers;
    }

    private static String extractQueryParameter(String url, String name) {
        try {
            Uri uri = Uri.parse(url);
            String value = uri.getQueryParameter(name);
            return value == null || value.isEmpty() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void putIfPresent(Map<String, String> headers, String name, String value) {
        if (value != null && !value.isEmpty()) {
            headers.put(name, value);
        }
    }

    public void release() {
        prepareGeneration++;
        releaseCurrentPlayer();
    }

    public static String playbackStateName(int state) {
        switch (state) {
            case Player.STATE_IDLE: return "IDLE";
            case Player.STATE_BUFFERING: return "BUFFERING";
            case Player.STATE_READY: return "READY";
            case Player.STATE_ENDED: return "ENDED";
            default: return "UNKNOWN";
        }
    }

    public static String userVisibleError(PlaybackException error) {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            return "Server error: " + error.getMessage();
        }
        return "Playback error: " + error.getErrorCodeName();
    }
}
