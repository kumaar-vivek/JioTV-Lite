package com.example.jiotvservice.tv;

import android.content.ContentResolver;
import com.example.jiotvservice.util.Logger;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.media.tv.TvContract;
import com.example.jiotvservice.model.Channel;
import com.example.jiotvservice.epg.EpgProgram;
import java.util.Collections;
import java.util.List;

public final class TvContractUtils {
    private TvContractUtils() {
        Logger.d("MYJIO", "TvContractUtils constructor called");
    }

    public static void replaceChannels(Context context, String inputId, List<Channel> channels) {
        Logger.d("MYJIO", "replaceChannels called");
        ContentResolver resolver = context.getContentResolver();
        resolver.delete(TvContract.buildChannelsUriForInput(inputId), null, null);

        if (channels == null) return;
        for (Channel ch : channels) {
            ContentValues v = new ContentValues();
            v.put(TvContract.Channels.COLUMN_INPUT_ID, inputId);
            v.put(TvContract.Channels.COLUMN_DISPLAY_NAME, ch.getChannelName());
            v.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, String.valueOf(ch.getChannelNumber()));
            v.put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_OTHER);
            v.put(TvContract.Channels.COLUMN_SERVICE_TYPE, TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO);
            // Stable application channel id; JioTvInputService reads this onTune().
            v.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA, String.valueOf(ch.getChannelId()));
            if (ch.getLogoUrl() != null && !ch.getLogoUrl().isEmpty()) {
                v.put(TvContract.Channels.COLUMN_APP_LINK_TEXT, "Open JioTV");
            }
            Uri uri = resolver.insert(TvContract.Channels.CONTENT_URI, v);
            if (uri == null) continue;
        }
    }

    public static void replacePrograms(Context context, long providerChannelId, List<EpgProgram> programs) {
        ContentResolver resolver = context.getContentResolver();
        Uri channelUri = TvContract.buildChannelUri(providerChannelId);
        resolver.delete(TvContract.Programs.CONTENT_URI,
                TvContract.Programs.COLUMN_CHANNEL_ID + "=?",
                new String[]{String.valueOf(providerChannelId)});

        if (programs == null) programs = Collections.emptyList();
        for (EpgProgram program : programs) {
            if (program.getEndEpoch() <= program.getStartEpoch()) continue;
            ContentValues v = new ContentValues();
            v.put(TvContract.Programs.COLUMN_CHANNEL_ID, providerChannelId);
            v.put(TvContract.Programs.COLUMN_TITLE, program.getTitle());
            v.put(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, program.getStartEpoch() * 1000L);
            v.put(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, program.getEndEpoch() * 1000L);
            v.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, program.getShowCategory());
            v.put(TvContract.Programs.COLUMN_LONG_DESCRIPTION, program.getDescription());
            if (!program.getShowCategory().isEmpty()) {
                v.put(TvContract.Programs.COLUMN_CANONICAL_GENRE, program.getShowCategory());
            }
            if (!program.getThumbnail().isEmpty()) {
                v.put(TvContract.Programs.COLUMN_POSTER_ART_URI, program.getThumbnail());
            } else if (!program.getPoster().isEmpty()) {
                v.put(TvContract.Programs.COLUMN_POSTER_ART_URI, program.getPoster());
            }
            resolver.insert(TvContract.Programs.CONTENT_URI, v);
        }
    }

}
