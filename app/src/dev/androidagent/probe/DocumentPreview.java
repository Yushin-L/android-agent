package dev.androidagent.probe;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Read-only document viewer. File bytes are served only from the selected workspace. */
final class DocumentPreview {
    private final AgentActivity activity;
    private final AgentService service;
    private final String owner,path,source;
    private final boolean html;
    private final Consumer<String> link;
    private final Dialog dialog;
    private final FrameLayout body;
    private WebView web;
    private MarkdownView markdown;
    private boolean raw,closed;
    DocumentPreview(AgentActivity activity,AgentService service,String owner,String path,String source,boolean html,Consumer<String> link){
        this.activity=activity;this.service=service;this.owner=owner;this.path=path;this.source=source;this.html=html;this.link=link;
        dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setFitsSystemWindows(true);root.setBackgroundColor(activity.fileColor("background"));root.setPadding(dp(12),dp(8),dp(12),dp(8));
        LinearLayout toolbar=new LinearLayout(activity);toolbar.setGravity(Gravity.CENTER_VERTICAL);root.addView(toolbar);
        Button close=new Button(activity);close.setText("닫기");close.setMinHeight(dp(48));close.setOnClickListener(v->close());toolbar.addView(close);
        TextView title=new TextView(activity);title.setText(new File(path).getName());title.setTextSize(17);title.setTypeface(activity.fileFont());title.setTextColor(activity.fileColor("text"));title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button mode=new Button(activity);mode.setText("원문");mode.setMinHeight(dp(48));mode.setOnClickListener(v->{raw=!raw;mode.setText(raw?"미리보기":"원문");render();});toolbar.addView(mode);
        body=new FrameLayout(activity);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(root);dialog.setOnDismissListener(d->{closed=true;dispose();});
    }
    void show(){dialog.show();dialog.getWindow().setBackgroundDrawable(new ColorDrawable(activity.fileColor("background")));dialog.getWindow().setLayout(-1,-1);render();}
    void close(){closed=true;dispose();dialog.dismiss();}
    boolean showing(){return !closed&&dialog.isShowing();}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private void dispose(){if(markdown!=null){markdown.release();markdown=null;}if(web!=null){body.removeView(web);web.stopLoading();web.destroy();web=null;}}
    private void render(){dispose();body.removeAllViews();
        if(raw){TextView text=new TextView(activity);text.setText(source);text.setTextSize(15);text.setTypeface(Typeface.MONOSPACE);text.setTextColor(activity.fileColor("text"));text.setTextIsSelectable(true);text.setPadding(dp(12),dp(12),dp(12),dp(12));ScrollView scroll=new ScrollView(activity);scroll.addView(text);body.addView(scroll);return;}
        if(!html){ScrollView scroll=new ScrollView(activity);markdown=new MarkdownView(activity,link,()->false,()->{});scroll.addView(markdown);body.addView(scroll);markdown.setMarkdown(source);return;}
        web=new WebView(activity);web.setBackgroundColor(activity.fileColor("background"));WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);settings.setDomStorageEnabled(false);settings.setDatabaseEnabled(false);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSaveFormData(false);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setTextZoom(Math.round(activity.getResources().getConfiguration().fontScale*100));
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                if(!request.getMethod().equals("GET"))return denied();
                try{String relative=PreviewPolicy.path(request.getUrl().toString());service.store.get(owner);
                    if(relative.equals("_agent_preview_font.otf"))return new WebResourceResponse("font/otf",null,200,"OK",headers(),activity.getAssets().open("fonts/Pretendard-Regular.otf"));
                    if(relative.equals(path))return new WebResourceResponse("text/html","UTF-8",200,"OK",headers(),new ByteArrayInputStream(styledHtml().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    Path file=service.files.resolve(owner,relative);
                    if(!Files.isRegularFile(file)||Files.size(file)>WorkspaceFiles.MAX_BYTES)return denied();
                    String mime=WorkspaceProvider.mime(relative);if(relative.toLowerCase(Locale.ROOT).endsWith(".css"))mime="text/css";
                    return new WebResourceResponse(mime,mime.startsWith("text/")?"UTF-8":null,200,"OK",headers(),Files.newInputStream(file));
                }catch(Exception e){return denied();}
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(request.isForMainFrame()&&request.hasGesture()){
                    String target=request.getUrl().toString();
                    try{if(path.equals(PreviewPolicy.path(target))&&request.getUrl().getFragment()!=null)return false;}catch(Exception ignored){}
                    try{link.accept(PreviewPolicy.url(PreviewPolicy.path(target)));}catch(Exception e){if(MarkdownDocument.webLink(target))link.accept(target);}
                }return true;
            }
        });
        body.addView(web,new FrameLayout.LayoutParams(-1,-1));
        try{web.loadUrl(PreviewPolicy.url(path));}catch(Exception e){close();}
    }
    private String styledHtml(){
        String ink=String.format(java.util.Locale.ROOT,"#%06x",activity.fileColor("text")&0xffffff),bg=String.format(java.util.Locale.ROOT,"#%06x",activity.fileColor("background")&0xffffff),surface=String.format(java.util.Locale.ROOT,"#%06x",activity.fileColor("surface")&0xffffff);
        String head="<meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>@font-face{font-family:Agent;src:url('"+PreviewPolicy.ORIGIN+"_agent_preview_font.otf')}html{color-scheme:light dark}body{font:17px/1.65 Agent,sans-serif;color:"+ink+";background:"+bg+";margin:20px;overflow-wrap:anywhere}img{max-width:100%;height:auto}pre{overflow:auto;padding:16px;background:"+surface+";border-radius:12px}table{border-collapse:collapse;display:block;overflow:auto}th,td{padding:10px 14px;border:1px solid #81958a}blockquote{border-left:3px solid #087f5b;padding-left:16px;margin-left:0}</style>";
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(?i)<head(?:\\s[^>]*)?>").matcher(source);
        return matcher.find()?source.substring(0,matcher.end())+head+source.substring(matcher.end()):head+source;
    }
    private static Map<String,String> headers(){Map<String,String> h=new HashMap<>();h.put("Content-Security-Policy","default-src 'none'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self'; script-src 'none'; connect-src 'none'; frame-src 'none'; object-src 'none'; form-action 'none'; base-uri 'none'");h.put("X-Content-Type-Options","nosniff");h.put("Cache-Control","no-store");return h;}
    private static WebResourceResponse denied(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",headers(),new ByteArrayInputStream(new byte[0]));}
}
