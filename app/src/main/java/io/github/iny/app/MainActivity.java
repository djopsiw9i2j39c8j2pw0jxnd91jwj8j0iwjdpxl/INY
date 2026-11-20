package io.github.iny.app;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.webkit.*;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.webkit.*;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.ApiException;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final String ORIGIN = "https://io.github.iny.app";
    private static final int AUTH = 301, UPLOAD = 302;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private WebView web;
    private FrameLayout root;
    private View fullView;
    private WebChromeClient.CustomViewCallback fullCallback;
    private SharedPreferences prefs;
    private volatile String token = "";
    private volatile long tokenUntil = 0;
    private JavaScriptReplyProxy authReply, uploadReply;
    private String authId, uploadId;
    private JSONObject uploadMetadata;
    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout.LayoutParams playerLp;
    private String playingId;
    private boolean fullscreen, portraitShort;
    private FrameLayout shortOverlay;
    private TextView ovChannel, ovTitle, ovSub, ovLike, ovSave;
    private long playSequence;
    private int quality = 720;
    private JSONArray qualityOptions = new JSONArray();
    private float playbackSpeed = 1f;
    private static final float[] SPEEDS = {.25f, .5f, .75f, 1f, 2f, 3f, 4f, 5f};
    private static final String[] SPEED_LABELS = {"0.25×", "0.5×", "0.75×", "1× (Bình thường)", "2×", "3×", "4×", "5×"};


    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("iny_private", MODE_PRIVATE);
        Yt.init();
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16,16,18));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom));
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setBackgroundColor(Color.rgb(16,16,18));
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                Uri u = req.getUrl();
                if (ORIGIN.equals(u.getScheme()+"://"+u.getHost()) && "/".equals(u.getPath())) return false;
                openExternal(u.toString());
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                if ("io.github.iny.app".equals(req.getUrl().getHost())) {
                    String path = req.getUrl().getPath();
                    Map<String,String> files = new HashMap<>();
                    files.put("/logo.png","image/png"); files.put("/app.js","application/javascript"); files.put("/styles.css","text/css");
                    if (files.containsKey(path)) {
                        try { return new WebResourceResponse(files.get(path), "UTF-8", getAssets().open(path.substring(1))); }
                        catch (IOException ignored) { }
                    }
                    return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback cb) {
                if(fullView!=null){cb.onCustomViewHidden();return;}
                fullView=view;fullCallback=cb;web.setVisibility(View.GONE);
                root.addView(view,new FrameLayout.LayoutParams(-1,-1));
                if(Build.VERSION.SDK_INT>=30) getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
            }
            @Override public void onHideCustomView(){ hideFullscreen(); }
        });
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            new AlertDialog.Builder(this).setTitle("Cập nhật Android System WebView")
                .setMessage("App cần WebView mới để giao tiếp an toàn. Hãy cập nhật Android System WebView hoặc Chrome rồi mở lại.")
                .setPositiveButton("Đóng",(d,w)->finish()).show();
            return;
        }
        WebViewCompat.addWebMessageListener(web,"Native",Collections.singleton(ORIGIN),(view,message,sourceOrigin,isMainFrame,reply)->{
            if(!isMainFrame || !ORIGIN.equals(sourceOrigin.toString())) return;
            try {
                JSONObject req = new JSONObject(message.getData());
                String id=req.getString("id"), op=req.getString("op");
                JSONObject args=req.optJSONObject("args");
                if(args==null) args=new JSONObject();
                final JSONObject a=args;
                if("authorize".equals(op)){ authorize(id,reply,a.optBoolean("upload"));return; }
                if("webLogin".equals(op)){ webLogin(id,reply);return; }
                if("upload".equals(op)){ chooseUpload(id,a,reply);return; }
                if("copy".equals(op)){
                    ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("INY",a.optString("text").substring(0,Math.min(30000,a.optString("text").length()))));
                    respond(reply,id,new JSONObject().put("copied",true),null);return;
                }
                if("share".equals(op)){
                    String text=a.optString("text");
                    startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"Chia sẻ"));
                    respond(reply,id,new JSONObject(),null);return;
                }
                if("open".equals(op)){openExternal(a.optString("url"));respond(reply,id,new JSONObject(),null);return;}
                if ("play".equals(op)) { play(a,id,reply); return; }
                if ("stop".equals(op)) { stopPlayer(); respond(reply,id,new JSONObject(),null); return; }
                if ("place".equals(op)) { layoutPlayer(a.optJSONObject("rect")); respond(reply,id,new JSONObject(),null); return; }
                if ("immersive".equals(op)) { final boolean on=a.optBoolean("on"); runOnUiThread(()->setShortImmersive(on)); respond(reply,id,new JSONObject(),null); return; }
                if ("shortInfo".equals(op)) { runOnUiThread(()->updateShortInfo(a)); respond(reply,id,new JSONObject(),null); return; }
                if ("speed".equals(op)) { setSpeed((float)a.optDouble("value",1)); respond(reply,id,new JSONObject(),null); return; }
                workers.execute(()->{
                    try{respond(reply,id,dispatch(op,a),null);}catch(Exception e){respond(reply,id,null,safeMessage(e));}
                });
            }catch(Exception ignored){ }
        });
        try {
            String html;
            try(InputStream in=getAssets().open("app.html")){html=new String(readBytes(in),StandardCharsets.UTF_8);}
            web.loadDataWithBaseURL(ORIGIN+"/",html,"text/html","UTF-8",null);
        }catch(IOException e){finish();}
    }
    private static byte[] readBytes(InputStream in)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;
        while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>8*1024*1024)throw new IOException("Phản hồi vượt quá giới hạn 8 MiB.");}
        return out.toByteArray();
    }
    private String safeMessage(Exception e){
        String friendly=Yt.friendly(e);
        if(friendly!=null) return friendly;
        String msg=e.getMessage();
        if(msg==null) return "Không hoàn tất được thao tác. Kiểm tra mạng rồi thử lại.";
        if(!token.isEmpty())msg=msg.replace(token,"[redacted]");
        return msg.length()>1400?msg.substring(0,1400):msg;
    }
    private void respond(JavaScriptReplyProxy p,String id,JSONObject result,String error){
        runOnUiThread(()->{try{
            JSONObject o=new JSONObject().put("id",id);
            if(error==null)o.put("data",result==null?new JSONObject():result);else o.put("error",error);
            p.postMessage(o.toString());
        }catch(Exception ignored){ }});
    }
    private void event(String name,JSONObject args){
        runOnUiThread(()->{if(web!=null) web.evaluateJavascript("window.nativeEvent&&nativeEvent("+JSONObject.quote(name)+","+args+")",null);});
    }
    private boolean hasToken(){return !token.isEmpty() && SystemClock.elapsedRealtime()<tokenUntil;}
    private JSONObject dispatch(String op,JSONObject a) throws Exception {
        if(YtWeb.handles(op)&&(YtWeb.loggedIn()||("playlistVideos".equals(op)&&!hasToken())))return YtWeb.handle(op,a);
        switch(op){
            case "config": return new JSONObject().put("versionName",BuildConfig.VERSION_NAME).put("versionCode",BuildConfig.VERSION_CODE)
                .put("package",BuildConfig.APPLICATION_ID).put("authorized",hasToken()||YtWeb.loggedIn()).put("sha1",certificateSha1());
            case "logout":token="";tokenUntil=0;YtWeb.logout();return new JSONObject();
            case "update":return checkUpdate();
            case "download":return download(a);
            case "popular":return Yt.home(a.optString("pageToken"));
            case "search":return Yt.search(a.optString("q"),a.optString("duration"),a.optString("pageToken"));
            case "video":return Yt.video(a.getString("id"));
            case "comments":return Yt.comments(a.getString("id"),a.optString("pageToken"));
            case "replies":return Yt.replies(a.getString("id"),a.optString("pageToken"));
            case "channel":return Yt.channel(a.getString("id"));
            case "channelVideos":return Yt.channelMore(a.optString("pageToken"));
            case "suggest":return Yt.suggest(a.optString("q"));
            case "shorts":return Yt.shorts(a.optString("channel"),a.optString("pageToken"));
            case "rating":return api("GET","videos/getRating",params("id",a.getString("id")),null,true);
            case "myChannel":return api("GET","channels",params("part","snippet,statistics,contentDetails","mine","true"),null,true);
            case "subscriptions":return api("GET","subscriptions",params("part","snippet","mine","true","maxResults","50","pageToken",a.optString("pageToken")),null,true);
            case "subscription":return api("GET","subscriptions",params("part","id","mine","true","forChannelId",a.getString("id")),null,true);
            case "subscribe":return api("POST","subscriptions",params("part","snippet"),new JSONObject().put("snippet",new JSONObject().put("resourceId",new JSONObject().put("kind","youtube#channel").put("channelId",a.getString("id")))),true);
            case "unsubscribe":return api("DELETE","subscriptions",params("id",a.getString("id")),null,true);
            case "rate":return api("POST","videos/rate",params("id",a.getString("id"),"rating",a.optString("rating","like")),null,true);
            case "liked":return api("GET","videos",params("part","snippet,contentDetails,statistics","myRating","like","maxResults","24","pageToken",a.optString("pageToken")),null,true);
            case "playlists":return api("GET","playlists",params("part","snippet,contentDetails","mine","true","maxResults","50","pageToken",a.optString("pageToken")),null,true);
            case "playlistVideos":return api("GET","playlistItems",params("part","snippet,contentDetails","playlistId",a.getString("id"),"maxResults","50","pageToken",a.optString("pageToken")),null,false);
            case "createPlaylist":return api("POST","playlists",params("part","snippet,status"),new JSONObject().put("snippet",new JSONObject().put("title",a.getString("title"))).put("status",new JSONObject().put("privacyStatus","private")),true);
            case "savePlaylist":return api("POST","playlistItems",params("part","snippet"),new JSONObject().put("snippet",new JSONObject().put("playlistId",a.getString("playlistId")).put("resourceId",new JSONObject().put("kind","youtube#video").put("videoId",a.getString("videoId")))),true);
            case "postComment":return api("POST","commentThreads",params("part","snippet"),new JSONObject().put("snippet",new JSONObject().put("videoId",a.getString("id")).put("topLevelComment",new JSONObject().put("snippet",new JSONObject().put("textOriginal",a.getString("text"))))),true);
            default:throw new IOException("Thao tác chưa được hỗ trợ: "+op);
        }
    }
    private Map<String,String> params(String... entries){
        Map<String,String> m=new LinkedHashMap<>();for(int i=0;i<entries.length;i+=2)if(entries[i+1]!=null&&!entries[i+1].isEmpty())m.put(entries[i],entries[i+1]);return m;
    }
    private JSONObject api(String method,String path,Map<String,String> q,JSONObject body,boolean auth)throws Exception{
        if(auth&&!hasToken())throw new IOException("AUTH_REQUIRED: Đăng nhập / cấp quyền Google trước khi thực hiện.");
        if(!hasToken())throw new IOException("AUTH_REQUIRED: Đăng nhập / cấp quyền Google trước khi dùng tính năng này.");
        StringBuilder url=new StringBuilder("https://www.googleapis.com/youtube/v3/").append(path).append('?');
        for(Map.Entry<String,String> en:q.entrySet())url.append(URLEncoder.encode(en.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(en.getValue(),"UTF-8")).append('&');
        HttpURLConnection c=(HttpURLConnection)new URL(url.toString()).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(15000);c.setReadTimeout(30000);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("X-Android-Package",BuildConfig.APPLICATION_ID);c.setRequestProperty("X-Android-Cert",certificateSha1().replace(":",""));
        if(hasToken())c.setRequestProperty("Authorization","Bearer "+token);
        try{
            if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            return readResponse(c);
        }finally{c.disconnect();}
    }
    private JSONObject readResponse(HttpURLConnection c)throws Exception{
        int code=c.getResponseCode();
        InputStream stream=code>=400?c.getErrorStream():c.getInputStream();
        String text="";if(stream!=null)try(InputStream in=stream){text=new String(readBytes(in),StandardCharsets.UTF_8);}
        if(code>=400){
            String msg="HTTP "+code;
            try{JSONObject err=new JSONObject(text).getJSONObject("error");msg=err.optString("message",msg);JSONArray reasons=err.optJSONArray("errors");if(reasons!=null&&reasons.length()>0)msg+=" ("+reasons.getJSONObject(0).optString("reason")+")";}catch(JSONException ignored){}
            if(code==401){token="";tokenUntil=0;msg="AUTH_REQUIRED: Phiên Google hết hạn. Hãy đăng nhập lại.";}
            throw new IOException(msg);
        }
        return text.isEmpty()?new JSONObject():new JSONObject(text);
    }
    private String certificateSha1()throws Exception{
        Signature[] signatures;
        if(Build.VERSION.SDK_INT>=28)signatures=getPackageManager().getPackageInfo(getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.getApkContentsSigners();
        else signatures=getPackageManager().getPackageInfo(getPackageName(),PackageManager.GET_SIGNATURES).signatures;
        byte[] sha=MessageDigest.getInstance("SHA-1").digest(signatures[0].toByteArray());
        StringBuilder b=new StringBuilder();for(byte v:sha){if(b.length()>0)b.append(':');b.append(String.format(Locale.US,"%02X",v&255));}return b.toString();
    }
    private void authorize(String id,JavaScriptReplyProxy reply,boolean upload){
        if(authReply!=null){respond(reply,id,null,"Một yêu cầu cấp quyền đang mở.");return;}
        authReply=reply;authId=id;
        List<Scope> scopes=new ArrayList<>();
        scopes.add(new Scope("https://www.googleapis.com/auth/youtube.force-ssl"));
        if(upload) scopes.add(new Scope("https://www.googleapis.com/auth/youtube.upload"));
        AuthorizationRequest req=AuthorizationRequest.builder().setRequestedScopes(scopes).build();
        Identity.getAuthorizationClient(this).authorize(req).addOnSuccessListener(result->{
            if(result.hasResolution()){
                try{startIntentSenderForResult(result.getPendingIntent().getIntentSender(),AUTH,null,0,0,0);}
                catch(Exception e){completeAuth(null,e);}
            }else completeAuth(result,null);
        }).addOnFailureListener(e->completeAuth(null,e));
    }
    private void completeAuth(AuthorizationResult result,Exception error){
        if(authReply==null)return;
        try{
            if(error!=null)throw error;
            if(result==null||result.getAccessToken()==null)throw new IOException("Không nhận được quyền truy cập Google.");
            token=result.getAccessToken();tokenUntil=SystemClock.elapsedRealtime()+50*60*1000;
            respond(authReply,authId,new JSONObject().put("authorized",true),null);
        }catch(Exception e){
            String message;
            if(e instanceof ApiException){
                int code=((ApiException)e).getStatusCode();
                message=code==10 ? "Cấu hình Google OAuth chưa khớp. Kiểm tra package và SHA-1 trong Cài đặt." :
                    code==8 ? "Google từ chối (mã 8): SHA-1 của bản cài này chưa được đăng ký trong Google Cloud. Mở Bạn → Cài đặt, sao chép SHA-1 và thêm vào OAuth client Android." :
                    code==16 || code==12501 ? "Bạn đã hủy cấp quyền Google." : "Google không cấp quyền (mã " + code + "). Kiểm tra ứng dụng OAuth và tài khoản thử nghiệm.";
            } else message=safeMessage(e);
            respond(authReply,authId,null,message);
        }
        authReply=null;authId=null;
    }
    private void chooseUpload(String id,JSONObject a,JavaScriptReplyProxy reply){
        if(!hasToken()){respond(reply,id,null,YtWeb.loggedIn()?"Tải video lên cần Google OAuth (chưa hỗ trợ khi đăng nhập bằng tài khoản trong app).":"AUTH_REQUIRED: Đăng nhập trước khi tải lên.");return;}
        if(uploadReply!=null){respond(reply,id,null,"Một video đang được tải lên.");return;}
        uploadId=id;uploadReply=reply;uploadMetadata=a;
        try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),UPLOAD);}
        catch(Exception e){respond(uploadReply,uploadId,null,safeMessage(e));uploadReply=null;}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==AUTH){
            if(data==null){completeAuth(null,new IOException(result==RESULT_CANCELED ? "Đã đóng màn hình cấp quyền Google." : "Google không trả về kết quả cấp quyền."));return;}
            try{completeAuth(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data),null);}catch(Exception e){completeAuth(null,e);}
        }
        if(request==UPLOAD&&uploadReply!=null){
            final JavaScriptReplyProxy reply=uploadReply;final String id=uploadId;final JSONObject metadata=uploadMetadata;
            if(result!=RESULT_OK||data==null||data.getData()==null){respond(reply,id,null,"Đã hủy chọn video.");uploadReply=null;return;}
            Uri uri=data.getData();
            workers.execute(()->{try{respond(reply,id,uploadVideo(uri,metadata),null);}catch(Exception e){respond(reply,id,null,safeMessage(e));}finally{runOnUiThread(()->uploadReply=null);}});
        }
    }
    private JSONObject uploadVideo(Uri uri,JSONObject a)throws Exception{
        long size=-1;
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0))size=c.getLong(0);}
        if(size<=0)throw new IOException("Không đọc được kích thước. Chọn video lưu trên thiết bị thay vì tệp đám mây.");
        if(!hasToken())throw new IOException("Cấp quyền Google lại trước khi tải lên.");
        String mime=getContentResolver().getType(uri);if(mime==null)mime="video/mp4";
        String privacy=a.optString("privacy","private");if(!Arrays.asList("private","unlisted","public").contains(privacy))privacy="private";
        String title=a.optString("title").trim();if(title.isEmpty()||title.length()>100)throw new IOException("Tiêu đề cần từ 1 đến 100 ký tự.");
        JSONObject body=new JSONObject().put("snippet",new JSONObject().put("title",title).put("description",a.optString("description"))).put("status",new JSONObject().put("privacyStatus",privacy).put("selfDeclaredMadeForKids",a.optBoolean("madeForKids",false)));
        HttpURLConnection init=(HttpURLConnection)new URL("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status").openConnection();
        init.setConnectTimeout(15000);init.setReadTimeout(30000);init.setRequestMethod("POST");init.setDoOutput(true);
        init.setRequestProperty("Authorization","Bearer "+token);init.setRequestProperty("Content-Type","application/json; charset=UTF-8");init.setRequestProperty("X-Upload-Content-Type",mime);init.setRequestProperty("X-Upload-Content-Length",Long.toString(size));
        String location;
        try{
            try(OutputStream out=init.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            if(init.getResponseCode()>=400)readResponse(init);
            location=init.getHeaderField("Location");
        }finally{init.disconnect();}
        if(location==null)throw new IOException("YouTube không trả về phiên tải lên.");
        URL target=new URL(location);
        if(!"https".equals(target.getProtocol())||!target.getHost().endsWith(".googleapis.com"))throw new IOException("Địa chỉ phiên tải lên không hợp lệ.");
        HttpURLConnection up=(HttpURLConnection)target.openConnection();up.setRequestMethod("PUT");up.setDoOutput(true);up.setConnectTimeout(20000);up.setReadTimeout(120000);up.setFixedLengthStreamingMode(size);up.setRequestProperty("Content-Type",mime);up.setRequestProperty("Authorization","Bearer "+token);
        long sent=0,last=0;
        try{
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=up.getOutputStream()){
                if(in==null)throw new IOException("Không mở được tệp.");
                byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))!=-1){out.write(buf,0,n);sent+=n;long now=SystemClock.elapsedRealtime();if(now-last>750){event("uploadProgress",new JSONObject().put("percent",(int)(100*sent/size)));last=now;}}
            }
            JSONObject res=readResponse(up);event("uploadProgress",new JSONObject().put("percent",100));return res;
        }finally{up.disconnect();}
    }
    private JSONObject checkUpdate()throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BuildConfig.RELEASES_API).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","INY/"+BuildConfig.VERSION_NAME);
        try{
            if(c.getResponseCode()==404)return new JSONObject().put("available",false).put("message","Repository chưa có GitHub Release công khai.");
            JSONObject release=readResponse(c);String tag=release.optString("tag_name").replaceFirst("^[vV]","");
            long remote=parseVersion(tag), local=parseVersion(BuildConfig.VERSION_NAME);
            return new JSONObject().put("available",remote>local).put("tag",tag).put("url",release.optString("html_url")).put("body",release.optString("body")).put("message",remote>local?"Có bản mới: "+tag:"Bạn đang dùng bản hiện tại (hoặc mới hơn).");
        }finally{c.disconnect();}
    }
    static long parseVersion(String v){try{String[] p=v.split("\\.");if(p.length!=3)return -1;return Long.parseLong(p[0])*100000+Long.parseLong(p[1])*1000+Long.parseLong(p[2]);}catch(Exception e){return -1;}}
    private JSONObject download(JSONObject a)throws Exception{
        if(Build.VERSION.SDK_INT<29 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            runOnUiThread(()->requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},401));
            throw new IOException("Android 8/9 cần quyền lưu tệp. Cho phép quyền rồi nhấn tải lại.");
        }
        Uri uri=Uri.parse(a.getString("url"));String host=uri.getHost();
        if(!"https".equals(uri.getScheme())||host==null||uri.getUserInfo()!=null)throw new IOException("Chỉ hỗ trợ URL HTTPS công khai.");
        host=host.toLowerCase(Locale.ROOT);
        for(String blocked:Arrays.asList("youtube.com","youtu.be","googlevideo.com","youtube-nocookie.com"))if(host.equals(blocked)||host.endsWith("."+blocked))throw new IOException("Không tải video YouTube qua API. Dùng liên kết tệp do bạn sở hữu / được phép tải.");
        for(InetAddress addr:InetAddress.getAllByName(host))if(addr.isAnyLocalAddress()||addr.isLoopbackAddress()||addr.isLinkLocalAddress()||addr.isSiteLocalAddress())throw new IOException("Không hỗ trợ địa chỉ mạng nội bộ.");
        String path=uri.getPath()==null?"":uri.getPath().toLowerCase(Locale.ROOT);
        if(!path.matches(".*\\.(mp4|webm|m4v|mov|m4a|mp3)$"))throw new IOException("Cần liên kết tệp trực tiếp .mp4/.webm/.mov/.m4v/.m4a/.mp3, không phải trang web.");
        String filename="INY-"+System.currentTimeMillis()+path.substring(path.lastIndexOf('.'));
        DownloadManager.Request req=new DownloadManager.Request(uri).setTitle(filename).setDescription("Tệp do người dùng yêu cầu tải").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,filename);
        long id=((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(req);return new JSONObject().put("downloadId",id).put("filename",filename);
    }
    private void play(JSONObject a,String request,JavaScriptReplyProxy reply)throws Exception{
        final String id=a.getString("id");
        if(!id.matches("[\\w-]{11}"))throw new IOException("Video không hợp lệ.");
        final long seq=++playSequence;
        final int height=a.optInt("height",720);
        final boolean shortMode=a.optBoolean("short");
        final JSONObject rect=a.optJSONObject("rect");
        final JSONArray options=a.optJSONArray("qualities");
        final JSONObject info=a.optJSONObject("info");
        setSpeed((float)a.optDouble("speed",playbackSpeed));
        workers.execute(()->{
            try{
                String[] streams=Yt.stream(id,height);
                runOnUiThread(()->{
                    if(seq!=playSequence||isDestroyed()){respond(reply,request,new JSONObject(),null);return;}
                    qualityOptions=options==null?new JSONArray():options;
                    quality=height;portraitShort=shortMode;
                    startPlayer(id,streams,rect);
                    if(shortMode&&info!=null)updateShortInfo(info);
                    respond(reply,request,new JSONObject(),null);
                });
            }catch(Exception e){respond(reply,request,null,safeMessage(e));}
        });
    }
    private void setSpeed(float rate){
        for(float allowed:SPEEDS)if(rate==allowed){playbackSpeed=rate;if(player!=null)player.setPlaybackSpeed(rate);return;}
    }
    private void playbackSettings(){
        new AlertDialog.Builder(this).setTitle("Cài đặt phát").setItems(new String[]{"Chất lượng", "Tốc độ phát"},(d,i)->{
            if(i==1){
                int selected=0;for(int j=0;j<SPEEDS.length;j++)if(SPEEDS[j]==playbackSpeed)selected=j;
                new AlertDialog.Builder(this).setTitle("Tốc độ phát").setSingleChoiceItems(SPEED_LABELS,selected,(dialog,k)->{
                    setSpeed(SPEEDS[k]);event("speedChanged",new JSONObject(Collections.singletonMap("value",playbackSpeed)));dialog.dismiss();
                }).setNegativeButton("Đóng",null).show();
            }else{
                String[] labels=new String[qualityOptions.length()];int selected=-1;
                for(int j=0;j<labels.length;j++){int h=qualityOptions.optInt(j);labels[j]=h+"p";if(h==quality)selected=j;}
                if(labels.length==0){new AlertDialog.Builder(this).setTitle("Chất lượng").setMessage("Video đang tự chọn chất lượng.").setPositiveButton("OK",null).show();return;}
                new AlertDialog.Builder(this).setTitle("Chất lượng").setSingleChoiceItems(labels,selected,(dialog,k)->{
                    int chosen=qualityOptions.optInt(k,720);
                    event("qualityChanged",new JSONObject(Collections.singletonMap("value",chosen)));dialog.dismiss();
                }).setNegativeButton("Đóng",null).show();
            }
        }).setNegativeButton("Đóng",null).show();
    }
    private JSONObject msg(String m){try{return new JSONObject().put("message",m);}catch(JSONException e){return new JSONObject();}}
    private void ensurePlayer(){
        if(player!=null)return;
        player=new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException e){stopPlayer();event("playerError",msg(e.getErrorCodeName()));}
        });
        playerView=new PlayerView(this){
            private float x,y; private boolean swiped, canSwipe;
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){
                if(!portraitShort||fullscreen)return super.dispatchTouchEvent(e);
                int action=e.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();swiped=false;canSwipe=true;}
                if(action==MotionEvent.ACTION_UP&&!swiped&&Math.abs(e.getX()-x)<12*getResources().getDisplayMetrics().density&&Math.abs(e.getY()-y)<12*getResources().getDisplayMetrics().density&&player!=null){player.setPlayWhenReady(!player.getPlayWhenReady());return true;}
                if(!swiped&&canSwipe&&action==MotionEvent.ACTION_MOVE){
                    float dy=e.getY()-y;
                    if(Math.abs(dy)>64*getResources().getDisplayMetrics().density&&Math.abs(dy)>Math.abs(e.getX()-x)*1.4f){
                        MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();
                        swiped=true;event("shortSwipe",new JSONObject(Collections.singletonMap("direction",dy<0?1:-1)));
                    }
                }
                if(swiped)return true;
                return action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE?true:super.dispatchTouchEvent(e);
            }
        };
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setPlayer(player);
        View gear=playerView.findViewById(androidx.media3.ui.R.id.exo_settings);
        if(gear!=null)gear.setOnClickListener(v->playbackSettings());
        playerView.setFullscreenButtonClickListener(this::setFullscreen);
        playerView.setVisibility(View.GONE);
        playerLp=new FrameLayout.LayoutParams(-1,0);
        root.addView(playerView,playerLp);
        buildShortOverlay();
    }
    private void startPlayer(String id,String[] st,JSONObject rect){
        ensurePlayer();
        playerView.setUseController(!portraitShort);
        layoutPlayer(rect);
        playerView.setVisibility(View.VISIBLE);
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(st[3]).setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15000).setReadTimeoutMs(30000);
        MediaSource src;
        if("hls".equals(st[2]))src=new HlsMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(st[0]));
        else{
            ProgressiveMediaSource.Factory pf=new ProgressiveMediaSource.Factory(http);
            src=pf.createMediaSource(MediaItem.fromUri(st[0]));
            if(!st[1].isEmpty())src=new MergingMediaSource(src,pf.createMediaSource(MediaItem.fromUri(st[1])));
        }
        boolean same=id.equals(playingId);
        long pos=same?player.getCurrentPosition():0;
        boolean resume=!same||player.getPlayWhenReady();
        playingId=id;
        syncOverlay();
        player.setMediaSource(src,pos);
        player.prepare();
        player.setRepeatMode(portraitShort?Player.REPEAT_MODE_ONE:Player.REPEAT_MODE_OFF);
        player.setPlaybackSpeed(playbackSpeed);
        player.setPlayWhenReady(resume);
    }
    private void layoutPlayer(JSONObject r){
        if(playerView==null||fullscreen||r==null)return;
        if(r.optDouble("w",0)<=0 || r.optDouble("h",0)<=0){playerView.setVisibility(View.INVISIBLE);syncOverlay();return;}
        if(playingId!=null)playerView.setVisibility(View.VISIBLE);
        float d=getResources().getDisplayMetrics().density;
        syncOverlay();
        playerLp.gravity=Gravity.TOP|Gravity.START;
        playerLp.leftMargin=Math.round((float)r.optDouble("x",0)*d);
        playerLp.topMargin=Math.round((float)r.optDouble("y",0)*d);
        playerLp.width=Math.round((float)r.optDouble("w",0)*d);
        playerLp.height=Math.round((float)r.optDouble("h",0)*d);
        playerView.setLayoutParams(playerLp);
    }
    @SuppressWarnings("deprecation")
    private void setFullscreen(boolean fs){
        if(playerView==null)return;
        fullscreen=fs;
        playerView.setLayoutParams(fs?new FrameLayout.LayoutParams(-1,-1):playerLp);
        setRequestedOrientation(fs&&!portraitShort?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController c=getWindow().getInsetsController();
            if(c!=null){if(fs)c.hide(WindowInsets.Type.systemBars());else c.show(WindowInsets.Type.systemBars());}
        }else getWindow().getDecorView().setSystemUiVisibility(fs?(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE):View.SYSTEM_UI_FLAG_VISIBLE);
        if(!fs)event("relayout",new JSONObject());
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void setIcon(TextView t,int res,int color){
        android.graphics.drawable.Drawable d=getResources().getDrawable(res,getTheme()).mutate();
        d.setBounds(0,0,dp(28),dp(28));d.setColorFilter(color,android.graphics.PorterDuff.Mode.SRC_IN);
        t.setCompoundDrawables(null,d,null,null);t.setCompoundDrawablePadding(dp(2));
    }
    private TextView ovButton(int icon,String label,String act){
        TextView t=new TextView(this);t.setText(label);t.setTextColor(Color.WHITE);t.setTextSize(12);t.setGravity(Gravity.CENTER);
        t.setShadowLayer(6,0,2,Color.BLACK);t.setPadding(dp(6),dp(8),dp(6),dp(8));t.setTypeface(null,android.graphics.Typeface.BOLD);
        setIcon(t,icon,Color.WHITE);
        t.setOnClickListener(v->{if(web!=null)web.evaluateJavascript("window.shortAct&&shortAct('"+act+"')",null);});return t;
    }
    private void buildShortOverlay(){
        shortOverlay=new FrameLayout(this);shortOverlay.setVisibility(View.GONE);
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0,0xCC000000}));
        shortOverlay.addView(shade,new FrameLayout.LayoutParams(-1,dp(260),Gravity.BOTTOM));
        android.widget.ImageView back=new android.widget.ImageView(this);back.setImageResource(R.drawable.ic_chevron_left);back.setPadding(dp(7),dp(7),dp(7),dp(7));
        GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.OVAL);bg.setColor(0x66000000);back.setBackground(bg);
        back.setOnClickListener(v->{if(web!=null)web.evaluateJavascript("window.shortAct&&shortAct('back')",null);});
        FrameLayout.LayoutParams bl=new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.TOP|Gravity.START);bl.setMargins(dp(10),dp(10),0,0);shortOverlay.addView(back,bl);
        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setGravity(Gravity.CENTER_HORIZONTAL);
        ovLike=ovButton(R.drawable.ic_heart,"Thích","like");TextView cm=ovButton(R.drawable.ic_message,"Bình luận","comments");ovSave=ovButton(R.drawable.ic_bookmark,"Lưu","save");
        TextView sh=ovButton(R.drawable.ic_share,"Chia sẻ","share");TextView st=ovButton(R.drawable.ic_sliders,"Cài đặt","settings");
        st.setOnClickListener(v->playbackSettings());
        for(TextView t:new TextView[]{ovLike,cm,ovSave,sh,st}){((TextView)t).setTextSize(12);col.addView(t,new LinearLayout.LayoutParams(dp(74),-2));}
        FrameLayout.LayoutParams cl=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);cl.setMargins(0,0,dp(4),dp(28));shortOverlay.addView(col,cl);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        ovChannel=new TextView(this);ovChannel.setTextColor(Color.WHITE);ovChannel.setTextSize(15);ovChannel.setTypeface(null,android.graphics.Typeface.BOLD);ovChannel.setSingleLine(true);ovChannel.setEllipsize(android.text.TextUtils.TruncateAt.END);ovChannel.setShadowLayer(6,0,2,Color.BLACK);ovChannel.setMaxWidth(dp(170));
        ovChannel.setOnClickListener(v->{if(web!=null)web.evaluateJavascript("window.shortAct&&shortAct('channel')",null);});
        ovSub=new TextView(this);ovSub.setText("Đăng ký");ovSub.setTextColor(Color.BLACK);ovSub.setTextSize(13);ovSub.setTypeface(null,android.graphics.Typeface.BOLD);ovSub.setPadding(dp(14),dp(7),dp(14),dp(7));
        GradientDrawable pill=new GradientDrawable();pill.setCornerRadius(dp(20));pill.setColor(Color.WHITE);ovSub.setBackground(pill);
        ovSub.setOnClickListener(v->{if(web!=null)web.evaluateJavascript("window.shortAct&&shortAct('subscribe')",null);});
        row.addView(ovChannel);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.setMargins(dp(10),0,0,0);row.addView(ovSub,sp);
        ovTitle=new TextView(this);ovTitle.setTextColor(Color.WHITE);ovTitle.setTextSize(14);ovTitle.setMaxLines(2);ovTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);ovTitle.setShadowLayer(6,0,2,Color.BLACK);ovTitle.setPadding(0,dp(8),0,0);
        info.addView(row);info.addView(ovTitle);
        FrameLayout.LayoutParams il=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.START);il.setMargins(dp(14),0,dp(84),dp(28));shortOverlay.addView(info,il);
        root.addView(shortOverlay,new FrameLayout.LayoutParams(-1,-1));
    }
    private void syncOverlay(){
        if(shortOverlay==null)return;
        shortOverlay.setVisibility(portraitShort&&playingId!=null&&playerView!=null&&playerView.getVisibility()==View.VISIBLE?View.VISIBLE:View.GONE);
        if(shortOverlay.getVisibility()==View.VISIBLE)shortOverlay.bringToFront();
    }
    private void updateShortInfo(JSONObject i){
        if(shortOverlay==null)return;
        if(i.has("channel"))ovChannel.setText(i.optString("channel"));
        if(i.has("title"))ovTitle.setText(i.optString("title"));
        if(i.has("likes"))ovLike.setText(i.optString("likes","Thích"));
        boolean liked=i.optBoolean("liked"),saved=i.optBoolean("saved");
        setIcon(ovLike,liked?R.drawable.ic_heart_fill:R.drawable.ic_heart,liked?0xFFFF4055:Color.WHITE);ovLike.setTextColor(liked?0xFFFF4055:Color.WHITE);
        setIcon(ovSave,saved?R.drawable.ic_bookmark_fill:R.drawable.ic_bookmark,Color.WHITE);ovSave.setText(saved?"Đã lưu":"Lưu");
        boolean sub=i.optBoolean("subscribed");ovSub.setText(sub?"Đã đăng ký":"Đăng ký");
        ((GradientDrawable)ovSub.getBackground()).setColor(sub?0x66FFFFFF:Color.WHITE);ovSub.setTextColor(sub?Color.WHITE:Color.BLACK);
        syncOverlay();
    }
    @SuppressWarnings("deprecation")
    private void setShortImmersive(boolean on){
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController c=getWindow().getInsetsController();
            if(c!=null){if(on){c.hide(WindowInsets.Type.systemBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}else c.show(WindowInsets.Type.systemBars());}
        }else getWindow().getDecorView().setSystemUiVisibility(on?(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE):View.SYSTEM_UI_FLAG_VISIBLE);
    }
    private LinearLayout loginView;
    private WebView loginWeb;
    private JavaScriptReplyProxy loginReply;
    private String loginId;
    private static final String LOGIN_URL="https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue="+Uri.encode("https://www.youtube.com/signin?action_handle_signin=true&app=desktop&hl=vi&next=https%3A%2F%2Fm.youtube.com%2F");
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void webLogin(String id,JavaScriptReplyProxy reply){
        runOnUiThread(()->{
            if(loginView!=null){respond(reply,id,null,"Màn hình đăng nhập đang mở.");return;}
            loginReply=reply;loginId=id;
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setBackgroundColor(Color.rgb(16,16,18));
            LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(14),dp(10),dp(14),dp(10));
            TextView title=new TextView(this);title.setText("Đăng nhập YouTube");title.setTextColor(Color.WHITE);title.setTextSize(16);title.setTypeface(null,android.graphics.Typeface.BOLD);
            TextView close=new TextView(this);close.setText("Đóng");close.setTextColor(Color.WHITE);close.setTextSize(15);close.setPadding(dp(12),dp(6),dp(12),dp(6));
            close.setOnClickListener(v->finishWebLogin(false));
            bar.addView(title,new LinearLayout.LayoutParams(0,-2,1f));bar.addView(close);
            WebView w=new WebView(this);
            WebSettings ws=w.getSettings();ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);
            ws.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
            CookieManager cm=CookieManager.getInstance();cm.setAcceptCookie(true);cm.setAcceptThirdPartyCookies(w,true);
            w.setWebViewClient(new WebViewClient(){
                @Override public void onPageFinished(WebView view,String url){
                    CookieManager.getInstance().flush();
                    try{String h=Uri.parse(url).getHost();if(h!=null&&h.endsWith("youtube.com")&&YtWeb.loggedIn())finishWebLogin(true);}catch(Exception ignored){}
                }
            });
            box.addView(bar);box.addView(w,new LinearLayout.LayoutParams(-1,0,1f));
            root.addView(box,new FrameLayout.LayoutParams(-1,-1));
            loginView=box;loginWeb=w;
            w.loadUrl(LOGIN_URL);
        });
    }
    private void finishWebLogin(boolean ok){
        if(loginView==null)return;
        root.removeView(loginView);
        if(loginWeb!=null){loginWeb.stopLoading();loginWeb.destroy();}
        loginView=null;loginWeb=null;
        JavaScriptReplyProxy r=loginReply;String id=loginId;loginReply=null;loginId=null;
        if(r==null)return;
        if(ok){CookieManager.getInstance().flush();try{respond(r,id,new JSONObject().put("authorized",true),null);}catch(JSONException e){respond(r,id,new JSONObject(),null);}}
        else respond(r,id,null,"Đã đóng màn hình đăng nhập.");
    }
    private void stopPlayer(){
        ++playSequence;
        if(fullscreen)setFullscreen(false);
        if(player!=null){player.stop();player.clearMediaItems();}
        if(playerView!=null)playerView.setVisibility(View.GONE);
        playingId=null;
        if(shortOverlay!=null)shortOverlay.setVisibility(View.GONE);
    }
    private void openExternal(String url){
        Uri u=Uri.parse(url);if(!"https".equals(u.getScheme())&& !"http".equals(u.getScheme()))return;
        try{startActivity(new Intent(Intent.ACTION_VIEW,u).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException ignored){}
    }
    private void hideFullscreen(){
        if(fullView==null)return;root.removeView(fullView);fullView=null;web.setVisibility(View.VISIBLE);if(fullCallback!=null)fullCallback.onCustomViewHidden();fullCallback=null;
        if(Build.VERSION.SDK_INT>=30)getWindow().getInsetsController().show(WindowInsets.Type.systemBars());
    }
    @Override public void onBackPressed(){if(loginView!=null){if(loginWeb!=null&&loginWeb.canGoBack())loginWeb.goBack();else finishWebLogin(false);return;}if(fullscreen){setFullscreen(false);return;}if(fullView!=null)hideFullscreen();else web.evaluateJavascript("window.onNativeBack&&onNativeBack()",null);}
    @Override protected void onPause(){super.onPause();if(player!=null)player.pause();if(web!=null){web.evaluateJavascript("window.pausePlayer&&pausePlayer()",null);web.onPause();}}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){workers.shutdownNow();if(player!=null){player.release();player=null;}if(web!=null){web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}
