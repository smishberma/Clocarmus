import com.android.ide.common.vectordrawable.Svg2Vector;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Build-time adapter to the Android SDK's SVG importer; no app runtime dependency. */
public class ConvertPlayerSvg {
    public static void main(String[] args) throws Exception {
        try (var output = new ByteArrayOutputStream()) {
            String errors = Svg2Vector.parseSvgToXml(Path.of(args[0]), output);
            if (!errors.isBlank()) throw new IllegalArgumentException(errors);
            Files.write(Path.of(args[1]), output.toByteArray());
        }
    }
}
