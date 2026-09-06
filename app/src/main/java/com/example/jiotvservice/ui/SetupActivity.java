package com.example.jiotvservice.ui;

import android.app.Activity;
import com.example.jiotvservice.util.Logger;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.widget.TextView;
import com.example.jiotvservice.R;
import com.example.jiotvservice.tv.JioTvSyncAdapter;
import com.example.jiotvservice.session.SessionManager;
import android.media.tv.TvContract;
import android.content.ComponentName;
import android.content.Intent;

public class SetupActivity extends Activity {
    private TextView status;
    private String inputId;

    @Override protected void onCreate(Bundle state) {
        Logger.d("MYJIO", "SetupActivity:onCreate called");
        super.onCreate(state);
        setContentView(R.layout.activity_setup);
        status = findViewById(R.id.setup_status);
        inputId = getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);

        if (inputId == null || inputId.isEmpty()) {
            inputId = TvContract.buildInputId(new ComponentName(this, com.example.jiotvservice.tv.JioTvInputService.class));
        }

        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            status.setText("Open JioTV Integrated and complete OTP login first.\nThen return to Live Channels setup.");
            return;
        }
        sync();
    }

    private void sync() {
        Logger.d("MYJIO", "sync called");
        status.setText("Syncing JioTV channels…");
        JioTvSyncAdapter.syncChannels(this, inputId, new JioTvSyncAdapter.SyncCallback() {
            @Override public void onSuccess(int count) {
                Logger.d("MYJIO", "onSuccess called");
                status.setText(count + " channels added to the TV input.");
                setResult(RESULT_OK);
                finish();
            }
            @Override public void onError(String message) {
                Logger.d("MYJIO", "onError called");
                status.setText("Channel sync failed: " + message);
                setResult(RESULT_CANCELED);
            }
        });
    }
}
