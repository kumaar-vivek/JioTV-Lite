package com.example.jiotvservice.ui;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.jiotvservice.data.PlaybackRemoteDataSource;
import com.example.jiotvservice.epg.EpgProgram;
import com.example.jiotvservice.data.PlaybackRepository;
import com.example.jiotvservice.model.AuthModels;
import com.example.jiotvservice.model.Channel;
import com.example.jiotvservice.session.SessionManager;
import com.example.jiotvservice.util.Logger;
import java.util.ArrayList;
import java.util.List;

public class PlaybackViewModel extends ViewModel {
    private static final String TAG = "PlaybackVM";
    private final PlaybackRepository repository;
    
    private final MutableLiveData<AuthModels.PlaybackUrlResponse> playbackData = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Channel> currentChannel = new MutableLiveData<>();

    private long playbackGeneration = 0;
    private long currentPlaybackGeneration = -1;
    private List<String> candidates = new ArrayList<>();
    private int candidateIndex = 0;
    private String licenseUrl;
    private long lastFallbackTime = 0;

    public PlaybackViewModel(Context context, SessionManager session) {
        this.repository = new PlaybackRepository(context, session);
    }

    public LiveData<AuthModels.PlaybackUrlResponse> getPlaybackData() { return playbackData; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<Channel> getCurrentChannel() { return currentChannel; }

    public List<String> getCandidates() { return candidates; }
    public int getCandidateIndex() { return candidateIndex; }
    public String getLicenseUrl() { return licenseUrl; }

    public void playChannel(Channel channel) {
        final long generation = ++playbackGeneration;
        final int requestedChannelId = channel.getChannelId();
        
        currentChannel.setValue(channel);
        loading.setValue(true);
        candidates.clear();
        candidateIndex = 0;
        licenseUrl = null;
        lastFallbackTime = 0;
        
        Logger.p(TAG, "Fetching playback URL for " + channel.getChannelName() + " (CH " + requestedChannelId + ") gen=" + generation);
        
        repository.getPlaybackUrl(requestedChannelId, new PlaybackRemoteDataSource.PlaybackUrlCallback() {
            @Override
            public void onSuccess(AuthModels.PlaybackUrlResponse response) {
                Channel current = currentChannel.getValue();
                if (generation != playbackGeneration || current == null || current.getChannelId() != requestedChannelId) {
                    Logger.p(TAG, "Ignoring stale playback response for " + requestedChannelId + " (current=" + playbackGeneration + ", resp=" + generation + ")");
                    return;
                }
                currentPlaybackGeneration = generation;
                loading.postValue(false);
                candidates = response.getPlaybackCandidates();
                licenseUrl = response.getMpdKey();
                Logger.p(TAG, "Found " + candidates.size() + " playback candidates for channel");
                playbackData.postValue(response);
            }

            @Override
            public void onFailure(String errorMsg) {
                if (generation != playbackGeneration) {
                    return;
                }
                loading.postValue(false);
                error.postValue(errorMsg);
                Logger.e(TAG, "API failure for " + requestedChannelId + ": " + errorMsg);
            }
        });
    }


    public void playProgram(Channel channel, EpgProgram program) {
        final long generation = ++playbackGeneration;
        final int requestedChannelId = channel.getChannelId();

        currentChannel.setValue(channel);
        loading.setValue(true);
        candidates.clear();
        candidateIndex = 0;
        licenseUrl = null;
        lastFallbackTime = 0;

        Logger.d(TAG, "Fetching EPG playback URL for " + channel.getChannelName() +
                " (CH " + requestedChannelId + ") program=" +
                (program == null ? "" : program.getTitle()));

        repository.getPlaybackUrl(requestedChannelId, program,
                new PlaybackRemoteDataSource.PlaybackUrlCallback() {
                    @Override
                    public void onSuccess(AuthModels.PlaybackUrlResponse response) {
                        Channel current = currentChannel.getValue();
                        if (generation != playbackGeneration || current == null ||
                                current.getChannelId() != requestedChannelId) {
                            Logger.w(TAG, "Ignoring stale EPG playback response for " + requestedChannelId);
                            return;
                        }
                        currentPlaybackGeneration = generation;
                        loading.postValue(false);
                        candidates = response.getPlaybackCandidates();
                        licenseUrl = response.getMpdKey();
                        playbackData.postValue(response);
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        if (generation != playbackGeneration) return;
                        loading.postValue(false);
                        error.postValue(errorMsg);
                        Logger.e(TAG, "EPG playback failure for " + requestedChannelId + ": " + errorMsg);
                    }
                });
    }

    public boolean tryNextFallback() {
        if (currentPlaybackGeneration != playbackGeneration) {
            Logger.w(TAG, "Fallback aborted: generation mismatch (stale error)");
            return true; // Pretend we handled it to stop further fallbacks
        }

        long now = System.currentTimeMillis();
        if (now - lastFallbackTime < 2000) {
            Logger.d(TAG, "Fallback debounced (too rapid)");
            return true; 
        }
        
        if (candidates == null || candidateIndex + 1 >= candidates.size()) {
            Logger.w(TAG, "No more fallback candidates left. Total tried: " + (candidateIndex + 1));
            return false;
        }
        
        lastFallbackTime = now;
        candidateIndex++;
        String nextUrl = candidates.get(candidateIndex);
        Logger.i(TAG, "FALLBACK INITIATED: Attempting candidate index " + candidateIndex + ": " + nextUrl);
        
        AuthModels.PlaybackUrlResponse current = playbackData.getValue();
        if (current != null) {
            playbackData.postValue(current);
        }
        return true;
    }
}
