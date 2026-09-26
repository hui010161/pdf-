package juanzi.model;

import java.awt.image.BufferedImage;

/**
 * 一次框选产生的内容块。
 */
public class SelectionItem {

    private final String name;
    /** 高分辨率位图（导出用）。 */
    private final BufferedImage image;
    /** 缩略图（列表显示用）。 */
    private final BufferedImage thumbnail;
    /** 内容区在底图上的像素尺寸，用于换算成版面点尺寸（保持原始物理大小）。 */
    private final double sourceWidthPx;
    private final double sourceHeightPx;

    public SelectionItem(String name, BufferedImage image, BufferedImage thumbnail,
                         double sourceWidthPx, double sourceHeightPx) {
        this.name = name;
        this.image = image;
        this.thumbnail = thumbnail;
        this.sourceWidthPx = Math.max(1, sourceWidthPx);
        this.sourceHeightPx = Math.max(1, sourceHeightPx);
    }

    public String getName() {
        return name;
    }

    public BufferedImage getImage() {
        return image;
    }

    public BufferedImage getThumbnail() {
        return thumbnail;
    }

    /** 该内容块在原始页面上的物理尺寸（点）。 */
    public double getPointWidth() {
        return sourceWidthPx;
    }

    public double getPointHeight() {
        return sourceHeightPx;
    }

    @Override
    public String toString() {
        return name;
    }
}
