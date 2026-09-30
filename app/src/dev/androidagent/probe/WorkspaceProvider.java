package dev.androidagent.probe;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.file.*;

/** Read-only, explicitly granted URI access to one workspace file. */
public final class WorkspaceProvider extends ContentProvider {
    public static final String AUTHORITY="dev.androidagent.probe.files";
    public static Uri uri(String owner,String path){return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(owner).appendPath(path).build();}
    private File file(Uri uri)throws Exception{
        if(!AUTHORITY.equals(uri.getAuthority())||uri.getPathSegments().size()!=2)throw new IOException("INVALID_URI");
        String owner=uri.getPathSegments().get(0);new WorkspaceStore(new File(getContext().getFilesDir(),"workspaces.json").toPath()).get(owner);
        WorkspaceFiles files=new WorkspaceFiles(new File(getContext().getFilesDir(),"workspaces").toPath(),new File(getContext().getCacheDir(),"file-jobs").toPath());
        Path path=files.resolve(owner,uri.getPathSegments().get(1));if(!Files.isRegularFile(path))throw new IOException("FILE_UNAVAILABLE");return path.toFile();
    }
    public static String mime(String name){if(name==null)return "application/octet-stream";int dot=name.lastIndexOf('.');String type=dot<0?null:android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot+1).toLowerCase(java.util.Locale.ROOT));return type==null?"application/octet-stream":type;}
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){try{return mime(file(uri).getName());}catch(Exception e){return "application/octet-stream";}}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File file=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++){if(columns[i].equals(OpenableColumns.DISPLAY_NAME))values[i]=file.getName();else if(columns[i].equals(OpenableColumns.SIZE))values[i]=file.length();}cursor.addRow(values);return cursor;}catch(Exception e){return null;}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{try{if(!"r".equals(mode))throw new IOException("READ_ONLY");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}catch(Exception e){throw new FileNotFoundException("File unavailable");}}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
}
