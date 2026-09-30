import dev.androidagent.probe.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WorkspaceFilesTest {
 interface Work{void run()throws Exception;}
 static void reject(Work work)throws Exception{try{work.run();throw new AssertionError("accepted unsafe operation");}catch(IOException expected){}}
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  Path base=Files.createTempDirectory("workspace-files"),tmp=base.resolve("cache");WorkspaceFiles files=new WorkspaceFiles(base.resolve("workspaces"),tmp);
  String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();
  check(files.list(a,"").length()==0);files.write(a,"plan.md","안녕하세요",false);check(files.read(a,"plan.md").equals("안녕하세요"));check(files.list(b,"").length()==0);
  reject(()->files.read(a,"../"+b+"/plan.md"));reject(()->files.write(a,"/tmp/escape","no",true));reject(()->files.write(a,"plan.md","overwrite",false));
  Path outside=Files.createTempDirectory("outside");Files.createSymbolicLink(files.root(a).resolve("link"),outside);reject(()->files.write(a,"link/no.txt","no",false));
  files.mkdir(a,"notes/sub");files.write(a,"notes/sub/free.txt","free layout",false);check(files.read(a,"notes/sub/free.txt").equals("free layout"));
  JSONObject imported=files.copyIn(a,"plan.md",new ByteArrayInputStream("second".getBytes(StandardCharsets.UTF_8)),()->false);check(imported.getString("path").equals("plan (1).md"));check(files.read(a,"plan.md").equals("안녕하세요"));
  reject(()->files.copyIn(a,"cancel.txt",new ByteArrayInputStream(new byte[1024]),()->true));check(!Files.exists(files.resolve(a,"cancel.txt")));
  InputStream huge=new InputStream(){long count;public int read(){return count++>WorkspaceFiles.MAX_BYTES?-1:0;}public int read(byte[] b,int off,int len){if(count>WorkspaceFiles.MAX_BYTES)return -1;Arrays.fill(b,off,off+len,(byte)0);count+=len;return len;}};
  reject(()->files.copyIn(a,"huge.bin",huge,()->false));check(!Files.exists(files.resolve(a,"huge.bin")));try(java.util.stream.Stream<Path> entries=Files.list(tmp)){check(entries.count()==0);}
  reject(()->files.download(a,"http://localhost/test","file",()->false));
  java.net.URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new java.net.URLStreamHandler(){
   protected java.net.URLConnection openConnection(java.net.URL url){return new java.net.HttpURLConnection(url){
    public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
    public int getResponseCode(){return url.getPath().equals("/redirect")||url.getPath().equals("/insecure")?302:url.getPath().equals("/fail")?403:200;}
    public String getHeaderField(String name){return url.getPath().equals("/insecure")?"http://fixture/file":"https://fixture/file";}
    public long getContentLengthLong(){return 8;}
    public InputStream getInputStream(){return new ByteArrayInputStream("download".getBytes(StandardCharsets.UTF_8));}
   };}
  }:null);
  JSONObject downloaded=files.download(a,"https://fixture/redirect","download.txt",()->false);check(files.read(a,downloaded.getString("path")).equals("download"));check(downloaded.getLong("size")==8);
  reject(()->files.download(a,"https://fixture/insecure","no.txt",()->false));reject(()->files.download(a,"https://fixture/fail","no.txt",()->false));check(!Files.exists(files.resolve(a,"no.txt")));
  files.write(b,"keep.txt","keep",false);files.delete(a);check(files.read(b,"keep.txt").equals("keep"));check(Files.exists(outside));
  WorkspaceStore store=new WorkspaceStore(base.resolve("mapping.json"));String owner=store.create("files");store.attach(owner,"session");JSONArray attachments=new JSONArray().put(new JSONObject().put("path","photo.png").put("mime","image/png"));store.attachments(owner,"session",attachments);
  WorkspaceStore restarted=new WorkspaceStore(base.resolve("mapping.json"));check(restarted.attachments(owner,"session").length()==1);check(restarted.owner("session").equals(owner));restarted.attach(owner,"new-session");check(restarted.attachments(owner,"new-session").length()==0);check(restarted.attachments(owner,"session").length()==1);
  restarted.appendAttachment(owner,"session",new JSONObject().put("path","later.txt"));restarted.consumeAttachments(owner,"session",attachments);check(restarted.attachments(owner,"session").getJSONObject(0).getString("path").equals("later.txt"));
  System.out.println("PASS workspace isolation, traversal/symlink rejection, free directories, no overwrite, cancelled/oversize copy cleanup, HTTPS restriction, delete isolation, durable session attachments");
 }
}
