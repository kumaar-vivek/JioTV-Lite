package com.example.jiotvservice.tv;

import com.example.jiotvservice.util.Logger;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.content.ComponentName;

public class InitializeProgramsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Logger.d("MYJIO", "onReceive called");
        if (!TvContract.ACTION_INITIALIZE_PROGRAMS.equals(intent.getAction())) return;
        TvInputManager manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (manager == null) return;
        String inputId = TvContract.buildInputId(new ComponentName(context, JioTvInputService.class));
        JioTvSyncAdapter.syncChannels(context, inputId, null);
    }
}
