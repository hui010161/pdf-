import java.io.File;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

/** 打印 PDF 基本信息，并可把页面渲染成 PNG 以便肉眼查看。 */
public class PdfInfo {
    public static void main(String[] args) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new File(args[0]))) {
            System.out.println("pages=" + doc.getNumberOfPages() + " version=" + doc.getVersion());
            int i = 0;
            for (PDPage p : doc.getPages()) {
                PDRectangle b = p.getMediaBox();
                System.out.printf("page %d: %.1f x %.1f pt  rotation=%d%n",
                        i++, b.getWidth(), b.getHeight(), p.getRotation());
            }
            if (args.length > 1) {
                File dir = new File(args[1]);
                dir.mkdirs();
                PDFRenderer r = new PDFRenderer(doc);
                float dpi = args.length > 2 ? Float.parseFloat(args[2]) : 110f;
                for (int n = 0; n < doc.getNumberOfPages(); n++) {
                    File out = new File(dir, "page-" + (n + 1) + ".png");
                    ImageIO.write(r.renderImageWithDPI(n, dpi), "png", out);
                    System.out.println("wrote " + out);
                }
            }
        }
    }
}
