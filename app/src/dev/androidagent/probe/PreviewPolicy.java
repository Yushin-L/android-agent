package dev.androidagent.probe;

import java.net.URI;
import java.io.IOException;

/** Local preview origin is deliberately independent of authenticated app endpoints. */
public final class PreviewPolicy {
    public static final String ORIGIN="https://workspace.invalid/";
    public static String path(String url)throws IOException{
        try{URI u=new URI(url);if(!"https".equals(u.getScheme())||!"workspace.invalid".equals(u.getHost())||u.getPort()!=-1||u.getUserInfo()!=null)throw new IOException("PREVIEW_ORIGIN");
            String p=u.getPath();if(p==null||!p.startsWith("/"))throw new IOException("PREVIEW_PATH");p=p.substring(1);
            for(String component:p.split("/",-1))if(component.equals("..")||component.indexOf('\\')>=0||component.indexOf('\0')>=0)throw new IOException("PREVIEW_PATH");return p;
        }catch(java.net.URISyntaxException e){throw new IOException("PREVIEW_URL",e);}
    }
    public static String url(String path)throws Exception{return new URI("https","workspace.invalid","/"+path,null).toASCIIString();}
    public static String linkedPath(String document,String target)throws Exception{
        if(target.startsWith("#"))return document;
        URI link=new URI(target);if(link.isAbsolute()||link.getRawAuthority()!=null)throw new IOException("EXTERNAL_LINK");
        return path(new URI(url(document)).resolve(link).toASCIIString());
    }
}
