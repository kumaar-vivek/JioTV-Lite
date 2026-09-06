package com.example.jiotvservice.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.jiotvservice.R;
import com.example.jiotvservice.model.Channel;
import com.example.jiotvservice.session.SessionManager;
import java.util.List;

public class ChannelTileAdapter extends BaseAdapter {
    public interface Listener { void onChannelClicked(Channel channel); }
    public interface FavoriteListener { void onFavoriteChanged(Channel channel); }

    private final Context context;
    private final List<Channel> channels;
    private final Listener listener;
    private final FavoriteListener favoriteListener;
    private final SessionManager session;

    public ChannelTileAdapter(Context context, List<Channel> channels, SessionManager session,
                              Listener listener, FavoriteListener favoriteListener) {
        this.context = context;
        this.channels = channels;
        this.session = session;
        this.listener = listener;
        this.favoriteListener = favoriteListener;
    }

    @Override public int getCount() { return channels.size(); }
    @Override public Object getItem(int position) { return channels.get(position); }
    @Override public long getItemId(int position) { return channels.get(position).getChannelId(); }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(context).inflate(R.layout.channel_tile, parent, false);
        }

        Channel ch = channels.get(position);
        ((TextView) v.findViewById(R.id.tile_number))
                .setText(String.valueOf(ch.getChannelNumber()));
        ((TextView) v.findViewById(R.id.tile_name))
                .setText(ch.getChannelName());

        ImageView logo = v.findViewById(R.id.tile_logo);
        ChannelLogoLoader.load(context, logo, ch.getLogoUrl());

        ImageView favorite = v.findViewById(R.id.tile_favorite);
        boolean isFavorite = session != null &&
                session.isFavoriteChannel(ch.getChannelId());

        // In Channel Tiles, show a heart ONLY when the channel is a favorite.
        // The empty/outline heart remains available in the player banner only.
        favorite.setVisibility(isFavorite ? View.VISIBLE : View.GONE);
        if (isFavorite) {
            favorite.setImageResource(R.drawable.ic_favorite);
            favorite.setContentDescription("Favorite");
            favorite.setOnClickListener(view -> {
                if (favoriteListener != null) favoriteListener.onFavoriteChanged(ch);
            });
        } else {
            favorite.setOnClickListener(null);
        }

        ImageView crown = v.findViewById(R.id.tile_crown);
        // Premium/unsubscribed indicator belongs at the top-right.
        crown.setVisibility(ch.isSubscribed() ? View.GONE : View.VISIBLE);

        ImageView hdIcon = v.findViewById(R.id.tile_hd);
        hdIcon.setVisibility(ch.isHd() ? View.VISIBLE : View.GONE);

        v.setOnClickListener(view -> {
            if (listener != null) listener.onChannelClicked(ch);
        });
        return v;
    }
}
