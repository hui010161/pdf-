import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
/**
 * Writes an ASCII-only javac @argfile listing every .java under the source root.
 * Paths are emitted relative to the current directory so the file stays pure ASCII
 * even when the project lives in a path with non-ASCII characters.
 *
 * usage: java GenList.java <outFile> <sourceRoot>
 */
public class GenList {
    public static void main(String[] a) throws Exception {
        Path out = Paths.get(a[0]).toAbsolutePath();
        Path root = Paths.get(a[1]).toAbsolutePath();
        Path cwd = Paths.get("").toAbsolutePath();
        StringBuilder sb = new StringBuilder();
        try (var s = Files.walk(root)) {
            s.filter(Files::isRegularFile)
             .filter(p -> p.getFileName().toString().endsWith(".java"))
             .sorted()
             .forEach(p -> sb.append(cwd.relativize(p).toString().replace('\\', '/')).append('\n'));
        }
        Files.write(out, sb.toString().getBytes(StandardCharsets.US_ASCII));
        System.out.println("sources: " + (sb.length() == 0 ? 0 : sb.toString().split("\n").length) + " files -> " + out);
    }
}