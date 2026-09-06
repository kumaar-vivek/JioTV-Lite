package com.example.jiotvservice.model;

import android.util.Base64;

import com.example.jiotvservice.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class AuthModels {
    private AuthModels() {}

    public static final class OtpRequest {
        @SerializedName("number") public final String number;
        public OtpRequest(String mobileNumber) {
            this.number = encodeMobile(mobileNumber);
        }
    }

    public static final class OtpVerifyRequest {
        @SerializedName("number") public final String number;
        @SerializedName("otp") public final String otp;
        @SerializedName("deviceInfo") public final DeviceInfo deviceInfo;
        public OtpVerifyRequest(String mobileNumber, String otp, DeviceInfo deviceInfo) {
            this.number = encodeMobile(mobileNumber);
            this.otp = otp == null ? "" : otp.trim();
            this.deviceInfo = deviceInfo;
        }
    }

    public static final class DeviceInfo {
        @SerializedName("consumptionDeviceName") public final String consumptionDeviceName;
        @SerializedName("info") public final DeviceInfoDetails info;
        public DeviceInfo(String consumptionDeviceName, DeviceInfoDetails info) {
            this.consumptionDeviceName = consumptionDeviceName;
            this.info = info;
        }
    }

    public static final class DeviceInfoDetails {
        @SerializedName("type") public final String type;
        @SerializedName("platform") public final Platform platform;
        @SerializedName("androidId") public final String androidId;
        public DeviceInfoDetails(String type, Platform platform, String androidId) {
            this.type = type; this.platform = platform; this.androidId = androidId;
        }
    }

    public static final class Platform {
        @SerializedName("name") public final String name;
        @SerializedName("version") public final String version;
        public Platform(String name, String version) { this.name = name; this.version = version; }
    }

    public static final class RefreshAuthTokenRequest {
        @SerializedName("appName") public final String appName;
        @SerializedName("deviceId") public final String deviceId;
        @SerializedName("refreshToken") public final String refreshToken;
        public RefreshAuthTokenRequest(String appName, String deviceId, String refreshToken) {
            this.appName = appName; this.deviceId = deviceId; this.refreshToken = refreshToken;
        }
    }

    public static final class OtpSendResponse {
        @SerializedName("code") private String code;
        @SerializedName("message") private String message;
        @SerializedName("status") private String status;
        @SerializedName("result") private String result;
        public String getCode(){return code;} public String getMessage(){return message;} public String getStatus(){return status;} public String getResult(){return result;}
    }

    public static final class AuthResponse {
        @SerializedName("ssoToken") private String ssoToken;
        @SerializedName("ssotoken") private String ssoTokenLower;
        @SerializedName("authToken") private String authToken;
        @SerializedName("token") private String token;
        @SerializedName("accessToken") private String accessToken;
        @SerializedName("access_token") private String accessTokenSnake;
        @SerializedName("refreshToken") private String refreshToken;
        @SerializedName("lbCookie") private String lbCookie;
        @SerializedName("uniqueid") private String uniqueId;
        @SerializedName("uniqueId") private String uniqueIdCamel;
        @SerializedName("sessionAttributes") private SessionAttributes sessionAttributes;
        @SerializedName("message") private String message;

        public String getSsoToken(){
            if(ok(ssoToken)) return ssoToken;
            if(ok(ssoTokenLower)) return ssoTokenLower;
            return null;
        }

        public String getAccessToken(){
            if(ok(accessToken)) return accessToken;
            if(ok(accessTokenSnake)) return accessTokenSnake;
            if(ok(authToken)) return authToken;
            if(ok(token)) return token;
            return null;
        }

        public String getRefreshToken(){return refreshToken;}

        public String getLbCookie(){return lbCookie;}

        public String getUniqueId(){
            if(ok(uniqueId)) return uniqueId;
            if(ok(uniqueIdCamel)) return uniqueIdCamel;
            return sessionAttributes == null || sessionAttributes.user == null
                    ? null : sessionAttributes.user.unique;
        }

        public String getSubscriberId(){
            return sessionAttributes == null || sessionAttributes.user == null
                    ? null : sessionAttributes.user.subscriberId;
        }

        public String getMessage(){return message;}

        public boolean hasToken(){
            return ok(getSsoToken()) && ok(getAccessToken());
        }

        public static final class SessionAttributes {
            @SerializedName("user") User user;
        }

        public static final class User {
            @SerializedName("subscriberId") String subscriberId;
            @SerializedName("unique") String unique;
        }

        public static AuthResponse fromRawJson(String raw) throws JsonParseException {
            return new Gson().fromJson(unwrapJsonStringValue(parseJsonDocument(raw)), AuthResponse.class);
        }
    }

    public static final class PlaybackUrlResponse {
        @SerializedName("code") private String code;
        @SerializedName("message") private String message;
        @SerializedName("status") private String status;
        @SerializedName("error") private String error;
        @SerializedName("result") private String result;
        @SerializedName("bitrates") private Bitrates bitrates;
        @SerializedName("mpd") private Mpd mpd;
        @SerializedName("fallback_url") private String fallbackUrl;
        @SerializedName("adsConfig") private AdsConfig adsConfig;
        @SerializedName("jct") private String jct;
        @SerializedName("pxe") private String pxe;
        @SerializedName("st") private String st;
        @SerializedName("playbackToken") private String playbackToken;
        @SerializedName("algoNumber") private Integer algoNumber;
        @SerializedName("algoName") private String algoName;
        @SerializedName("streamKey") private String streamKey;
        @SerializedName("streamPolicy") private String streamPolicy;
        @SerializedName("expiryTime") private Long expiryTime;
        @SerializedName("nvAuthorizations") private String nvAuthorizations;
        @SerializedName("channel_id") private String channelId;
        @SerializedName("content_id") private String contentId;

        // Request context supplied by PlaybackRemoteDataSource. These are
        // deliberately transient so Gson never expects them in the response JSON.
        private transient String requestChannelId;
        private transient String requestSrno;

        public static final class Bitrates {
            @SerializedName("low") public String low;
            @SerializedName("medium") public String medium;
            @SerializedName("high") public String high;
            @SerializedName("auto") public String auto;
        }

        public static final class Mpd {
            @SerializedName("result") public String result;
            @SerializedName("key") public String key;
            @SerializedName("bitrates") public Bitrates bitrates;
            @SerializedName("auto") public String auto;
        }

        public static final class AdsConfig {
            @SerializedName("adsSpot") public AdsSpot adsSpot;
        }

        public static final class AdsSpot {
            @SerializedName("preroll") public String preroll;
            @SerializedName("midroll") public String midroll;
        }

        public String getResult() { return result; }
        public String getMpdResult() { 
            if (mpd != null && ok(mpd.auto)) return mpd.auto;
            return mpd != null ? mpd.result : null; 
        }
        public String getMpdKey() { return mpd != null ? mpd.key : null; }
        public String getFallbackUrl() { return fallbackUrl; }
        public String getNvAuthorizations() { return nvAuthorizations; }
        public String getChannelIdStr() { return channelId; }
        public String getContentIdStr() { return contentId; }
        public String getRequestChannelId() {
            return requestChannelId != null ? requestChannelId : channelId;
        }
        public String getRequestSrno() { return requestSrno; }
        public void setRequestContext(String channelId, String srno) {
            this.requestChannelId = channelId;
            this.requestSrno = srno;
        }
        public String getPlaybackToken() { return playbackToken; }

        public List<String> getPlaybackCandidates() {
            java.util.LinkedHashSet<String> uniqueCandidates = new java.util.LinkedHashSet<>();
            
            // Tier 1: MPD (WDVLive) - Primary choice
            if (mpd != null && ok(mpd.auto)) uniqueCandidates.add(mpd.auto);
            if (mpd != null && ok(mpd.result)) uniqueCandidates.add(mpd.result);
            if (mpd != null && mpd.bitrates != null && ok(mpd.bitrates.auto)) {
                uniqueCandidates.add(mpd.bitrates.auto);
            }
            
            // Tier 2: HLS (Fallback) - Secondary choice
            if (bitrates != null && ok(bitrates.auto)) uniqueCandidates.add(bitrates.auto);
            if (ok(result)) uniqueCandidates.add(result);

            // Tier 3: Specific High Quality Renditions
            if (mpd != null && mpd.bitrates != null && ok(mpd.bitrates.high)) {
                uniqueCandidates.add(mpd.bitrates.high);
            }
            if (bitrates != null && ok(bitrates.high)) {
                uniqueCandidates.add(bitrates.high);
            }
            
            // Tier 4: Global Fallback
            if (ok(fallbackUrl)) uniqueCandidates.add(fallbackUrl);
            
            return new ArrayList<>(uniqueCandidates);
        }

        public String getPreferredResult() {
            List<String> candidates = getPlaybackCandidates();
            return candidates.isEmpty() ? null : candidates.get(0);
        }

        public String getMessage() {
            if (ok(message)) return message;
            if (ok(error)) return error;
            if (ok(status)) return status;
            if (ok(code)) return code;
            return null;
        }

        public static PlaybackUrlResponse fromRawJson(String raw) throws JsonParseException {
            Logger.p("AuthModelTag",raw); //vivek
            return new Gson().fromJson(parseJsonDocument(raw), PlaybackUrlResponse.class);
        }
    }



    /** Response shape from getMobileChannelList: {code,message,result:[...]} */
    public static final class ChannelListResponse {
        @SerializedName("code") private int code;
        @SerializedName("message") private String message;
        @SerializedName("result") private List<com.example.jiotvservice.model.Channel> result;

        private static final Gson RAW_RESPONSE_GSON = new GsonBuilder()
                .registerTypeAdapter(ChannelListResponse.class, new Deserializer())
                .registerTypeAdapter(Channel.class, new Channel.Deserializer())
                .setLenient()
                .create();

        public int getCode() { return code; }
        public String getMessage() { return message; }
        public List<com.example.jiotvservice.model.Channel> getResult() { return result; }

        public static ChannelListResponse fromRawJson(String raw) throws JsonParseException {
            JsonElement element = parseJsonDocument(raw);
            return RAW_RESPONSE_GSON.fromJson(element, ChannelListResponse.class);
        }

        public static final class Deserializer implements JsonDeserializer<ChannelListResponse> {
            private static final Type CHANNEL_LIST_TYPE =
                    new TypeToken<List<Channel>>() {}.getType();

            @Override
            public ChannelListResponse deserialize(JsonElement json, Type typeOfT,
                    JsonDeserializationContext context) throws JsonParseException {
                ChannelListResponse response = new ChannelListResponse();
                JsonElement body = unwrapJsonString(json);

                if (body == null || body.isJsonNull()) {
                    return response;
                }

                if (body.isJsonArray()) {
                    response.code = 200;
                    response.result = context.deserialize(body, CHANNEL_LIST_TYPE);
                    return response;
                }

                if (body.isJsonObject()) {
                    JsonObject object = body.getAsJsonObject();
                    response.code = intOrDefault(object.get("code"), 0);
                    response.message = stringOrNull(object.get("message"));
                    response.result = deserializeChannelList(context, firstPresent(object,
                            "result", "channels", "channelList", "items", "data", "list"));
                    return response;
                }

                response.message = stringOrNull(body);
                return response;
            }

            private static List<Channel> deserializeChannelList(
                    JsonDeserializationContext context, JsonElement element) {
                JsonElement result = unwrapJsonString(element);

                if (result == null || result.isJsonNull()) {
                    return null;
                }

                if (result.isJsonArray()) {
                    return context.deserialize(result, CHANNEL_LIST_TYPE);
                }

                if (result.isJsonObject()) {
                    JsonObject object = result.getAsJsonObject();
                    JsonElement nested = firstPresent(object,
                            "channels", "channelList", "items", "data", "list");
                    if (nested != null) {
                        return deserializeChannelList(context, nested);
                    }
                }

                return null;
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
                        current = parseJsonDocument(trimmed);
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

            private static int intOrDefault(JsonElement element, int defaultValue) {
                if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
                    return defaultValue;
                }
                try {
                    return element.getAsInt();
                } catch (RuntimeException ignored) {
                    return defaultValue;
                }
            }

            private static String stringOrNull(JsonElement element) {
                if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
                    return null;
                }
                try {
                    return element.getAsString();
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
    }

    public static JsonElement parseJsonDocument(String raw) throws JsonParseException {
        if (raw == null) {
            throw new JsonParseException("Empty response body");
        }

        String value = stripBom(raw.trim());
        if (value.isEmpty()) {
            throw new JsonParseException("Empty response body");
        }

        return JsonParser.parseString(value);
    }

    public static String serverMessageFromRaw(String raw, String fallback) {
        if (!ok(raw)) {
            return fallback;
        }

        try {
            PlaybackUrlResponse response = PlaybackUrlResponse.fromRawJson(raw);
            String message = response.getMessage();
            if (ok(message)) {
                return message;
            }
            if (ok(response.getResult())) {
                return response.getResult();
            }
        } catch (RuntimeException ignored) {}

        String trimmed = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (trimmed.length() > 240) {
            return trimmed.substring(0, 240) + "...";
        }
        return ok(trimmed) ? trimmed : fallback;
    }

    private static JsonElement unwrapJsonStringValue(JsonElement element) {
        JsonElement current = element;
        for (int i = 0; i < 3; i++) {
            if (current == null || current.isJsonNull() ||
                    !current.isJsonPrimitive() ||
                    !current.getAsJsonPrimitive().isString()) {
                return current;
            }

            String value = current.getAsString();
            if (!ok(value)) {
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
                current = parseJsonDocument(trimmed);
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

    public static String normalizeMobile(String value){
        if(value==null)return ""; String n=value.trim().replace(" ","").replace("-","");
        if(n.startsWith("+91"))return n; if(n.startsWith("91")&&n.length()==12)return "+"+n; return "+91"+n;
    }
    public static String encodeMobile(String mobileNumber){
        return Base64.encodeToString(normalizeMobile(mobileNumber).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }
    private static boolean ok(String s){return s!=null&&!s.isEmpty();}
}
