import dev.androidagent.probe.WorkspaceStore;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

public class WorkspaceStoreTest {
    interface Checked { void run() throws Exception; }
    static void rejected(Checked call) throws Exception {
        try {call.run();} catch(java.io.IOException expected) {return;}
        throw new AssertionError("Expected rejection");
    }
    static void check(boolean value) {if(!value) throw new AssertionError();}
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("workspaces-test-");
        Path file=root.resolve("mapping.json");
        try {
            WorkspaceStore store=new WorkspaceStore(file);
            String a=store.create("여행 계획"), b=store.create("휴대폰 정리");
            check(!a.equals(b));
            store.attach(a,"session-1");store.saveUi(a,"session-1","작성 중",120);
            store.attach(a,"session-2");store.attach(b,"session-3");
            rejected(() -> store.select(b,"session-1"));
            rejected(() -> store.attach(b,"session-1"));
            rejected(() -> store.create("  "));
            rejected(() -> store.saveUi(b,"session-1","wrong",0));
            store.select(a,"session-1");
            WorkspaceStore restored=new WorkspaceStore(file);
            JSONObject w=restored.get(a);
            check(w.getString("activeSession").equals("session-1"));
            check(w.getJSONArray("sessions").length()==2);
            check(w.getJSONArray("sessions").getJSONObject(0).getString("draft").equals("작성 중"));
            check(w.getJSONArray("sessions").getJSONObject(0).getInt("scrollY")==120);
            w.put("name","external mutation");check(restored.get(a).getString("name").equals("여행 계획"));
            restored.attach(a,"session-1");check(restored.get(a).getJSONArray("sessions").length()==2);
            // A failed disk write must roll back in-memory state, without destroying prior data.
            Path obstruction=root.resolve("directory");Files.createDirectory(obstruction);
            Path target=obstruction.resolve("mapping.json");WorkspaceStore broken=new WorkspaceStore(target);
            Files.delete(obstruction);Files.write(obstruction,new byte[]{1});
            rejected(() -> broken.create("should roll back"));check(broken.list().length()==0);
            String raw=new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
            check(!raw.contains("messages")&&!raw.contains("transcript"));
            System.out.println("PASS workspace persistence, ownership isolation, UI restoration, defensive snapshots, atomic rollback");
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(root)) {
                for(Path p:(Iterable<Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(p);
            }
        }
    }
}
