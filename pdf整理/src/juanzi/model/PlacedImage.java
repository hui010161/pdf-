package juanzi.model;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * 拼版页上的一个贴图。坐标为“点”，原点在页面左上角，x 向右、y 向下。
 */
public class PlacedImage {

    private final SelectionItem source;
    private double x;
    private double y;
    private double width;
    private double height;
    /** 旋转角度，单位为度，只允许 90 的倍数。 */
    private int rotation;

    public PlacedImage(SelectionItem source, double x, double y, double width, double height) {
        this.source = source;
        this.x = x;
        this.y = y;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    public PlacedImage copy() {
        PlacedImage p = new PlacedImage(source, x, y, width, height);
        p.rotation = rotation;
        return p;
    }

    public SelectionItem getSource() {
        return source;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = ((rotation % 360) + 360) % 360;
    }

    public void rotate90() {
        setRotation(rotation + 90);
    }

    public void moveBy(double dx, double dy) {
        this.x += dx;
        this.y += dy;
    }

    public void moveTo(double nx, double ny) {
        this.x = nx;
        this.y = ny;
    }

    /** 未旋转时的版面矩形。 */
    public Rectangle2D bounds() {
        return new Rectangle2D.Double(x, y, width, height);
    }

    /** 旋转后占据的实际矩形（用于命中测试与范围判断）。 */
    public Rectangle2D rotatedBounds() {
        if (rotation % 180 == 0) {
            return bounds();
        }
        double cx = x + width / 2.0;
        double cy = y + height / 2.0;
        return new Rectangle2D.Double(cx - height / 2.0, cy - width / 2.0, height, width);
    }

    public void setSize(double w, double h) {
        this.width = Math.max(4, w);
        this.height = Math.max(4, h);
    }

    /** 贴图内容在页面上的绘制变换。 */
    public AffineTransform placement(double renderScale) {
        double cx = (x + width / 2.0) * renderScale;
        double cy = (y + height / 2.0) * renderScale;
        AffineTransform t = new AffineTransform();
        t.translate(cx, cy);
        if (rotation != 0) {
            t.rotate(Math.toRadians(rotation));
        }
        t.scale(width * renderScale / source.getImage().getWidth(),
                height * renderScale / source.getImage().getHeight());
        t.translate(-source.getImage().getWidth() / 2.0, -source.getImage().getHeight() / 2.0);
        return t;
    }

    /**
     * 把自身绘制到给定画布（画布已按 renderScale 缩放）。
     * <p>注意：裁剪区域必须在“设备坐标”下计算后再 setClip——如果先 transform 再 setClip，
     * Java2D 在缩放绘制位图时会走错误的分块路径，导致图片被截断。</p>
     */
    public void paint(java.awt.Graphics2D g, double renderScale) {
        BufferedImage img = source.getImage();
        AffineTransform old = g.getTransform();
        java.awt.Shape oldClip = g.getClip();

        Rectangle2D rb = rotatedBounds();
        // 裁剪矩形用页面坐标 -> 设备坐标（乘 renderScale），矩形本身已是旋转后的外接框
        Rectangle2D device = new Rectangle2D.Double(rb.getX() * renderScale, rb.getY() * renderScale,
                rb.getWidth() * renderScale, rb.getHeight() * renderScale);

        java.awt.geom.Area clip = new java.awt.geom.Area(device);
        if (oldClip != null) {
            clip.intersect(new java.awt.geom.Area(oldClip));
        }
        g.setClip(clip);
        g.transform(placement(renderScale));
        g.drawImage(img, 0, 0, null);
        g.setTransform(old);
        g.setClip(oldClip);
    }
}
