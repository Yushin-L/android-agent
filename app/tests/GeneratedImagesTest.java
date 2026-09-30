import dev.androidagent.probe.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;
public class GeneratedImagesTest {
 interface Work{void run()throws Exception;}
 static void reject(Work work)throws Exception{try{work.run();throw new AssertionError("accepted invalid image source");}catch(java.io.IOException expected){}}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("images"),home=Files.createDirectories(root.resolve("home"));String owner=UUID.randomUUID().toString(),thread="session-a";
  WorkspaceFiles files=new WorkspaceFiles(root.resolve("workspaces"),root.resolve("temp"));GeneratedImages images=new GeneratedImages(files,home);
  Path source=Files.createDirectories(home.resolve("generated_images").resolve(thread)).resolve("call.png");byte[] bytes={-119,80,78,71,0,1,2};Files.write(source,bytes);
  JSONObject item=new JSONObject().put("id","call").put("status","completed").put("savedPath",source.toString());JSONObject saved=images.retain(owner,thread,item);
  if(!Arrays.equals(bytes,Files.readAllBytes(files.resolve(owner,saved.getString("path"))))||!Files.exists(source))throw new AssertionError("copy mismatch");
  if(!saved.getString("path").equals(images.retain(owner,thread,item).getString("path"))||files.list(owner,"").length()!=1)throw new AssertionError("duplicate import");
  reject(()->images.retain(owner,"session-b",item));
  Path secret=home.resolve("auth.json");Files.write(secret,new byte[]{1});reject(()->images.retain(owner,thread,new JSONObject(item.toString()).put("savedPath",secret.toString())));
  Path link=source.getParent().resolve("link.png");Files.createSymbolicLink(link,secret);reject(()->images.retain(owner,thread,new JSONObject(item.toString()).put("savedPath",link.toString())));
  reject(()->images.retain(owner,thread,new JSONObject(item.toString()).put("status","failed")));
  Files.write(files.resolve(owner,saved.getString("path")),new byte[]{9});reject(()->images.retain(owner,thread,item));
  System.out.println("PASS generated-image binary copy, original retained, idempotent resume, ownership/auth/symlink denial, conflict preservation");
 }
}
