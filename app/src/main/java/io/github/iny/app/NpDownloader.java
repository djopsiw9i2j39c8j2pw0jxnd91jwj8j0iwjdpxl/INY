package io.github.iny.app;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;
import java.util.zip.GZIPInputStream;

final class NpDownloader extends Downloader {
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

    private static Charset charset(String contentType) {
        if (contentType != null) {
            Matcher m = Pattern.compile("(?i)charset=\\s*[\"']?([^;\\s\"']+)").matcher(contentType);
            if (m.find()) try { return Charset.forName(m.group(1)); } catch (IllegalArgumentException ignored) { }
        }
        return StandardCharsets.UTF_8;
    }

    private static String suggestionUrl(String value) throws IOException {
        URL url = new URL(value);
        if ("https".equals(url.getProtocol()) && "suggestqueries-clients6.youtube.com".equals(url.getHost())
                && "/complete/search".equals(url.getPath()) && url.getQuery() != null) return value + "&oe=UTF-8";
        return value;
    }

    @Override public Response execute(Request req) throws IOException, ReCaptchaException {
        HttpURLConnection c = (HttpURLConnection) new URL(suggestionUrl(req.url())).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestMethod(req.httpMethod());
        c.setRequestProperty("User-Agent", UA);
        for (Map.Entry<String, List<String>> h : req.headers().entrySet()) {
            boolean first = true;
            for (String v : h.getValue()) {
                if (first) c.setRequestProperty(h.getKey(), v); else c.addRequestProperty(h.getKey(), v);
                first = false;
            }
        }
        byte[] body = req.dataToSend();
        try {
            if (body != null || "POST".equals(req.httpMethod())) {
                c.setDoOutput(true);
                try (OutputStream o = c.getOutputStream()) { if (body != null) o.write(body); }
            }
            int code = c.getResponseCode();
            if (code == 429) throw new ReCaptchaException("YouTube yêu cầu xác minh (429)", req.url());
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = "";
            if (in != null) {
                if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
                try (InputStream s = in) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = s.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        if (out.size() > 24 * 1024 * 1024) throw new IOException("Phản hồi quá lớn.");
                    }
                    text = new String(out.toByteArray(), charset(c.getContentType()));
                }
            }
            Map<String, List<String>> headers = new HashMap<>();
            for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet())
                if (e.getKey() != null) headers.put(e.getKey(), e.getValue());
            return new Response(code, c.getResponseMessage(), headers, text, c.getURL().toString());
        } finally {
            c.disconnect();
        }
    }
}
