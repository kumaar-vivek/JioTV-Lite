package com.example.jiotvservice.data;

import com.example.jiotvservice.model.AuthModels;
import com.example.jiotvservice.epg.EpgProgram;
import android.content.Context;
import com.example.jiotvservice.session.SessionManager;

public class PlaybackRepository {
    private final PlaybackRemoteDataSource remoteDataSource;

    public PlaybackRepository(Context context, SessionManager session) {
        this.remoteDataSource = new PlaybackRemoteDataSource(context, session);
    }

    public void getPlaybackUrl(int channelId, PlaybackRemoteDataSource.PlaybackUrlCallback callback) {
        remoteDataSource.getPlaybackUrl(channelId, callback);
    }

    public void getPlaybackUrl(int channelId, EpgProgram program,
                               PlaybackRemoteDataSource.PlaybackUrlCallback callback) {
        remoteDataSource.getPlaybackUrl(channelId, program, callback);
    }
}
