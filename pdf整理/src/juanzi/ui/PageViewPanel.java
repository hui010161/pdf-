package juanzi.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import juanzi.model.SelectionItem;

/**
 * 中间：源页预览，支持鼠标框选内容。
 *
 * <p>缩放分三种模式：适应窗口 / 适应宽度 / 手动。页面载入后默认「适应窗口」，
 * 保证整页宽度都能看见——否则预览右边会被窗口裁掉，框选时会漏掉右侧的字，
 * 拼出来的 PDF 就会出现文字被硬切的情况。</p>
 */
public class PageViewPanel extends JPanel {

    private static final int MAX_RENDER_SIDE = 6000;
    private static final int PADDING = 10;

    /** 缩放模式。 */
    public enum ZoomMode { FIT, FIT_WIDTH, MANUAL }

    private PageRef ref;
    private BufferedImage pageImage;
    private ZoomMode mode = ZoomMode.FIT;
    private double manualScale = 1.0;
    /** 渲染时的 dpi，用于把屏幕框选换算回点坐标。 */
    private double renderDpi = 96;

    private java.awt.Point dragStart;
    private java.awt.Rectangle dragRect;

    private BiConsumer<SelectionItem, Rectangle2D> onSelectionCreated;
    private Runnable onStatus;

    public PageViewPanel() {
        setBackground(new Color(0x3C3F41));
        setOpaque(true);
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (pageImage == null || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                dragStart = clampToImage(e.getPoint());
                dragRect = new Rectangle(dragStart);
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) {
                    return;
                }
                java.awt.Point p = clampToImage(e.getPoint());
                dragRect = new Rectangle(
                        Math.min(dragStart.x, p.x), Math.min(dragStart.y, p.y),
                        Math.abs(dragStart.x - p.x), Math.abs(dragStart.y - p.y));
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragStart == null || dragRect == null) {
                    return;
                }
                Rectangle r = dragRect;
                dragStart = null;
                dragRect = null;
                if (r.width < 6 || r.height < 6) {
                    repaint();
                    return;
                }
                createSelection(r);
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(pageImage != null
                        ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        // 窗口/侧栏尺寸变化时，自适应模式要跟着重算
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refreshAutoZoom();
            }
        });
        // 滚动视口的可视尺寸变化也要重算（拖动分隔条时最容易触发）
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.PARENT_CHANGED) != 0) {
                installViewportListener();
            }
        });
    }

    private void installViewportListener() {
        javax.swing.JViewport vp = ViewportUtil.findViewport(this);
        if (vp == null || vp == watchedViewport) {
            return;
        }
        if (watchedViewport != null) {
            watchedViewport.removePropertyChangeListener(extentListener);
        }
        watchedViewport = vp;
        vp.addPropertyChangeListener(extentListener);
    }

    private javax.swing.JViewport watchedViewport;
    private final java.beans.PropertyChangeListener extentListener = evt -> {
        if ("extentSize".equals(evt.getPropertyName())) {
            refreshAutoZoom();
        }
    };

    /** 适应模式下按最新的可视尺寸重新计算。 */
    private void refreshAutoZoom() {
        if (mode == ZoomMode.MANUAL) {
            return;
        }
        java.awt.Dimension need = getPreferredSize();
        java.awt.Dimension vp = ViewportUtil.viewportExtent(this);
        if (vp.width <= 0 || need.width > vp.width + 1 || need.height > vp.height + 1) {
            revalidate();
            repaint();
            status();
        }
    }

    public void setOnSelectionCreated(BiConsumer<SelectionItem, Rectangle2D> c) {
        this.onSelectionCreated = c;
    }

    public void setOnStatus(Runnable r) {
        this.onStatus = r;
    }

    private void status() {
        if (onStatus != null) {
            onStatus.run();
        }
    }

    private java.awt.Point clampToImage(java.awt.Point p) {
        if (pageImage == null) {
            return p;
        }
        double z = getZoom();
        int w = (int) Math.round(pageImage.getWidth() * z);
        int h = (int) Math.round(pageImage.getHeight() * z);
        return new java.awt.Point(
                Math.max(0, Math.min(p.x, w - 1)),
                Math.max(0, Math.min(p.y, h - 1)));
    }

    public PageRef getPageRef() {
        return ref;
    }

    public ZoomMode getZoomMode() {
        return mode;
    }

    /** 当前缩放：显示尺寸 / 页面点尺寸。1.0 表示 1pt 画 1px。 */
    public double getZoom() {
        if (pageImage == null) {
            return 1.0;
        }
        if (mode == ZoomMode.MANUAL) {
            return manualScale;
        }
        return autoScale(mode == ZoomMode.FIT_WIDTH);
    }

    /** 适应窗口 / 适应宽度时按**可视区域**算缩放（不是父容器，滚动条会占地方）。 */
    private double autoScale(boolean widthOnly) {
        BufferedImage img = pageImage;
        if (img == null) {
            return 1.0;
        }
        double ptW = img.getWidth() / renderDpi * 72.0;
        double ptH = img.getHeight() / renderDpi * 72.0;
        java.awt.Dimension vp = ViewportUtil.viewportExtent(this);
        double availW = vp.width > 40 ? vp.width - PADDING * 2 : 800;
        double availH = vp.height > 40 ? vp.height - PADDING * 2 : 1000;
        double sw = availW / ptW;
        double sh = availH / ptH;
        double s = widthOnly ? sw : Math.min(sw, sh);
        return Math.max(0.08, Math.min(s, 6.0));
    }

    public void setMode(ZoomMode newMode) {
        this.mode = newMode;
        revalidate();
        repaint();
        status();
    }

    /** 手动设置缩放（会切到手动模式）。 */
    public void setZoom(double zoom) {
        this.mode = ZoomMode.MANUAL;
        this.manualScale = Math.max(0.05, Math.min(zoom, 8.0));
        revalidate();
        repaint();
        status();
    }

    public void zoomIn() {
        setZoom(getZoom() * 1.15);
    }

    public void zoomOut() {
        setZoom(getZoom() / 1.15);
    }

    /** 适应窗口。 */
    public void fitToWindow() {
        setMode(ZoomMode.FIT);
    }

    /** 适应宽度（只看左右是否完整）。 */
    public void fitToWidth() {
        setMode(ZoomMode.FIT_WIDTH);
    }

    /** 1 个页面点对应多少屏幕像素。 */
    public double pixelsPerPoint() {
        return getZoom();
    }

    public void clear() {
        ref = null;
        pageImage = null;
        revalidate();
        repaint();
        status();
    }

    /** 载入并显示某一页；dpi 为渲染精度。 */
    public void showPage(PageRef ref, float dpi) throws java.io.IOException {
        showPage(ref, dpi, true);
    }

    /**
     * 载入并显示某一页。
     *
     * @param autoFit 是否在载入后自动适应窗口（只改精度时传 false，保留当前缩放）
     */
    public void showPage(PageRef ref, float dpi, boolean autoFit) throws java.io.IOException {
        this.ref = ref;
        Rectangle2D size = ref.doc.pageSize(ref.pageIndex);
        double effectiveDpi = dpi;
        double side = Math.max(size.getWidth(), size.getHeight()) * dpi / 72.0;
        if (side > MAX_RENDER_SIDE) {
            effectiveDpi = dpi * MAX_RENDER_SIDE / side;
        }
        this.pageImage = ref.doc.render(ref.pageIndex, (float) effectiveDpi);
        this.renderDpi = effectiveDpi;
        if (autoFit && mode != ZoomMode.MANUAL) {
            mode = ZoomMode.FIT;
        }
        revalidate();
        repaint();
        status();
    }

    public void rerender(float dpi) throws java.io.IOException {
        if (ref != null) {
            showPage(ref, dpi, false);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (pageImage == null) {
            return new Dimension(600, 800);
        }
        double z = getZoom();
        return new Dimension((int) Math.ceil(pageImage.getWidth() * z) + PADDING,
                (int) Math.ceil(pageImage.getHeight() * z) + PADDING);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        if (pageImage == null) {
            g.setColor(Color.LIGHT_GRAY);
            g.drawString("请先导入 PDF，然后在左侧点击要框选的页面", 24, 40);
            g.dispose();
            return;
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double z = getZoom();
        int w = (int) Math.round(pageImage.getWidth() * z);
        int h = (int) Math.round(pageImage.getHeight() * z);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(pageImage, 0, 0, w, h, null);
        g.setColor(new Color(0x99, 0x99, 0x99));
        g.drawRect(0, 0, w - 1, h - 1);

        if (dragRect != null && dragRect.width > 0 && dragRect.height > 0) {
            g.setColor(new Color(0x33, 0x99, 0xFF, 60));
            g.fillRect(dragRect.x, dragRect.y, dragRect.width, dragRect.height);
            g.setColor(new Color(0x1E, 0x88, 0xE5));
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[] { 6f, 4f }, 0f));
            g.drawRect(dragRect.x, dragRect.y, dragRect.width, dragRect.height);
            g.setStroke(new BasicStroke());
            String label = String.format("区域 %.0f × %.0f pt",
                    dragRect.width / z / renderDpi * 72, dragRect.height / z / renderDpi * 72);
            g.setColor(Color.BLACK);
            g.fillRect(dragRect.x, Math.max(0, dragRect.y - 18), g.getFontMetrics().stringWidth(label) + 8, 17);
            g.setColor(Color.WHITE);
            g.drawString(label, dragRect.x + 4, Math.max(12, dragRect.y - 5));
        }
        g.dispose();
    }

    /** 把屏幕上的一块矩形变成可重复使用的内容块。 */
    private void createSelection(Rectangle screenRect) {
        double z = getZoom();
        double scale = 1.0 / z; // 屏幕 -> 位图像素
        int x = (int) Math.floor(screenRect.x * scale);
        int y = (int) Math.floor(screenRect.y * scale);
        int w = (int) Math.ceil(screenRect.width * scale);
        int h = (int) Math.ceil(screenRect.height * scale);
        x = Math.max(0, x);
        y = Math.max(0, y);
        w = Math.min(w, pageImage.getWidth() - x);
        h = Math.min(h, pageImage.getHeight() - y);
        if (w < 4 || h < 4) {
            return;
        }
        BufferedImage crop = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = crop.createGraphics();
        g2.drawImage(pageImage, 0, 0, w, h, x, y, x + w, y + h, null);
        g2.dispose();

        double ptW = w / renderDpi * 72.0;
        double ptH = h / renderDpi * 72.0;
        crop = limitSize(crop);
        String name = String.format("%s P%d", shortName(ref.doc.getDisplayName()), ref.pageIndex + 1);
        SelectionItem item = new SelectionItem(name, crop, juanzi.model.ImageUtils.makeThumb(crop), ptW, ptH);
        if (onSelectionCreated != null) {
            Rectangle2D pageRect = new Rectangle2D.Double(
                    x / renderDpi * 72.0, y / renderDpi * 72.0, ptW, ptH);
            onSelectionCreated.accept(item, pageRect);
        }
    }

    private static String shortName(String n) {
        return n.length() > 18 ? n.substring(0, 17) + "…" : n;
    }

    /** 超大选区按比例降采样，防止内存被撑爆（300 DPI 的 A4 整页也在上限内）。 */
    private static BufferedImage limitSize(BufferedImage src) {
        int maxSide = 2600;
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxSide && h <= maxSide) {
            return src;
        }
        double s = maxSide / (double) Math.max(w, h);
        int nw = Math.max(1, (int) (w * s));
        int nh = Math.max(1, (int) (h * s));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }
}
