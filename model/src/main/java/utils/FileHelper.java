package utils;
import java.io.File;
import java.nio.file.Files;
import java.util.Objects;


public class FileHelper {
    static public String getFileName(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }

    static public void deleteDir(File dir) {
        if (dir.exists()) {
            for (File f: Objects.requireNonNull(dir.listFiles())) {
                if (f.isDirectory()) {
                    deleteDir(f);
                } else {
                    f.delete();
                }
            }
            dir.delete();
        }
    }

    static public void createDir(File dir) {
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
