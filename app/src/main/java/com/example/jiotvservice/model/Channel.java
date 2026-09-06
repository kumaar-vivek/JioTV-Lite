package com.example.jiotvservice.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Type;

public class Channel {
    @SerializedName("channel_id") private int channelId;
    @SerializedName("channel_name") private String channelName;
    @SerializedName("channel_number") private int channelNumber;
    @SerializedName("stbChannelNumber") private int stbChannelNumber;
    @SerializedName("channel_order") private String channelOrder;
    @SerializedName("logoUrl") private String logoUrl;
    @SerializedName("channelCategory") private String channelCategory;
    @SerializedName("channelLanguage") private String channelLanguage;
    @SerializedName("channelLanguageId") private int channelLanguageId;
    @SerializedName("channelCategoryId") private int channelCategoryId;
    @SerializedName("isHD") private boolean hd;
    @SerializedName("isCatchupAvailable") private boolean catchupAvailable;
    @SerializedName("is_premium") private boolean premium;
    @SerializedName("business_type") private String businessType;
    @SerializedName("plan_type") private String planType;
    private Boolean subscribed;

    // Cache for pre-fetched playback info
    private String cachedUrl;
    private String cachedLicenseUrl;
    private java.util.List<String> cachedCandidates;
    private long cacheTime;

    public int getChannelId() {
        return channelId;
    }

    public String getChannelName() {
        return channelName == null ? "" : channelName;
    }

    /**
     * The supplied successful JioTV response uses stbChannelNumber as the
     * user-visible channel number. Fall back to channel_number for variants.
     */
    public int getChannelNumber() {
        return stbChannelNumber != 0 ? stbChannelNumber : channelNumber;
    }

    public String getLogoUrl() { return logoUrl; }
    public String getChannelCategory() { return channelCategory == null ? "" : channelCategory; }
    public String getChannelLanguage() { return channelLanguage == null ? "" : channelLanguage; }
    public int getChannelLanguageId() { return channelLanguageId; }
    public int getChannelCategoryId() { return channelCategoryId; }
    public boolean isHd() { return hd; }
    public boolean isCatchupAvailable() { return catchupAvailable; }
    public boolean isPremium() { return premium; }
    public String getBusinessType() { return businessType == null ? "" : businessType; }
    public String getPlanType() { return planType == null ? "" : planType; }

    public boolean isSubscribed() {
        if (subscribed != null) {
            return subscribed;
        }
        return !"premium".equalsIgnoreCase(getBusinessType());
    }

    public void setCachedPlaybackInfo(String url, String licenseUrl, java.util.List<String> candidates) {
        this.cachedUrl = url;
        this.cachedLicenseUrl = licenseUrl;
        this.cachedCandidates = candidates;
        this.cacheTime = System.currentTimeMillis();
    }

    public String getCachedUrl() { return cachedUrl; }
    public String getCachedLicenseUrl() { return cachedLicenseUrl; }
    public java.util.List<String> getCachedCandidates() { return cachedCandidates; }
    
    public boolean isCacheValid() {
        if (cachedUrl == null) return false;
        // Typical JioTV URL tokens expire in 10-15 minutes. 
        // We'll consider it valid for 8 minutes to be safe.
        return (System.currentTimeMillis() - cacheTime) < (8 * 60 * 1000);
    }

    public static final class Deserializer implements JsonDeserializer<Channel> {
        @Override
        public Channel deserialize(JsonElement json, Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            Channel channel = new Channel();
            JsonElement body = unwrapJsonString(json);

            if (body == null || body.isJsonNull() || !body.isJsonObject()) {
                return channel;
            }

            JsonObject object = body.getAsJsonObject();
            channel.channelId = intFromAliases(object, "channel_id", "channelId", "id");
            channel.channelName = stringFromAliases(object,
                    "channel_name", "channelName", "name");
            channel.channelNumber = intFromAliases(object,
                    "channel_number", "channelNumber", "number");
            channel.stbChannelNumber = intFromAliases(object,
                    "stbChannelNumber", "stb_channel_number", "stbChannelNo");
            channel.channelOrder = stringFromAliases(object,
                    "channel_order", "channelOrder");
            channel.logoUrl = stringFromAliases(object,
                    "logoUrl", "logo_url", "logo");
            channel.channelCategory = stringFromAliases(object,
                    "channelCategory", "channel_category", "category");
            channel.channelLanguage = stringFromAliases(object,
                    "channelLanguage", "channel_language", "language");
            channel.channelLanguageId = intFromAliases(object,
                    "channelLanguageId", "channel_language_id", "languageId");
            channel.channelCategoryId = intFromAliases(object,
                    "channelCategoryId", "channel_category_id", "categoryId");
            channel.hd = booleanFromAliases(object, "isHD", "isHd", "hd");
            channel.catchupAvailable = booleanFromAliases(object,
                    "isCatchupAvailable", "is_catchup_available", "catchupAvailable", "catchup");
            channel.premium = booleanFromAliases(object,
                    "is_premium", "isPremium", "premium");
            channel.businessType = stringFromAliases(object,
                    "business_type", "businessType");
            channel.planType = stringFromAliases(object,
                    "plan_type", "planType");
            channel.subscribed = nullableBooleanFromAliases(object,
                    "isSubscribed", "is_subscribed", "subscribed",
                    "isEntitled", "entitled", "isPlayable", "playable");
            return channel;
        }

        private static int intFromAliases(JsonObject object, String... names) {
            JsonElement element = firstPresent(object, names);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
                return 0;
            }

            try {
                return element.getAsInt();
            } catch (RuntimeException ignored) {
                String value = stringOrEmpty(element).trim();
                if (value.isEmpty()) {
                    return 0;
                }
                try {
                    return (int) Double.parseDouble(value);
                } catch (RuntimeException ignoredAgain) {
                    return 0;
                }
            }
        }

        private static boolean booleanFromAliases(JsonObject object, String... names) {
            JsonElement element = firstPresent(object, names);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
                return false;
            }

            try {
                return element.getAsBoolean();
            } catch (RuntimeException ignored) {
                String value = stringOrEmpty(element).trim();
                return "1".equals(value) ||
                        "true".equalsIgnoreCase(value) ||
                        "yes".equalsIgnoreCase(value);
            }
        }

        private static Boolean nullableBooleanFromAliases(JsonObject object, String... names) {
            JsonElement element = firstPresent(object, names);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
                return null;
            }

            try {
                return element.getAsBoolean();
            } catch (RuntimeException ignored) {
                String value = stringOrEmpty(element).trim();
                if (value.isEmpty()) {
                    return null;
                }
                if ("1".equals(value) ||
                        "true".equalsIgnoreCase(value) ||
                        "yes".equalsIgnoreCase(value)) {
                    return true;
                }
                if ("0".equals(value) ||
                        "false".equalsIgnoreCase(value) ||
                        "no".equalsIgnoreCase(value)) {
                    return false;
                }
                return null;
            }
        }

        private static String stringFromAliases(JsonObject object, String... names) {
            JsonElement element = firstPresent(object, names);
            return stringOrEmpty(element);
        }

        private static String stringOrEmpty(JsonElement element) {
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
                return "";
            }
            try {
                return element.getAsString();
            } catch (RuntimeException ignored) {
                return "";
            }
        }

        private static JsonElement firstPresent(JsonObject object, String... names) {
            for (String name : names) {
                if (object.has(name)) {
                    return object.get(name);
                }
            }
            return null;
        }

        private static JsonElement unwrapJsonString(JsonElement element) {
            JsonElement current = element;

            for (int i = 0; i < 3; i++) {
                if (current == null || current.isJsonNull() ||
                        !current.isJsonPrimitive() ||
                        !current.getAsJsonPrimitive().isString()) {
                    return current;
                }

                String value = current.getAsString();
                if (value == null) {
                    return current;
                }

                String trimmed = stripBom(value.trim());
                if (trimmed.isEmpty()) {
                    return current;
                }

                char first = trimmed.charAt(0);
                if (first != '{' && first != '[' && first != '"') {
                    return current;
                }

                try {
                    current = AuthModels.parseJsonDocument(trimmed);
                } catch (RuntimeException ignored) {
                    return current;
                }
            }

            return current;
        }

        private static String stripBom(String value) {
            return !value.isEmpty() && value.charAt(0) == '\uFEFF'
                    ? value.substring(1).trim() : value;
        }
    }
}
