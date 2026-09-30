package dev.androidagent.probe;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import org.json.*;

/** Import only the owning session's Codex-generated artifact, preserving its source. */
public final class GeneratedImages {
    private final WorkspaceFiles files;private final Path home;
    public GeneratedImages(WorkspaceFiles files,Path home){this.files=files;this.home=home.toAbsolutePath().normalize();}
    public JSONObject retain(String owner,String thread,JSONObject item)throws Exception{
        if(!"completed".equals(item.optString("status")))throw new IOException("IMAGE_NOT_COMPLETED");
        String saved=item.optString("savedPath");if(saved.isEmpty())throw new IOException("IMAGE_PATH_UNAVAILABLE");
        Path source=Paths.get(saved).toAbsolutePath().normalize(),root=files.root(owner);
        if(source.startsWith(root)){source=files.resolve(owner,root.relativize(source).toString());if(!Files.isRegularFile(source))throw new IOException("IMAGE_MISSING");return files.info(owner,source);}
        Path allowed=home.resolve("generated_images").resolve(thread.replaceAll("[^a-zA-Z0-9_-]","_"));
        if(!source.startsWith(allowed)||!Files.isRegularFile(source)||Files.size(source)>WorkspaceFiles.MAX_BYTES)throw new IOException("IMAGE_PATH_UNAVAILABLE");
        Path part=home;for(Path segment:home.relativize(source)){part=part.resolve(segment);if(Files.isSymbolicLink(part))throw new IOException("INVALID_IMAGE_PATH");}
        String name="generated-"+hex(MessageDigest.getInstance("SHA-256").digest((thread+":"+item.getString("id")).getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,24)+".png";
        Path destination=files.resolve(owner,name);
        if(Files.exists(destination)){if(!java.util.Arrays.equals(digest(source),digest(destination)))throw new IOException("IMAGE_NAME_CONFLICT");return files.info(owner,destination);}
        try(InputStream in=Files.newInputStream(source)){return files.copyIn(owner,name,in,()->false);}
    }
    private static byte[] digest(Path path)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(path)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}return digest.digest();}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
}
