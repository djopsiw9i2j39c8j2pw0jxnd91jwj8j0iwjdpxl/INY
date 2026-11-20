package io.github.iny.app;

import android.text.Html;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException;
import org.schabi.newpipe.extractor.exceptions.PaidContentException;
import org.schabi.newpipe.extractor.exceptions.PrivateContentException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import java.io.IOException;
import java.util.*;

/** Toàn bộ phần lấy dữ liệu YouTube không cần API key (NewPipe Extractor). */
final class Yt {
    private static final StreamingService S = ServiceList.YouTube;
    private static boolean ready;

    private static final class Cached { StreamInfo info; long at; }
    private static final class Pick { String video, audio; boolean hls; int height; }

    private static final Map<String, Object[]> pages = lru(150);
    private static final Map<String, Object[]> replies = lru(400);
    private static final Map<String, Cached> infos = lru(6);

    private static <V> Map<String, V> lru(final int max) {
        return Collections.synchronizedMap(new LinkedHashMap<String, V>() {
            @Override protected boolean removeEldestEntry(Map.Entry<String, V> e) { return size() > max; }
        });
    }

    static synchronized void init() {
        if (ready) return;
        NewPipe.init(new NpDownloader(), new Localization("vi", "VN"), new ContentCountry("VN"));
        ready = true;
    }

    // ---------- tiện ích ----------
    private static String nz(String s) { return s == null ? "" : s; }

    private static String keep(String kind, Object ctx, Page next) {
        String t = UUID.randomUUID().toString();
        pages.put(t, new Object[]{kind, ctx, next});
        return t;
    }

    private static JSONObject list(JSONArray items, String token) throws JSONException {
        JSONObject o = new JSONObject().put("items", items);
        if (token != null) o.put("nextPageToken", token);
        return o;
    }

    private static String text(Description d) {
        if (d == null || d.content() == null) return "";
        String c = d.content();
        return d.type() == Description.Type.HTML ? Html.fromHtml(c, Html.FROM_HTML_MODE_LEGACY).toString() : c;
    }

    private static String channelId(String url) {
        if (url == null || url.isEmpty()) return "";
        try { return S.getChannelLHFactory().getId(url); } catch (Exception e) { return ""; }
    }

    private static String bestImage(List<Image> l) {
        if (l == null || l.isEmpty()) return "";
        Image b = l.get(0);
        for (Image i : l) if (i.getHeight() > b.getHeight()) b = i;
        return b.getUrl();
    }

    private static JSONObject video(StreamInfoItem s) throws Exception {
        String id = S.getStreamLHFactory().getId(s.getUrl());
        JSONObject o = new JSONObject().put("kind", "np").put("id", id).put("title", nz(s.getName()))
            .put("channel", nz(s.getUploaderName())).put("channelId", channelId(s.getUploaderUrl()))
            .put("thumb", bestImage(s.getThumbnails())).put("short", s.isShortFormContent())
            .put("publishedText", nz(s.getTextualUploadDate())).put("duration", s.getDuration())
            .put("live", s.getStreamType() == StreamType.LIVE_STREAM);
        if (s.getViewCount() >= 0) o.put("views", s.getViewCount());
        return o;
    }

    /** Shorts dựa vào metadata của nguồn, không suy đoán từ thời lượng. */
    private static JSONArray videos(List<? extends InfoItem> src, String mode) {
        JSONArray a = new JSONArray();
        for (InfoItem i : src) {
            if (!(i instanceof StreamInfoItem)) continue;
            StreamInfoItem s = (StreamInfoItem) i;
            long d = s.getDuration();
            if ("short".equals(mode) && !s.isShortFormContent()) continue;
            if ("long".equals(mode) && d < 1200) continue;
            try { a.put(video(s)); } catch (Exception ignored) { }
        }
        return a;
    }

    private static void addAll(JSONArray to, JSONArray from) throws JSONException {
        for (int i = 0; i < from.length(); i++) to.put(from.get(i));
    }

    // ---------- trang chủ / tìm kiếm ----------
    static JSONObject home(String token) throws Exception {
        if (!token.isEmpty()) return more(token, "");
        try {
            KioskExtractor<? extends InfoItem> ex = S.getKioskList().getDefaultKioskExtractor();
            ex.fetchPage();
            ListExtractor.InfoItemsPage<? extends InfoItem> p = ex.getInitialPage();
            JSONArray a = videos(p.getItems(), "");
            if (a.length() > 0) return list(a, p.getNextPage() != null ? keep("kiosk", ex, p.getNextPage()) : null);
        } catch (Exception ignored) { }
        return search("Việt Nam", "", "");
    }

    static JSONObject suggest(String query) throws Exception {
        String q = query.trim();
        List<String> rows = q.isEmpty() ? Collections.emptyList() : S.getSuggestionExtractor().suggestionList(q);
        JSONArray items = new JSONArray();
        for (String row : rows.subList(0, Math.min(8, rows.size()))) {
            items.put(row.contains("&") ? Html.fromHtml(row, Html.FROM_HTML_MODE_LEGACY).toString() : row);
        }
        return list(items, null);
    }

    private static JSONArray searchItems(List<? extends InfoItem> items, String mode) {
        if ("long".equals(mode)) return videos(items, mode);
        JSONArray result = new JSONArray();
        for (InfoItem item : items) {
            try {
                if (item instanceof StreamInfoItem && !"channels".equals(mode)) result.put(video((StreamInfoItem) item));
                else if (item instanceof ChannelInfoItem && !"videos".equals(mode)) {
                    ChannelInfoItem c = (ChannelInfoItem) item;
                    String id = channelId(c.getUrl());
                    if (!id.isEmpty()) result.put(new JSONObject().put("kind", "npChannel")
                        .put("id", id).put("title", nz(c.getName())).put("thumb", bestImage(c.getThumbnails()))
                        .put("subscribers", c.getSubscriberCount()));
                }
            } catch (Exception ignored) { }
        }
        return result;
    }

    static JSONObject search(String q, String mode, String token) throws Exception {
        if (!token.isEmpty()) return more(token, mode);
        if (q.trim().isEmpty()) throw new IOException("Nhập từ khóa để tìm kiếm.");
        SearchQueryHandler h = S.getSearchQHFactory().fromQuery(q.trim(),
            "channels".equals(mode) ? Collections.singletonList(YoutubeSearchQueryHandlerFactory.CHANNELS) :
                ("videos".equals(mode) || "long".equals(mode)) ? Collections.singletonList(YoutubeSearchQueryHandlerFactory.VIDEOS) : Collections.emptyList(), "");
        SearchInfo si = SearchInfo.getInfo(S, h);
        JSONArray a = searchItems(si.getRelatedItems(), mode);
        Page next = si.getNextPage();
        if (a.length() == 0 && next == null && !si.getErrors().isEmpty())
            throw new IOException("Chưa lấy được kết quả từ YouTube. Thử lại sau.");
        return list(a, next != null ? keep("search", h, next) : null);
    }

    // v0.27.1 không thu thập Shorts shelf trong tìm kiếm. Lấy trực tiếp tab Shorts của kênh.
    private static final String[] SHORTS_CHANNELS = {"UCX6OQ3DkcsbYNE6H8uQQuVA", "UCsXVk37bltHxD1rDPwtNM8Q"};
    static JSONObject shorts(String channel, String token) throws Exception {
        if (!token.isEmpty()) return more(token, "short");
        if (!channel.isEmpty()) return shortTab(channel);
        JSONArray items = new JSONArray();
        String next = null;
        Exception error = null;
        for (String id : SHORTS_CHANNELS) {
            try {
                JSONObject batch = shortTab(id);
                addAll(items, batch.getJSONArray("items"));
                if (next == null) {
                    next = batch.optString("nextPageToken", null);
                }
            } catch (Exception e) { error = e; }
        }
        if (items.length() == 0 && error != null) {
            throw error;
        }
        return list(items, next);
    }

    private static JSONObject shortTab(String id) throws Exception {
        if (!id.matches("UC[\\w-]{22}")) throw new IOException("Không tìm thấy kênh Shorts.");
        ListLinkHandler h = S.getChannelTabLHFactory().fromQuery(id, Collections.singletonList(ChannelTabs.SHORTS), "");
        ChannelTabInfo ti = ChannelTabInfo.getInfo(S, h);
        return list(videos(ti.getRelatedItems(), "short"), ti.getNextPage() != null ? keep("channelShorts", h, ti.getNextPage()) : null);
    }

    // ---------- phân trang chung ----------
    @SuppressWarnings("unchecked")
    private static JSONObject more(String token, String mode) throws Exception {
        Object[] c = pages.get(token);
        if (c == null) throw new IOException("Phiên cuộn đã hết hạn. Hãy tải lại trang.");
        String kind = (String) c[0];
        Page pg = (Page) c[2];
        switch (kind) {
            case "kiosk": {
                ListExtractor.InfoItemsPage<? extends InfoItem> p = ((KioskExtractor<? extends InfoItem>) c[1]).getPage(pg);
                return list(videos(p.getItems(), mode), p.getNextPage() != null ? keep(kind, c[1], p.getNextPage()) : null);
            }
            case "search": {
                ListExtractor.InfoItemsPage<InfoItem> p = SearchInfo.getMoreItems(S, (SearchQueryHandler) c[1], pg);
                return list(searchItems(p.getItems(), mode), p.getNextPage() != null ? keep(kind, c[1], p.getNextPage()) : null);
            }
            case "channelShorts":
            case "channel": {
                ListExtractor.InfoItemsPage<InfoItem> p = ChannelTabInfo.getMoreItems(S, (ListLinkHandler) c[1], pg);
                return list(videos(p.getItems(), "channelShorts".equals(kind) ? "short" : ""),
                    p.getNextPage() != null ? keep(kind, c[1], p.getNextPage()) : null);
            }
            case "comments":
            case "replies": {
                ListExtractor.InfoItemsPage<CommentsInfoItem> p = CommentsInfo.getMoreItems(S, (String) c[1], pg);
                return list(commentList(p.getItems(), (String) c[1]), p.getNextPage() != null ? keep(kind, c[1], p.getNextPage()) : null);
            }
            default: throw new IOException("Phiên cuộn không hợp lệ.");
        }
    }

    // ---------- video ----------
    private static StreamInfo info(String id) throws Exception {
        Cached c = infos.get(id);
        if (c == null || System.currentTimeMillis() - c.at > 45 * 60 * 1000L) {
            c = new Cached();
            c.info = StreamInfo.getInfo(S, "https://www.youtube.com/watch?v=" + id);
            c.at = System.currentTimeMillis();
            infos.put(id, c);
        }
        return c.info;
    }

    static JSONObject video(String id) throws Exception {
        StreamInfo i = info(id);
        JSONObject o = new JSONObject().put("kind", "np").put("id", id).put("title", nz(i.getName()))
            .put("channel", nz(i.getUploaderName())).put("channelId", channelId(i.getUploaderUrl()))
            .put("thumb", "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg")
            .put("publishedText", nz(i.getTextualUploadDate())).put("duration", i.getDuration())
            .put("description", text(i.getDescription()))
            .put("live", i.getStreamType() == StreamType.LIVE_STREAM)
            .put("related", videos(i.getRelatedItems(), ""))
            .put("qualities", qualities(i));
        if (i.getViewCount() >= 0) o.put("views", i.getViewCount());
        if (i.getLikeCount() >= 0) o.put("likes", i.getLikeCount());
        return o;
    }

    // ---------- chọn luồng phát ----------
    private static List<VideoStream> usable(List<VideoStream> src) {
        List<VideoStream> l = new ArrayList<>();
        if (src != null) for (VideoStream v : src)
            if (v.isUrl() && v.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP) l.add(v);
        return l;
    }

    private static List<AudioStream> usableAudio(List<AudioStream> src) {
        List<AudioStream> all = new ArrayList<>(), orig = new ArrayList<>();
        if (src != null) for (AudioStream a : src) {
            if (!a.isUrl() || a.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) continue;
            all.add(a);
            if (a.getAudioTrackType() == null || a.getAudioTrackType() == AudioTrackType.ORIGINAL) orig.add(a);
        }
        return orig.isEmpty() ? all : orig;
    }

    private static JSONArray qualities(StreamInfo i) {
        TreeSet<Integer> hs = new TreeSet<>(Collections.reverseOrder());
        boolean split = !usable(i.getVideoOnlyStreams()).isEmpty() && !usableAudio(i.getAudioStreams()).isEmpty();
        for (VideoStream v : usable(split ? i.getVideoOnlyStreams() : i.getVideoStreams())) if (v.getHeight() > 0) hs.add(v.getHeight());
        JSONArray a = new JSONArray();
        for (int h : hs) a.put(h);
        return a;
    }

    private static long score(VideoStream v, int max) {
        int h = v.getHeight();
        long s = h <= max ? 10000L + h : 10000L - h;
        return s * 2 + (v.getFormat() == MediaFormat.MPEG_4 ? 1 : 0);
    }

    private static Pick pick(StreamInfo i, int height) {
        Pick p = new Pick();
        List<VideoStream> only = usable(i.getVideoOnlyStreams()), mux = usable(i.getVideoStreams());
        List<AudioStream> au = usableAudio(i.getAudioStreams());
        List<VideoStream> from = !only.isEmpty() && !au.isEmpty() ? only : mux;
        if (i.getStreamType() == StreamType.LIVE_STREAM || from.isEmpty()) {
            String hls = i.getHlsUrl();
            if (hls == null || hls.isEmpty()) return null;
            p.video = hls;
            p.hls = true;
            return p;
        }
        VideoStream best = null;
        for (VideoStream v : from) if (best == null || score(v, height) > score(best, height)) best = v;
        p.video = best.getContent();
        p.height = best.getHeight();
        if (from == only) {
            AudioStream a = au.get(0);
            for (AudioStream x : au) if (x.getAverageBitrate() > a.getAverageBitrate()) a = x;
            p.audio = a.getContent();
        }
        return p;
    }

    /** Trả về {videoUrl, audioUrl|"", "hls"|"progressive", userAgent}. */
    static String[] stream(String id, int height) throws Exception {
        StreamInfo i = info(id);
        Pick p = pick(i, height <= 0 ? 720 : height);
        if (p == null) throw new IOException("Không lấy được luồng phát trực tiếp cho video này.");
        return new String[]{p.video, p.audio == null ? "" : p.audio, p.hls ? "hls" : "progressive", userAgent(p.video)};
    }

    private static String userAgent(String url) {
        if (url.contains("c=ANDROID_VR")) return "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip";
        if (url.contains("c=ANDROID")) return "com.google.android.youtube/19.28.35 (Linux; U; Android 14; en_US) gzip";
        if (url.contains("c=IOS")) return "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)";
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
    }

    // ---------- bình luận ----------
    private static JSONArray commentList(List<CommentsInfoItem> src, String url) throws JSONException {
        JSONArray a = new JSONArray();
        for (CommentsInfoItem c : src) {
            String cid = c.getCommentId();
            if (cid == null) continue;
            if (c.getReplies() != null) replies.put(cid, new Object[]{url, c.getReplies()});
            a.put(new JSONObject().put("id", cid).put("author", nz(c.getUploaderName()))
                .put("text", text(c.getCommentText())).put("publishedText", nz(c.getTextualUploadDate()))
                .put("likes", Math.max(0, c.getLikeCount())).put("replyCount", Math.max(0, c.getReplyCount())));
        }
        return a;
    }

    static JSONObject comments(String id, String token) throws Exception {
        if (!token.isEmpty()) return more(token, "");
        String url = "https://www.youtube.com/watch?v=" + id;
        CommentsInfo ci = CommentsInfo.getInfo(S, url);
        Page next = ci.getNextPage();
        return list(commentList(ci.getRelatedItems(), url), next != null ? keep("comments", url, next) : null);
    }

    static JSONObject replies(String commentId, String token) throws Exception {
        if (!token.isEmpty()) return more(token, "");
        Object[] r = replies.get(commentId);
        if (r == null) throw new IOException("Không tải được phản hồi của bình luận này.");
        String url = (String) r[0];
        ListExtractor.InfoItemsPage<CommentsInfoItem> p = CommentsInfo.getMoreItems(S, url, (Page) r[1]);
        return list(commentList(p.getItems(), url), p.getNextPage() != null ? keep("replies", url, p.getNextPage()) : null);
    }

    // ---------- kênh ----------
    static JSONObject channel(String id) throws Exception {
        ChannelInfo ci = ChannelInfo.getInfo(S, "https://www.youtube.com/channel/" + id);
        JSONObject o = new JSONObject().put("id", id).put("title", nz(ci.getName()))
            .put("description", nz(ci.getDescription())).put("avatar", bestImage(ci.getAvatars()))
            .put("subscribers", ci.getSubscriberCount())
            .put("hasShorts", ci.getTabs().stream().anyMatch(t -> t.getContentFilters().contains(ChannelTabs.SHORTS)));
        JSONArray items = new JSONArray();
        String token = null;
        for (ListLinkHandler t : ci.getTabs()) {
            if (!t.getContentFilters().contains(ChannelTabs.VIDEOS)) continue;
            ChannelTabInfo ti = ChannelTabInfo.getInfo(S, t);
            items = videos(ti.getRelatedItems(), "");
            if (ti.getNextPage() != null) token = keep("channel", t, ti.getNextPage());
            break;
        }
        o.put("items", items);
        if (token != null) o.put("nextPageToken", token);
        return o;
    }

    static JSONObject channelMore(String token) throws Exception { return more(token, ""); }

    // ---------- thông báo lỗi dễ hiểu ----------
    static String friendly(Exception e) {
        if (e instanceof SignInConfirmNotBotException) return "YouTube đang yêu cầu xác minh không phải bot với kết nối này. Thử đổi mạng (Wi‑Fi/4G) hoặc thử lại sau.";
        if (e instanceof ReCaptchaException) return "YouTube tạm chặn quá nhiều yêu cầu. Thử lại sau ít phút.";
        if (e instanceof AgeRestrictedContentException) return "Video giới hạn độ tuổi, không xem được trong ứng dụng.";
        if (e instanceof GeographicRestrictionException) return "Video bị chặn ở khu vực của bạn.";
        if (e instanceof PrivateContentException) return "Video riêng tư.";
        if (e instanceof PaidContentException) return "Video trả phí / chỉ dành cho hội viên.";
        if (e instanceof ContentNotAvailableException) return "Video không khả dụng.";
        return null;
    }

}
