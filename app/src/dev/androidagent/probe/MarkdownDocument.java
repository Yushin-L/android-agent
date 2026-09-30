package dev.androidagent.probe;

import java.net.URI;
import java.util.*;
import org.commonmark.Extension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;

public final class MarkdownDocument {
    public static final int RENDER_LIMIT=200000;
    public static Node parse(String source){
        List<Extension> extensions=Arrays.asList(TablesExtension.create(),StrikethroughExtension.create());
        return Parser.builder().extensions(extensions).build().parse(source);
    }
    public static boolean webLink(String value){
        try{URI u=new URI(value);return ("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()))&&u.getHost()!=null&&u.getUserInfo()==null;}
        catch(Exception e){return false;}
    }
}
