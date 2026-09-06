package com.example.jiotvservice.epg;

import android.content.Context;

import com.example.jiotvservice.api.ApiProvider;
import com.example.jiotvservice.api.JioTvApiService;
import com.example.jiotvservice.util.Logger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lightweight EPG repository following the original application's important
 * characteristics: channel + day-offset requests, in-flight de-duplication,
 * and caching.
 */
public class EpgRepository {
    private static final String TAG = "EPG";
    private static final long CACHE_MS = 5 * 60 * 1000L;

    private final JioTvApiService api;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, List<Callback>> inFlight = new ConcurrentHashMap<>();
    private final ExecutorService parseExecutor = Executors.newFixedThreadPool(3);

    public interface Callback {
        void onSuccess(List<EpgProgram> programs);
        void onFailure(String message);
    }

    private static final class CacheEntry {
        final long time;
        final List<EpgProgram> programs;
        CacheEntry(List<EpgProgram> programs) {
            this.time = System.currentTimeMillis();
            this.programs = programs;
        }
    }

    public EpgRepository(Context context) {
        api = ApiProvider.createService(JioTvApiService.class);
    }

    public void getPrograms(int channelId, int offset, Callback callback) {
        final String key = channelId + ":" + offset;
        CacheEntry cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.time < CACHE_MS) {
            callback.onSuccess(new ArrayList<>(cached.programs));
            return;
        }

        List<Callback> waiters = inFlight.computeIfAbsent(key,
                k -> Collections.synchronizedList(new ArrayList<>()));
        waiters.add(callback);
        if (waiters.size() > 1) {
            return;
        }

        Call<ResponseBody> call;
        try {
            call = api.getEpg(
                    JioTvApiService.EPG_URL,
                    offset,
                    channelId
            );
        } catch (Exception e) {
            notifyFailure(key, "EPG request creation failed: " + e.getMessage());
            return;
        }

        call.enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    notifyFailure(key, "EPG HTTP " + response.code());
                    return;
                }
                try {
                    String raw = response.body().string();
                    parseExecutor.execute(() -> {
                        try {
                            List<EpgProgram> programs = parse(raw, channelId);
                            cache.put(key, new CacheEntry(programs));
                            notifySuccess(key, programs);
                        } catch (Exception e) {
                            Logger.e(TAG, "EPG parse failed for CH " + channelId, e);
                            notifyFailure(key, "EPG parse error: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    notifyFailure(key, "EPG body read failed: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                notifyFailure(key, t.getMessage() == null ? "EPG network error" : t.getMessage());
            }
        });
    }

    public void getCurrentProgram(int channelId, Callback callback) {
        getPrograms(channelId, 0, new Callback() {
            @Override
            public void onSuccess(List<EpgProgram> programs) {
                long now = System.currentTimeMillis() / 1000L;
                EpgProgram current = null;
                for (EpgProgram p : programs) {
                    if (p.isCurrent(now)) {
                        current = p;
                        break;
                    }
                }
                List<EpgProgram> result = current == null
                        ? Collections.emptyList()
                        : Collections.singletonList(current);
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(String message) {
                callback.onFailure(message);
            }
        });
    }

    private void notifySuccess(String key, List<EpgProgram> programs) {
        List<Callback> callbacks = inFlight.remove(key);
        if (callbacks == null) return;
        for (Callback cb : callbacks) {
            try { cb.onSuccess(new ArrayList<>(programs)); } catch (Exception ignored) {}
        }
    }

    private void notifyFailure(String key, String message) {
        List<Callback> callbacks = inFlight.remove(key);
        if (callbacks == null) return;
        for (Callback cb : callbacks) {
            try { cb.onFailure(message); } catch (Exception ignored) {}
        }
    }

    public void clear() {
        cache.clear();
    }

    public void shutdown() {
        parseExecutor.shutdownNow();
    }

    private List<EpgProgram> parse(String raw, int fallbackChannelId) {
        JsonElement root = JsonParser.parseString(raw);
        JsonArray array = null;

        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            JsonElement epg = obj.get("epg");
            if (epg != null && epg.isJsonArray()) array = epg.getAsJsonArray();

            if (array == null) {
                JsonElement result = obj.get("result");
                if (result != null && result.isJsonObject()) {
                    JsonObject resultObj = result.getAsJsonObject();
                    JsonElement nested = resultObj.get("epg");
                    if (nested != null && nested.isJsonArray()) array = nested.getAsJsonArray();
                    if (array == null) {
                        JsonElement data = resultObj.get("data");
                        if (data != null && data.isJsonArray()) array = data.getAsJsonArray();
                    }
                } else if (result != null && result.isJsonArray()) {
                    array = result.getAsJsonArray();
                }
            }
            if (array == null) {
                JsonElement data = obj.get("data");
                if (data != null && data.isJsonArray()) array = data.getAsJsonArray();
                else if (data != null && data.isJsonObject()) {
                    JsonElement nested = data.getAsJsonObject().get("epg");
                    if (nested != null && nested.isJsonArray()) array = nested.getAsJsonArray();
                }
            }
        } else if (root.isJsonArray()) {
            array = root.getAsJsonArray();
        }

        List<EpgProgram> programs = new ArrayList<>();
        if (array == null) return programs;

        for (JsonElement e : array) {
            if (!e.isJsonObject()) continue;
            EpgProgram p = EpgProgram.fromJson(e.getAsJsonObject(), fallbackChannelId);
            if (p.getStartEpoch() > 0 && p.getEndEpoch() > p.getStartEpoch()) {
                programs.add(p);
            }
        }

        Collections.sort(programs, Comparator.comparingLong(EpgProgram::getStartEpoch));
        return programs;
    }
}
