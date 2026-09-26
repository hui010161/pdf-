package juanzi.pdf;

import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import juanzi.model.CompPage;

/**
 * 把拼版结果导出为 PDF：每页按页面尺寸输出，页内每个贴图按“点”坐标绘制。
 */
public final class PdfExporter {

    /** 导出时的渲染倍率，2.0 表示按 144dpi 光栅化，保证打印清晰。 */
    public static final float RENDER_SCALE = 2.0f;

    private PdfExporter() {
    }

    public static JFileChooser newSaveChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出 PDF");
        chooser.setFileFilter(new FileNameExtensionFilter("PDF 文件 (*.pdf)", "pdf"));
        chooser.setSelectedFile(new java.io.File("错题组卷.pdf"));
        return chooser;
    }

    public static String normalizeExtension(String path) {
        if (path == null || path.isEmpty()) {
            return "错题组卷.pdf";
        }
        if (!path.toLowerCase().endsWith(".pdf")) {
            return path + ".pdf";
        }
        return path;
    }

    /**
     * 导出成品 PDF。
     *
     * @param pages 拼版页面列表（按顺序）
     * @param out   输出文件
     */
    public static void export(List<CompPage> pages, java.io.File out) throws java.io.IOException {
        if (pages.isEmpty()) {
            throw new java.io.IOException("没有可导出的页面");
        }
        try (PDDocument doc = new PDDocument()) {
            for (CompPage cp : pages) {
                java.awt.image.BufferedImage raster = cp.render(RENDER_SCALE);
                PDPage page = new PDPage(new PDRectangle((float) cp.getWidth(), (float) cp.getHeight()));
                doc.addPage(page);
                PDImageXObject xobj = LosslessFactory.createFromImage(doc, raster);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(xobj, 0, 0, (float) cp.getWidth(), (float) cp.getHeight());
                }
            }
            doc.save(out);
        }
    }

    /** 导出为多个 PNG（调试用，正常流程不需要）。 */
    public static List<java.io.File> exportPng(List<CompPage> pages, java.io.File dir, String prefix)
            throws java.io.IOException {
        List<java.io.File> result = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            java.io.File f = new java.io.File(dir, prefix + "-" + (i + 1) + ".png");
            ImageIO.write(pages.get(i).render(RENDER_SCALE), "png", f);
            result.add(f);
        }
        return result;
    }
}
