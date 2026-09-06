package com.example.jiotvservice.ui;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.jiotvservice.session.SessionManager;

public class PlaybackViewModelFactory implements ViewModelProvider.Factory {
    private final Context context;
    private final SessionManager session;

    public PlaybackViewModelFactory(Context context, SessionManager session) {
        this.context = context;
        this.session = session;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(PlaybackViewModel.class)) {
            return (T) new PlaybackViewModel(context, session);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
