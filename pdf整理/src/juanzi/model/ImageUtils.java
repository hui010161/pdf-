package juanzi.model;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 图片小工具。
 */
public final class ImageUtils {

    private ImageUtils() {
    }

    /** 生成用于列表显示的缩略图（等比缩放，不放大）。 */
    public static BufferedImage makeThumb(BufferedImage src) {
        return makeThumb(src, 160, 110);
    }

    public static BufferedImage makeThumb(BufferedImage src, int maxW, int maxH) {
        double s = Math.min(maxW / (double) src.getWidth(), maxH / (double) src.getHeight());
        s = Math.min(s, 1.0);
        int w = Math.max(1, (int) (src.getWidth() * s));
        int h = Math.max(1, (int) (src.getHeight() * s));
        BufferedImage thumb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return thumb;
    }
}
