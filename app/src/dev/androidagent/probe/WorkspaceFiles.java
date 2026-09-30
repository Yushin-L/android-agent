package dev.androidagent.probe;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.net.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** File operations confined to a GUI workspace. This is not an OS sandbox. */
public final class WorkspaceFiles {
    public static final long MAX_BYTES=50L*1024*1024;
    private final Path base,temporary;
    public WorkspaceFiles(Path base,Path temporary)throws IOException{this.base=base.toAbsolutePath().normalize();this.temporary=temporary;Files.createDirectories(this.base);Files.createDirectories(temporary);}
    public Path root(String id)throws IOException{
        if(!UUID.fromString(id).toString().equals(id))throw new IOException("INVALID_WORKSPACE");
        Path p=base.resolve(id);if(Files.isSymbolicLink(p))throw new IOException("INVALID_PATH");Files.createDirectories(p);return p;
    }
    public Path resolve(String id,String name)throws IOException{
        Path root=root(id),relative=Paths.get(name);if(relative.isAbsolute())throw new IOException("INVALID_PATH");
        Path p=root.resolve(relative).normalize();if(!p.startsWith(root))throw new IOException("INVALID_PATH");
        Path part=root;for(Path segment:root.relativize(p)){part=part.resolve(segment);if(Files.isSymbolicLink(part))throw new IOException("INVALID_PATH");}return p;
    }
    public static String safeName(String name){String s=name==null?"file":name.replaceAll("[\\\\/\\p{Cntrl}]","_");if(s.equals(".")||s.equals("..")||s.trim().isEmpty())s="file";while(s.getBytes(StandardCharsets.UTF_8).length>180)s=s.substring(s.offsetByCodePoints(0,1));return s;}
    public JSONObject info(String id,Path p)throws Exception{return new JSONObject().put("path",root(id).relativize(p).toString()).put("size",Files.isDirectory(p)?0:Files.size(p)).put("directory",Files.isDirectory(p));}
    public JSONArray list(String id,String directory)throws Exception{
        Path p=resolve(id,directory);JSONArray a=new JSONArray();try(DirectoryStream<Path> entries=Files.newDirectoryStream(p)){for(Path file:entries){if(Files.isSymbolicLink(file))continue;if(a.length()>=1000)break;a.put(info(id,file));}}return a;
    }
    public String read(String id,String path)throws Exception{
        Path p=resolve(id,path);if(Files.size(p)>1024*1024)throw new IOException("TEXT_TOO_LARGE");
        byte[] bytes=Files.readAllBytes(p);return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }
    public synchronized JSONObject write(String id,String path,String content,boolean overwrite)throws Exception{
        byte[] bytes=content.getBytes(StandardCharsets.UTF_8);if(bytes.length>1024*1024)throw new IOException("TEXT_TOO_LARGE");Path p=resolve(id,path);
        if(p.equals(root(id)))throw new IOException("INVALID_PATH");if(Files.exists(p)&&!overwrite)throw new IOException("FILE_EXISTS");
        Files.createDirectories(p.getParent());Path tmp=Files.createTempFile(temporary,"write-",".part");try{Files.write(tmp,bytes);Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);return info(id,p);}finally{Files.deleteIfExists(tmp);}
    }
    public JSONObject mkdir(String id,String path)throws Exception{Path p=resolve(id,path);Files.createDirectories(p);return info(id,p);}
    public JSONObject copyIn(String id,String name,InputStream in,BooleanSupplier cancelled)throws Exception{
        Path temp=Files.createTempFile(temporary,"import-",".part");long count=0,deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
        try{try(OutputStream out=Files.newOutputStream(temp)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){if(cancelled.getAsBoolean())throw new IOException("CANCELLED");if(System.nanoTime()>deadline)throw new IOException("TIME_LIMIT");count+=n;if(count>MAX_BYTES)throw new IOException("FILE_TOO_LARGE");out.write(b,0,n);}}
            synchronized(this){if(cancelled.getAsBoolean())throw new IOException("CANCELLED");String safe=safeName(name);Path target=resolve(id,safe);int n=1;int dot=safe.lastIndexOf('.');String stem=dot>0?safe.substring(0,dot):safe,ext=dot>0?safe.substring(dot):"";while(Files.exists(target))target=resolve(id,stem+" ("+(n++)+")"+ext);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);return info(id,target);}
        }finally{Files.deleteIfExists(temp);}
    }
    public JSONObject download(String id,String url,String name,BooleanSupplier cancelled)throws Exception{
        URL target=new URL(url);
        for(int redirect=0;redirect<6;redirect++){
            if(cancelled.getAsBoolean())throw new IOException("CANCELLED");
            if(!target.getProtocol().equals("https")||target.getUserInfo()!=null)throw new IOException("HTTPS_REQUIRED");
            HttpURLConnection c=(HttpURLConnection)target.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
            try{int status=c.getResponseCode();if(status>=300&&status<400){String next=c.getHeaderField("Location");if(next==null)throw new IOException("DOWNLOAD_REDIRECT");target=new URL(target,next);continue;}
                if(status<200||status>=300)throw new IOException("DOWNLOAD_HTTP_"+status);if(c.getContentLengthLong()>MAX_BYTES)throw new IOException("FILE_TOO_LARGE");
                try(InputStream in=c.getInputStream()){return copyIn(id,name,in,cancelled);}
            }finally{c.disconnect();}
        }throw new IOException("DOWNLOAD_REDIRECT_LIMIT");
    }
    /** Resolve a clicked chat/document link into this workspace only. */
    public synchronized String linkedFile(String id,String document,String target)throws Exception{
        java.net.URI uri=new java.net.URI(target.replace(" ","%20"));
        String scheme=uri.getScheme();
        if(uri.getRawAuthority()!=null||uri.isOpaque()||(scheme!=null&&!scheme.equalsIgnoreCase("file")))throw new IOException("EXTERNAL_LINK");
        String path=uri.getPath();if(path==null)throw new IOException("FILE_LINK");
        Path base=root(id),candidate;
        if(path.startsWith("/")){
            candidate=java.nio.file.Paths.get(path).normalize();
            if(candidate.startsWith(base))path=base.relativize(candidate).toString();
            else{Path realBase=base.toRealPath(),real=candidate.toRealPath();if(!real.startsWith(realBase))throw new IOException("OUTSIDE_WORKSPACE");path=realBase.relativize(real).toString();}
        }else{
            if(scheme!=null)throw new IOException("FILE_LINK");
            Path parent=document.isEmpty()?java.nio.file.Paths.get(""):java.nio.file.Paths.get(document).getParent();
            path=path.isEmpty()?document:(parent==null?java.nio.file.Paths.get(path):parent.resolve(path)).toString();
        }
        candidate=resolve(id,path);if(!Files.isRegularFile(candidate,java.nio.file.LinkOption.NOFOLLOW_LINKS))throw new IOException("FILE_NOT_FOUND");
        return base.relativize(candidate).toString();
    }
    /** Validate the entire selection before any mutation, and collapse overlapping paths. */
    public synchronized java.util.List<String> deletionPaths(String id,java.util.Collection<String> paths)throws IOException{
        java.util.Set<String> unique=new java.util.LinkedHashSet<>();
        for(String path:paths){Path target=resolve(id,path);String relative=root(id).relativize(target).toString();
            if(relative.isEmpty())throw new IOException("WORKSPACE_ROOT_DELETE_DENIED");
            if(!Files.exists(target,java.nio.file.LinkOption.NOFOLLOW_LINKS))throw new IOException("FILE_NOT_FOUND");unique.add(relative);}
        java.util.List<String> result=new java.util.ArrayList<>();
        for(String path:unique){boolean child=false;for(String parent:unique)if(path.startsWith(parent+"/")){child=true;break;}if(!child)result.add(path);}
        return result;
    }
    public synchronized void deleteEntry(String id,String name)throws Exception{
        Path workspace=root(id),target=resolve(id,name);if(target.equals(workspace))throw new IOException("WORKSPACE_ROOT_DELETE_DENIED");
        if(!Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("FILE_NOT_FOUND");
        // Default walk does not follow symbolic links, including links nested in a folder.
        Files.walkFileTree(target,new SimpleFileVisitor<Path>(){
            @Override public FileVisitResult visitFile(Path file,java.nio.file.attribute.BasicFileAttributes attrs)throws IOException{Files.delete(file);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult postVisitDirectory(Path folder,IOException failure)throws IOException{if(failure!=null)throw failure;Files.delete(folder);return FileVisitResult.CONTINUE;}
        });
    }
    public void delete(String id)throws Exception{Path root=root(id);try(java.util.stream.Stream<Path> walk=Files.walk(root)){for(Path p:(Iterable<Path>)walk.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
    public static JSONArray specs()throws Exception{
        JSONArray a=new JSONArray();
        a.put(spec("workspace_list","List files in this workspace directory. Paths are relative; use empty path for root.",new JSONObject().put("path",str()),"path"));
        a.put(spec("workspace_read","Read a UTF-8 text file, up to 1 MiB. Not a PDF/Office parser.",new JSONObject().put("path",str()),"path"));
        a.put(spec("workspace_write","Create or replace a UTF-8 file in this workspace, up to 1 MiB. Set overwrite true only when replacing intentionally.",new JSONObject().put("path",str()).put("content",str()).put("overwrite",new JSONObject().put("type","boolean")),"path","content","overwrite"));
        a.put(spec("workspace_mkdir","Create a directory within the workspace.",new JSONObject().put("path",str()),"path"));
        a.put(spec("workspace_download","Download a public HTTPS URL into this workspace (50 MiB limit). Supply a filename with correct extension. Does not inherit browser login. Returns actual saved path and size. Existing files are not overwritten.",new JSONObject().put("url",str()).put("name",str()),"url","name"));return a;
    }
    private static JSONObject str()throws Exception{return new JSONObject().put("type","string");}
    private static JSONObject spec(String name,String description,JSONObject fields,String...required)throws Exception{return new JSONObject().put("type","function").put("name",name).put("description",description).put("inputSchema",new JSONObject().put("type","object").put("properties",fields).put("required",new JSONArray(Arrays.asList(required))).put("additionalProperties",false));}
    public JSONObject invoke(String id,String tool,JSONObject args,BooleanSupplier cancelled)throws Exception{
        JSONObject result;
        if(cancelled.getAsBoolean())throw new IOException("CANCELLED");
        switch(tool){
            case "workspace_list":result=new JSONObject().put("files",list(id,args.getString("path")));break;
            case "workspace_read":result=new JSONObject().put("text",read(id,args.getString("path")));break;
            case "workspace_write":result=write(id,args.getString("path"),args.getString("content"),args.getBoolean("overwrite"));break;
            case "workspace_mkdir":result=mkdir(id,args.getString("path"));break;
            case "workspace_download":result=download(id,args.getString("url"),args.getString("name"),cancelled);break;
            default:throw new IOException("UNSUPPORTED_TOOL");
        }return result;
    }
}
