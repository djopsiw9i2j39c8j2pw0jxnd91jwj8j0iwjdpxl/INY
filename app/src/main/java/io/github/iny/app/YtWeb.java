package io.github.iny.app;

import android.webkit.CookieManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tài khoản YouTube bằng phiên đăng nhập (cookie) trong WebView, gọi InnerTube. Không cần Google Cloud. */
final class YtWeb {
    private YtWeb() {}
    private static final String ORIGIN = "https://www.youtube.com";
    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final Set<String> OPS = new HashSet<>(Arrays.asList("rating", "myChannel", "subscriptions", "subscription", "subscribe",
        "unsubscribe", "rate", "liked", "playlists", "playlistVideos", "createPlaylist", "savePlaylist", "postComment"));
    private static volatile String version = "2.20250603.01.00";
    private static volatile String apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    private static volatile boolean cfgLoaded;

    static boolean handles(String op) { return OPS.contains(op); }

    // ---- cookie / auth ----
    static String cookies() {
        try { String c = CookieManager.getInstance().getCookie(ORIGIN); return c == null ? "" : c; } catch (Exception e) { return ""; }
    }
    static String cookie(String name) {
        for (String p : cookies().split(";")) {
            String t = p.trim();
            if (t.startsWith(name + "=")) return t.substring(name.length() + 1);
        }
        return "";
    }
    static boolean loggedIn() { return !cookie("SAPISID").isEmpty() || !cookie("__Secure-3PAPISID").isEmpty(); }
    static void logout() {
        try { CookieManager cm = CookieManager.getInstance(); cm.removeAllCookies(null); cm.flush(); } catch (Exception ignored) { }
    }
    private static String hash(String label, String sid, long ts) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest((ts + " " + sid + " " + ORIGIN).getBytes(StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        for (byte v : d) b.append(String.format(Locale.US, "%02x", v & 255));
        return label + " " + ts + "_" + b;
    }
    private static String authorization() throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String sid = cookie("SAPISID"), s1 = cookie("__Secure-1PAPISID"), s3 = cookie("__Secure-3PAPISID");
        if (sid.isEmpty()) sid = s3;
        List<String> parts = new ArrayList<>();
        if (!sid.isEmpty()) parts.add(hash("SAPISIDHASH", sid, ts));
        if (!s1.isEmpty()) parts.add(hash("SAPISID1PHASH", s1, ts));
        if (!s3.isEmpty()) parts.add(hash("SAPISID3PHASH", s3, ts));
        return String.join(" ", parts);
    }

    // ---- http ----
    private static byte[] read(InputStream in, int max) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) != -1 && o.size() < max) o.write(buf, 0, n);
        return o.toByteArray();
    }
    private static void loadConfig() {
        if (cfgLoaded) return;
        cfgLoaded = true;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(ORIGIN + "/").openConnection();
            c.setConnectTimeout(12000); c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", UA); c.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8");
            try (InputStream in = c.getInputStream()) {
                String html = new String(read(in, 900_000), StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("\"INNERTUBE_CONTEXT_CLIENT_VERSION\":\"([0-9.]+)\"").matcher(html);
                if (m.find()) version = m.group(1);
                m = Pattern.compile("\"INNERTUBE_API_KEY\":\"([A-Za-z0-9_-]+)\"").matcher(html);
                if (m.find()) apiKey = m.group(1);
            } finally { c.disconnect(); }
        } catch (Exception ignored) { }
    }
    private static JSONObject post(String endpoint, JSONObject body, boolean auth) throws Exception {
        loadConfig();
        boolean in = loggedIn();
        if (auth && !in) throw new IOException("AUTH_REQUIRED: Đăng nhập YouTube trước khi dùng tính năng này.");
        body.put("context", new JSONObject()
            .put("client", new JSONObject().put("clientName", "WEB").put("clientVersion", version).put("hl", "vi").put("gl", "VN"))
            .put("user", new JSONObject().put("lockedSafetyMode", false)));
        HttpURLConnection c = (HttpURLConnection) new URL(ORIGIN + "/youtubei/v1/" + endpoint + "?prettyPrint=false&key=" + apiKey).openConnection();
        c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8");
        c.setRequestProperty("Origin", ORIGIN); c.setRequestProperty("Referer", ORIGIN + "/");
        c.setRequestProperty("X-Youtube-Client-Name", "1"); c.setRequestProperty("X-Youtube-Client-Version", version);
        if (in) {
            c.setRequestProperty("Cookie", cookies());
            c.setRequestProperty("Authorization", authorization());
            c.setRequestProperty("X-Origin", ORIGIN); c.setRequestProperty("X-Goog-AuthUser", "0");
        }
        try {
            try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = c.getResponseCode();
            InputStream s = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = s == null ? "" : new String(read(s, 8_000_000), StandardCharsets.UTF_8);
            if (code == 401) throw new IOException("AUTH_REQUIRED: Phiên đăng nhập YouTube đã hết hạn. Hãy đăng nhập lại.");
            if (code >= 400) throw new IOException("YouTube từ chối (HTTP " + code + ")" + reason(text));
            return text.isEmpty() ? new JSONObject() : new JSONObject(text);
        } finally { c.disconnect(); }
    }

    /** Lý do YouTube trả kèm khi báo lỗi, để người dùng biết lỗi thật chứ không chỉ mã HTTP. */
    private static String reason(String body) {
        try {
            String m = new JSONObject(body).getJSONObject("error").optString("message");
            return m.isEmpty() ? "." : ": " + m;
        } catch (Exception e) { return "."; }
    }

    // ---- json helpers ----
    private static Object find(Object n, String key) {
        if (n instanceof JSONObject) {
            JSONObject o = (JSONObject) n;
            if (o.has(key)) return o.opt(key);
            Iterator<String> it = o.keys();
            while (it.hasNext()) { Object r = find(o.opt(it.next()), key); if (r != null) return r; }
        } else if (n instanceof JSONArray) {
            JSONArray a = (JSONArray) n;
            for (int i = 0; i < a.length(); i++) { Object r = find(a.opt(i), key); if (r != null) return r; }
        }
        return null;
    }
    private static String findStr(Object n, String key) {
        if (n instanceof JSONObject) {
            JSONObject o = (JSONObject) n;
            if (o.opt(key) instanceof String) return (String) o.opt(key);
            Iterator<String> it = o.keys();
            while (it.hasNext()) { String r = findStr(o.opt(it.next()), key); if (r != null) return r; }
        } else if (n instanceof JSONArray) {
            JSONArray a = (JSONArray) n;
            for (int i = 0; i < a.length(); i++) { String r = findStr(a.opt(i), key); if (r != null) return r; }
        }
        return null;
    }
    private static void collect(Object n, String key, List<JSONObject> out) {
        if (n instanceof JSONObject) {
            JSONObject o = (JSONObject) n;
            if (o.opt(key) instanceof JSONObject) out.add(o.optJSONObject(key));
            Iterator<String> it = o.keys();
            while (it.hasNext()) collect(o.opt(it.next()), key, out);
        } else if (n instanceof JSONArray) {
            JSONArray a = (JSONArray) n;
            for (int i = 0; i < a.length(); i++) collect(a.opt(i), key, out);
        }
    }
    private static String text(Object t) {
        if (t instanceof String) return (String) t;
        if (!(t instanceof JSONObject)) return "";
        JSONObject o = (JSONObject) t;
        if (o.has("simpleText")) return o.optString("simpleText");
        if (o.has("content")) return o.optString("content");
        JSONArray runs = o.optJSONArray("runs");
        StringBuilder b = new StringBuilder();
        if (runs != null) for (int i = 0; i < runs.length(); i++) b.append(runs.optJSONObject(i) == null ? "" : runs.optJSONObject(i).optString("text"));
        return b.toString();
    }
    private static String image(Object n) {
        for (String key : new String[]{"thumbnails", "sources"}) {
            Object t = find(n, key);
            if (t instanceof JSONArray && ((JSONArray) t).length() > 0) {
                JSONArray a = (JSONArray) t;
                JSONObject last = a.optJSONObject(a.length() - 1);
                String u = last == null ? "" : last.optString("url");
                if (u.startsWith("//")) u = "https:" + u;
                if (u.startsWith("http")) return u;
            }
        }
        return "";
    }
    private static String nextToken(JSONObject r) {
        List<JSONObject> l = new ArrayList<>();
        collect(r, "continuationItemRenderer", l);
        return l.isEmpty() ? "" : nz(findStr(l.get(l.size() - 1), "token"));
    }
    private static String nz(String s) { return s == null ? "" : s; }
    private static JSONObject list(JSONArray items, String token) throws Exception {
        JSONObject o = new JSONObject().put("items", items);
        if (token != null && !token.isEmpty()) o.put("nextPageToken", token);
        return o;
    }
    private static JSONObject browse(String id, String token) throws Exception {
        JSONObject b = new JSONObject();
        if (token != null && !token.isEmpty()) b.put("continuation", token); else b.put("browseId", id);
        return post("browse", b, !id.startsWith("VLPL") && !id.startsWith("VLOL") && !id.startsWith("VLRD"));
    }

    // ---- operations ----
    static JSONObject handle(String op, JSONObject a) throws Exception {
        switch (op) {
            case "rating": {
                String s = findStr(post("next", new JSONObject().put("videoId", a.getString("id")), true), "likeStatus");
                String v = "LIKE".equals(s) ? "like" : "DISLIKE".equals(s) ? "dislike" : "none";
                return new JSONObject().put("items", new JSONArray().put(new JSONObject().put("rating", v)));
            }
            case "rate": {
                String r = a.optString("rating", "like"), vid = a.getString("id");
                String ep = "like".equals(r) ? "like/like" : "dislike".equals(r) ? "like/dislike" : "like/removelike";
                post(ep, new JSONObject().put("target", new JSONObject().put("videoId", vid)), true);
                return new JSONObject();
            }
            case "subscription": {
                String vid = a.optString("videoId"), ch = a.getString("id");
                JSONArray items = new JSONArray();
                if (!vid.isEmpty()) {
                    List<JSONObject> btns = new ArrayList<>();
                    collect(post("next", new JSONObject().put("videoId", vid), true), "subscribeButtonRenderer", btns);
                    for (JSONObject b : btns) {
                        if (!b.has("subscribed")) continue;
                        if (b.optBoolean("subscribed")) items.put(new JSONObject().put("id", ch));
                        break;
                    }
                }
                return new JSONObject().put("items", items);
            }
            case "subscribe":
                post("subscription/subscribe", new JSONObject().put("channelIds", new JSONArray().put(a.getString("id"))).put("params", "EgIIAhgA"), true);
                return new JSONObject().put("id", a.getString("id"));
            case "unsubscribe":
                post("subscription/unsubscribe", new JSONObject().put("channelIds", new JSONArray().put(a.getString("id"))).put("params", "CgIIAhgA"), true);
                return new JSONObject();
            case "myChannel": {
                JSONObject r = post("account/account_menu", new JSONObject(), true);
                Object h = find(r, "activeAccountHeaderRenderer");
                String name = h instanceof JSONObject ? text(((JSONObject) h).opt("accountName")) : "";
                String pic = h instanceof JSONObject ? image(h) : "";
                if (name.isEmpty()) name = "Tài khoản YouTube";
                JSONObject snippet = new JSONObject().put("title", name).put("thumbnails", new JSONObject().put("default", new JSONObject().put("url", pic)));
                return new JSONObject().put("items", new JSONArray().put(new JSONObject().put("snippet", snippet)));
            }
            case "subscriptions": {
                JSONObject r = browse("FEchannels", a.optString("pageToken"));
                JSONArray items = new JSONArray();
                Set<String> seen = new HashSet<>();
                List<JSONObject> l = new ArrayList<>();
                collect(r, "channelRenderer", l); collect(r, "gridChannelRenderer", l);
                for (JSONObject c : l) addChannel(items, seen, nz(c.optString("channelId")), text(c.opt("title")), image(c.opt("thumbnail")));
                List<JSONObject> lv = new ArrayList<>();
                collect(r, "lockupViewModel", lv);
                for (JSONObject c : lv) {
                    String id = nz(c.optString("contentId"));
                    if (!id.startsWith("UC")) continue;
                    Object md = find(c, "lockupMetadataViewModel");
                    addChannel(items, seen, id, md instanceof JSONObject ? text(((JSONObject) md).opt("title")) : "", image(c.opt("contentImage")));
                }
                return list(items, nextToken(r));
            }
            case "liked":
            case "playlistVideos": {
                String id = "liked".equals(op) ? "VLLL" : "VL" + a.getString("id");
                JSONObject r = browse(id, a.optString("pageToken"));
                JSONArray items = new JSONArray();
                List<JSONObject> l = new ArrayList<>();
                collect(r, "playlistVideoRenderer", l);
                for (JSONObject v : l) {
                    String vid = nz(v.optString("videoId"));
                    if (vid.length() != 11) continue;
                    JSONObject by = v.optJSONObject("shortBylineText");
                    items.put(new JSONObject().put("kind", "np").put("id", vid).put("title", text(v.opt("title"))).put("channel", text(by))
                        .put("channelId", nz(findStr(by, "browseId"))).put("thumb", "https://i.ytimg.com/vi/" + vid + "/hqdefault.jpg")
                        .put("duration", parseInt(v.optString("lengthSeconds"))).put("publishedText", "").put("short", false).put("live", false));
                }
                return list(items, nextToken(r));
            }
            case "playlists": {
                JSONObject r = browse("FEplaylist_aggregation", a.optString("pageToken"));
                JSONArray items = new JSONArray();
                Set<String> seen = new HashSet<>();
                List<JSONObject> l = new ArrayList<>();
                collect(r, "gridPlaylistRenderer", l); collect(r, "playlistRenderer", l);
                for (JSONObject p : l) addPlaylist(items, seen, nz(p.optString("playlistId")), text(p.opt("title")), digits(text(p.opt("videoCountText"))));
                List<JSONObject> lv = new ArrayList<>();
                collect(r, "lockupViewModel", lv);
                for (JSONObject p : lv) {
                    if (!"LOCKUP_CONTENT_TYPE_PLAYLIST".equals(p.optString("contentType"))) continue;
                    Object md = find(p, "lockupMetadataViewModel");
                    addPlaylist(items, seen, nz(p.optString("contentId")), md instanceof JSONObject ? text(((JSONObject) md).opt("title")) : "", 0);
                }
                return list(items, nextToken(r));
            }
            case "createPlaylist": {
                JSONObject r = post("playlist/create", new JSONObject().put("title", a.getString("title")).put("privacyStatus", "PRIVATE").put("videoIds", new JSONArray()), true);
                return new JSONObject().put("id", nz(r.optString("playlistId")));
            }
            case "savePlaylist":
                post("browse/edit_playlist", new JSONObject().put("playlistId", a.getString("playlistId")).put("actions", new JSONArray()
                    .put(new JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", a.getString("videoId")))), true);
                return new JSONObject();
            case "postComment": {
                JSONObject first = post("next", new JSONObject().put("videoId", a.getString("id")), true);
                String token = null;
                List<JSONObject> secs = new ArrayList<>();
                collect(first, "itemSectionRenderer", secs);
                for (JSONObject s : secs) {
                    String sid = s.optString("sectionIdentifier") + s.optString("targetId");
                    if (sid.contains("comment")) { token = findStr(s, "token"); if (token != null) break; }
                }
                if (token == null) throw new IOException("Video này tắt bình luận hoặc YouTube chưa trả về khung bình luận.");
                String params = findStr(post("next", new JSONObject().put("continuation", token), true), "createCommentParams");
                if (params == null) throw new IOException("Không lấy được quyền bình luận cho video này.");
                post("comment/create_comment", new JSONObject().put("commentText", a.getString("text")).put("createCommentParams", params), true);
                return new JSONObject();
            }
            default: throw new IOException("Thao tác chưa được hỗ trợ: " + op);
        }
    }
    private static void addChannel(JSONArray items, Set<String> seen, String id, String title, String pic) throws Exception {
        if (id.isEmpty() || !seen.add(id)) return;
        items.put(new JSONObject().put("snippet", new JSONObject().put("title", title).put("description", "")
            .put("thumbnails", new JSONObject().put("default", new JSONObject().put("url", pic))).put("resourceId", new JSONObject().put("channelId", id))));
    }
    private static void addPlaylist(JSONArray items, Set<String> seen, String id, String title, int count) throws Exception {
        if (id.isEmpty() || !seen.add(id)) return;
        items.put(new JSONObject().put("id", id).put("snippet", new JSONObject().put("title", title)).put("contentDetails", new JSONObject().put("itemCount", count)));
    }
    private static int parseInt(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; } }
    private static int digits(String s) { return parseInt(s.replaceAll("[^0-9]", "")); }
}
