package juanzi.pdf;

import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * 一个已导入的源 PDF 文档。
 */
public class SourceDoc {

    private final File file;
    private final PDDocument document;
    private final PDFRenderer renderer;

    public SourceDoc(File file) throws IOException {
        this.file = file;
        this.document = Loader.loadPDF(file);
        this.renderer = new PDFRenderer(document);
    }

    public File getFile() {
        return file;
    }

    public String getDisplayName() {
        return file.getName();
    }

    public int getPageCount() {
        return document.getNumberOfPages();
    }

    public PDPage page(int index) {
        return document.getPage(index);
    }

    /** 页面的显示尺寸（已考虑 /Rotate）。 */
    public Rectangle2D pageSize(int index) {
        PDPage page = page(index);
        PDRectangle box = page.getCropBox() != null ? page.getCropBox() : page.getMediaBox();
        int rot = ((page.getRotation() % 360) + 360) % 360;
        float w = box.getWidth();
        float h = box.getHeight();
        if (rot == 90 || rot == 270) {
            return new Rectangle2D.Float(0, 0, h, w);
        }
        return new Rectangle2D.Float(0, 0, w, h);
    }

    public Dimension2D pageDimension(int index) {
        Rectangle2D r = pageSize(index);
        return new java.awt.geom.Dimension2D() {
            @Override public double getWidth() { return r.getWidth(); }
            @Override public double getHeight() { return r.getHeight(); }
            @Override public void setSize(double w, double h) { }
        };
    }

    /**
     * 把第 index 页渲染为位图。
     *
     * @param dpi 目标分辨率
     */
    public java.awt.image.BufferedImage render(int index, float dpi) throws IOException {
        return renderer.renderImageWithDPI(index, dpi);
    }

    /** 按最长边限制渲染，避免超大内存占用。 */
    public java.awt.image.BufferedImage renderLimited(int index, float dpi, int maxPixelsPerSide) throws IOException {
        float scale = dpi / 72f;
        Rectangle2D r = pageSize(index);
        double w = r.getWidth() * scale;
        double h = r.getHeight() * scale;
        double max = Math.max(w, h);
        if (max > maxPixelsPerSide) {
            scale = (float) (scale * maxPixelsPerSide / max);
        }
        return renderer.renderImage(index, scale);
    }

    public void close() {
        try {
            document.close();
        } catch (IOException ignored) {
            // 关闭失败无需处理
        }
    }
}
