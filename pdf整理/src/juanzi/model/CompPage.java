package juanzi.model;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 拼版结果中的一页。默认 A4 纵向：595 x 842 点。
 */
public class CompPage {

    public static final double A4_WIDTH = 595;
    public static final double A4_HEIGHT = 842;

    private String title;
    private double width;
    private double height;
    private final List<PlacedImage> items = new ArrayList<>();

    public CompPage(String title, double width, double height) {
        this.title = title;
        this.width = width;
        this.height = height;
    }

    public static CompPage a4(String title) {
        return new CompPage(title, A4_WIDTH, A4_HEIGHT);
    }

    public CompPage deepCopy() {
        CompPage p = new CompPage(title, width, height);
        for (PlacedImage it : items) {
            p.items.add(it.copy());
        }
        return p;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public void setSize(double w, double h) {
        this.width = w;
        this.height = h;
    }

    public List<PlacedImage> getItems() {
        return items;
    }

    public void add(PlacedImage item) {
        items.add(item);
    }

    public void remove(PlacedImage item) {
        items.remove(item);
    }

    /** 按缩放倍率把该页渲染成位图，输出 PDF 与预览共用。 */
    public BufferedImage render(double scale) {
        int w = Math.max(1, (int) Math.round(width * scale));
        int h = Math.max(1, (int) Math.round(height * scale));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        for (PlacedImage it : items) {
            it.paint(g, scale);
        }
        g.dispose();
        return img;
    }

    /** 该页所有内容的最低边界（点坐标），空页返回 null。 */
    public java.awt.geom.Rectangle2D contentBounds() {
        java.awt.geom.Rectangle2D r = null;
        for (PlacedImage it : items) {
            java.awt.geom.Rectangle2D b = it.rotatedBounds();
            r = (r == null) ? b : r.createUnion(b);
        }
        return r;
    }
}
