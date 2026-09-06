package com.example.jiotvservice.epg;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * EPG program model. Field names intentionally accept the variants observed
 * in JioTV EPG responses and the original application's ProgramModel.
 */
public class EpgProgram {
    private final long startEpoch;
    private final long endEpoch;
    private final int channelId;
    private final String channelName;
    private final String showCategory;
    private final String description;
    private final String title;
    private final String thumbnail;
    private final String poster;
    private final String programId;
    private final String serialNo;
    private final String showtime;
    private final boolean catchupAvailable;

    public EpgProgram(long startEpoch, long endEpoch, int channelId,
                      String channelName, String showCategory,
                      String description, String title,
                      String thumbnail, String poster,
                      String programId, String serialNo,
                      String showtime, boolean catchupAvailable) {
        this.startEpoch = startEpoch;
        this.endEpoch = endEpoch;
        this.channelId = channelId;
        this.channelName = channelName == null ? "" : channelName;
        this.showCategory = showCategory == null ? "" : showCategory;
        this.description = description == null ? "" : description;
        this.title = title == null ? "" : title;
        this.thumbnail = thumbnail == null ? "" : thumbnail;
        this.poster = poster == null ? "" : poster;
        this.programId = programId == null ? "" : programId;
        this.serialNo = serialNo == null ? "" : serialNo;
        this.showtime = showtime == null ? "" : showtime;
        this.catchupAvailable = catchupAvailable;
    }

    public long getStartEpoch() { return startEpoch; }
    public long getEndEpoch() { return endEpoch; }
    public int getChannelId() { return channelId; }
    public String getChannelName() { return channelName; }
    public String getShowCategory() { return showCategory; }
    public String getDescription() { return description; }
    public String getTitle() { return title; }
    public String getThumbnail() { return thumbnail; }
    public String getPoster() { return poster; }
    public String getProgramId() { return programId; }
    public String getSerialNo() { return serialNo; }
    public String getShowtime() { return showtime; }
    public boolean isCatchupAvailable() { return catchupAvailable; }

    public boolean isCurrent(long nowSeconds) {
        return startEpoch <= nowSeconds && nowSeconds < endEpoch;
    }

    public boolean isFuture(long nowSeconds) {
        return startEpoch > nowSeconds;
    }

    public boolean isPast(long nowSeconds) {
        return endEpoch <= nowSeconds;
    }

    public static EpgProgram fromJson(JsonObject o, int fallbackChannelId) {
        long start = longValue(first(o, "startEpoch", "start_epoch", "startTime", "start", "begin"));
        long end = longValue(first(o, "endEpoch", "end_epoch", "endTime", "end", "stop"));
        int channelId = intValue(first(o, "channel_id", "channelId", "channelID"), fallbackChannelId);

        String channelName = stringValue(first(o, "channel_name", "channelName"));
        String category = stringValue(first(o, "showCategory", "show_category", "category", "categoryName"));
        String description = stringValue(first(o, "description", "desc"));
        String title = stringValue(first(o, "showname", "showName", "title", "programName", "programmeName", "name"));
        String thumbnail = stringValue(first(o, "episodeThumbnail", "episode_thumbnail", "thumbnail", "thumbnailUrl"));
        String poster = stringValue(first(o, "episodePoster", "episode_poster", "poster", "posterUrl"));
        String programId = stringValue(first(o, "programId", "program_id", "programmeId", "programme_id"));
        String serialNo = stringValue(first(o, "serialNo", "serialno", "srno", "serial_number"));
        String showtime = stringValue(first(o, "showtime", "showTime", "show_time"));
        boolean catchup = booleanValue(first(o, "isCatchupAvailable", "is_catchup_available", "catchupAvailable", "catchup"));

        // JioTV EPG epoch values are seconds. Be tolerant if a millisecond value
        // is returned by a future API variant.
        if (start > 100000000000L) start /= 1000L;
        if (end > 100000000000L) end /= 1000L;

        return new EpgProgram(start, end, channelId, channelName, category,
                description, title, thumbnail, poster, programId, serialNo,
                showtime, catchup);
    }

    private static JsonElement first(JsonObject o, String... names) {
        for (String n : names) if (o.has(n) && !o.get(n).isJsonNull()) return o.get(n);
        return null;
    }

    private static String stringValue(JsonElement e) {
        if (e == null || e.isJsonNull()) return "";
        try { return e.getAsString(); } catch (Exception ignored) { return ""; }
    }

    private static long longValue(JsonElement e) {
        if (e == null || e.isJsonNull()) return 0;
        try { return e.getAsLong(); }
        catch (Exception ignored) {
            try { return (long) Double.parseDouble(e.getAsString()); }
            catch (Exception ignored2) { return 0; }
        }
    }

    private static int intValue(JsonElement e, int fallback) {
        if (e == null || e.isJsonNull()) return fallback;
        try { return e.getAsInt(); }
        catch (Exception ignored) {
            try { return (int) Double.parseDouble(e.getAsString()); }
            catch (Exception ignored2) { return fallback; }
        }
    }

    private static boolean booleanValue(JsonElement e) {
        if (e == null || e.isJsonNull()) return false;
        try { return e.getAsBoolean(); }
        catch (Exception ignored) {
            String s = stringValue(e);
            return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
        }
    }
}
