package juanzi.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import juanzi.model.CompPage;
import juanzi.model.ComposerStore;
import juanzi.model.PlacedImage;
import juanzi.model.SelectionItem;

/**
 * 拼版画布：显示当前拼版页，支持拖入、移动、缩放、删除贴图。
 *
 * <p>缩放同样有「适应窗口 / 适应宽度 / 手动」三种模式，默认适应窗口，
 * 保证整页在窗口里能看全，不会误判贴图位置。</p>
 */
public class ComposeView extends JPanel implements Scrollable {

    /** 缩放模式。 */
    public enum ZoomMode { FIT, FIT_WIDTH, MANUAL }

    private static final double RENDER_SCALE = 2.0;
    private static final int MAX_RENDER_SIDE = 3000;
    private static final int PADDING = 10;

    private final ComposerStore store;
    private int pageIndex;
    private ZoomMode zoomMode = ZoomMode.FIT;
    private double manualZoom = 0.75;

    private PlacedImage selected;
    private PlacedImage dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    private int resizeCorner = -1; // 0=左上 1=右上 2=右下 3=左下
    private double resizeStartX;
    private double resizeStartY;
    private double startW;
    private double startH;
    private double startX;
    private double startY;
    private int startRot;

    /** 拖拽落点预览。 */
    private Point dropHint;
    private Runnable onStatus;

    public ComposeView(ComposerStore store) {
        this.store = store;
        setBackground(new Color(0x3C3F41));
        setOpaque(true);
        setFocusable(true);
        setTransferHandler(new DropHandler());
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                double px = e.getX() / getZoom();
                double py = e.getY() / getZoom();
                int corner = hitHandle(e.getPoint());
                if (corner >= 0 && selected != null) {
                    beginResize(corner);
                    return;
                }
                PlacedImage hit = hitTest(px, py);
                if (hit != null) {
                    store.beginEdit();
                    selected = hit;
                    dragging = hit;
                    dragOffsetX = px - hit.getX();
                    dragOffsetY = py - hit.getY();
                } else {
                    selected = null;
                }
                repaint();
                status();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                double px = e.getX() / getZoom();
                double py = e.getY() / getZoom();
                if (resizeCorner >= 0 && selected != null) {
                    doResize(px, py);
                    repaint();
                    status();
                    return;
                }
                if (dragging != null) {
                    double nx = px - dragOffsetX;
                    double ny = py - dragOffsetY;
                    dragging.moveTo(nx, ny);
                    keepOnPage(dragging);
                    repaint();
                    status();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = null;
                resizeCorner = -1;
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (hitHandle(e.getPoint()) >= 0) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));
                } else if (hitTest(e.getX() / getZoom(), e.getY() / getZoom()) != null) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                } else {
                    setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown()) {
                    setZoom(getZoom() - e.getWheelRotation() * 0.1);
                    e.consume();
                } else {
                    java.awt.Container parent = ComposeView.this.getParent();
                    if (parent != null) {
                        parent.dispatchEvent(SwingUtilities.convertMouseEvent(ComposeView.this, e, parent));
                    }
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
        // 窗口/侧栏尺寸变化时，自适应模式要跟着重算
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                refreshAutoZoom();
            }
        });
        // 滚动视口的可视尺寸变化也要重算（拖分隔条、缩放窗口）
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.PARENT_CHANGED) != 0) {
                installViewportListener();
            }
        });
        store.addPageListener(() -> {
            if (pageIndex >= store.getPages().size()) {
                pageIndex = store.getPages().size() - 1;
            }
            if (pageIndex < 0) {
                pageIndex = 0;
            }
            if (selected != null && !currentPage().getItems().contains(selected)) {
                selected = null;
            }
            revalidate();
            repaint();
            status();
        });
    }

    public void setOnStatus(Runnable r) {
        this.onStatus = r;
    }

    private javax.swing.JViewport watchedViewport;
    private final java.beans.PropertyChangeListener extentListener = evt -> {
        if ("extentSize".equals(evt.getPropertyName())) {
            refreshAutoZoom();
        }
    };

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

    /** 适应模式下按最新的可视尺寸重新计算；手动模式不动。 */
    private void refreshAutoZoom() {
        if (zoomMode == ZoomMode.MANUAL) {
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

    private void status() {
        if (onStatus != null) {
            onStatus.run();
        }
    }

    // ---------- 页面与缩放 ----------

    public CompPage currentPage() {
        return store.getPages().get(Math.max(0, Math.min(pageIndex, store.getPages().size() - 1)));
    }

    /** 兼容旧调用：返回当前拼版页。 */
    public CompPage getPage() {
        return currentPage();
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int index) {
        this.pageIndex = Math.max(0, Math.min(index, store.getPages().size() - 1));
        this.selected = null;
        revalidate();
        repaint();
        status();
    }

    public PlacedImage getSelected() {
        return selected;
    }

    public void clearSelection() {
        selected = null;
        repaint();
    }

    public double getZoom() {
        if (zoomMode == ZoomMode.MANUAL) {
            return manualZoom;
        }
        return autoZoom(zoomMode == ZoomMode.FIT_WIDTH);
    }

    public ZoomMode getZoomMode() {
        return zoomMode;
    }

    /**
     * 适应窗口 / 适应宽度时按**可视区域**尺寸算缩放。
     * <p>注意：不能用 getParent()，那是 JSplitPane 的内部容器，它的尺寸和
     * 滚动条实际能看到的范围并不一致，会导致算出来的比例偏大、页面撑出屏幕且缩不小。</p>
     */
    private double autoZoom(boolean widthOnly) {
        CompPage p = currentPage();
        java.awt.Dimension vp = ViewportUtil.viewportExtent(this);
        double availW = vp.width > 40 ? vp.width - PADDING * 2 : 600;
        double availH = vp.height > 40 ? vp.height - PADDING * 2 : 800;
        double sw = availW / p.getWidth();
        double sh = availH / p.getHeight();
        double s = widthOnly ? sw : Math.min(sw, sh);
        return Math.max(0.1, Math.min(s, 3.0));
    }

    /** 手动设置缩放（切到手动模式）。 */
    public void setZoom(double z) {
        this.zoomMode = ZoomMode.MANUAL;
        this.manualZoom = Math.max(0.1, Math.min(z, 4.0));
        revalidate();
        repaint();
        status();
    }

    public void setZoomMode(ZoomMode mode) {
        this.zoomMode = mode;
        revalidate();
        repaint();
        status();
    }

    /** 适应窗口。 */
    public void fitToWindow() {
        setZoomMode(ZoomMode.FIT);
    }

    public void zoomIn() {
        setZoom(getZoom() * 1.15);
    }

    public void zoomOut() {
        setZoom(getZoom() / 1.15);
    }

    @Override
    public Dimension getPreferredSize() {
        CompPage p = currentPage();
        double z = getZoom();
        return new Dimension((int) Math.ceil(p.getWidth() * z) + PADDING,
                (int) Math.ceil(p.getHeight() * z) + PADDING);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(620, 860);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 120;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    // ---------- 操作 ----------

    /** 把内容库中的一项放到当前页（居中偏上或指定位置）。 */
    public PlacedImage place(SelectionItem item, Double atX, Double atY) {
        if (item == null) {
            return null;
        }
        CompPage page = currentPage();
        store.beginEdit();
        double w = item.getPointWidth();
        double h = item.getPointHeight();
        // 保证不超出版心（留出 10pt 边距），避免导出后文字被页面切掉
        double maxW = page.getWidth() - 20;
        if (w > maxW) {
            double k = maxW / w;
            w *= k;
            h *= k;
        }
        double x = atX != null ? atX - w / 2 : (page.getWidth() - w) / 2;
        double y = atY != null ? atY - h / 2 : 40;
        x = Math.max(10, Math.min(x, page.getWidth() - 10 - w));
        y = Math.max(0, y);
        PlacedImage pi = new PlacedImage(item, x, y, w, h);
        page.add(pi);
        selected = pi;
        revalidate();
        repaint();
        status();
        return pi;
    }

    public void deleteSelected() {
        if (selected != null) {
            store.beginEdit();
            currentPage().remove(selected);
            selected = null;
            repaint();
            status();
        }
    }

    public void rotateSelected() {
        if (selected != null) {
            store.beginEdit();
            selected.rotate90();
            repaint();
            status();
        }
    }

    public void bringSelectedToFront() {
        if (selected != null) {
            CompPage page = currentPage();
            store.beginEdit();
            page.getItems().remove(selected);
            page.getItems().add(selected);
            repaint();
        }
    }

    // ---------- 命中测试 ----------

    private PlacedImage hitTest(double px, double py) {
        CompPage page = currentPage();
        for (int i = page.getItems().size() - 1; i >= 0; i--) {
            PlacedImage it = page.getItems().get(i);
            if (it.rotatedBounds().contains(px, py)) {
                return it;
            }
        }
        return null;
    }

    /** 返回被命中的缩放手柄序号，-1 表示没有。 */
    private int hitHandle(Point screen) {
        if (selected == null) {
            return -1;
        }
        Rectangle2D b = selected.rotatedBounds();
        double[][] corners = {
                { b.getMinX(), b.getMinY() },
                { b.getMaxX(), b.getMinY() },
                { b.getMaxX(), b.getMaxY() },
                { b.getMinX(), b.getMaxY() } };
        for (int i = 0; i < 4; i++) {
            double cx = corners[i][0] * getZoom();
            double cy = corners[i][1] * getZoom();
            if (Math.abs(cx - screen.x) <= 7 && Math.abs(cy - screen.y) <= 7) {
                return i;
            }
        }
        return -1;
    }

    /** 拖动时保证贴图留在页面内，避免拖到页外看不见。 */
    private void keepOnPage(PlacedImage it) {
        CompPage page = currentPage();
        Rectangle2D rb = it.rotatedBounds();
        double dx = 0;
        double dy = 0;
        if (rb.getWidth() <= page.getWidth()) {
            if (rb.getMinX() < 0) {
                dx = -rb.getMinX();
            } else if (rb.getMaxX() > page.getWidth()) {
                dx = page.getWidth() - rb.getMaxX();
            }
        }
        if (rb.getHeight() <= page.getHeight()) {
            if (rb.getMinY() < 0) {
                dy = -rb.getMinY();
            } else if (rb.getMaxY() > page.getHeight()) {
                dy = page.getHeight() - rb.getMaxY();
            }
        }
        if (dx != 0 || dy != 0) {
            it.moveBy(dx, dy);
        }
    }

    private void beginResize(int corner) {
        resizeCorner = corner;
        store.beginEdit();
        resizeStartX = selected.getX();
        resizeStartY = selected.getY();
        startW = selected.getWidth();
        startH = selected.getHeight();
        startX = selected.getX();
        startY = selected.getY();
        startRot = selected.getRotation();
    }

    private void doResize(double px, double py) {
        // 以对角为锚点，等比缩放（旋转 90/270 时宽高互换）
        double anchorX;
        double anchorY;
        switch (resizeCorner) {
            case 0 -> { anchorX = startX + startW; anchorY = startY + startH; }
            case 1 -> { anchorX = startX; anchorY = startY + startH; }
            case 2 -> { anchorX = startX; anchorY = startY; }
            default -> { anchorX = startX + startW; anchorY = startY; }
        }
        double dw = Math.abs(px - anchorX);
        double dh = Math.abs(py - anchorY);
        if (startW <= 0 || startH <= 0) {
            return;
        }
        double ratio = startH / startW;
        double newW = Math.max(dw, dh / ratio);
        double newH = newW * ratio;
        if (newW < 12 || newH < 12) {
            return;
        }
        boolean flipped = startRot % 180 != 0;
        // 旋转后宽高互换，重新换算未旋转的框
        double boxW = flipped ? newH : newW;
        double boxH = flipped ? newW : newH;
        // 锚点保持不动，另一角跟随鼠标
        double ncx;
        double ncy;
        if (startRot % 180 == 0) {
            ncx = (anchorX < px) ? anchorX + boxW / 2 : anchorX - boxW / 2;
            ncy = (anchorY < py) ? anchorY + boxH / 2 : anchorY - boxH / 2;
        } else {
            ncx = (anchorX < px) ? anchorX + boxH / 2 : anchorX - boxH / 2;
            ncy = (anchorY < py) ? anchorY + boxW / 2 : anchorY - boxW / 2;
        }
        selected.setSize(boxW, boxH);
        selected.moveTo(ncx - boxW / 2, ncy - boxH / 2);
    }

    // ---------- 拖入 ----------

    private class DropHandler extends TransferHandler {

        private DropHandler() {
            // 用于画落点预览：Swing 的 TransferHandler 没有 dragOver 回调，靠属性变化通知
            ComposeView.this.addPropertyChangeListener("dropLocation", evt -> {
                Object v = evt.getNewValue();
                if (v instanceof DropLocation loc) {
                    dropHint = loc.getDropPoint();
                    repaint();
                } else {
                    clearDropHint();
                }
            });
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(SelectionListPanel.SELECTION_FLAVOR);
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            clearDropHint();
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                SelectionItem item = (SelectionItem) support.getTransferable()
                        .getTransferData(SelectionListPanel.SELECTION_FLAVOR);
                Point p = support.getDropLocation().getDropPoint();
                clearDropHint();
                place(item, p.x / getZoom(), p.y / getZoom());
                return true;
            } catch (Exception ex) {
                clearDropHint();
                return false;
            }
        }
    }

    private void clearDropHint() {
        if (dropHint != null) {
            dropHint = null;
            repaint();
        }
    }

    // ---------- 绘制 ----------

    /** 页面光栅缓存，避免拖动时反复整页渲染。 */
    private BufferedImage cachedRaster;
    private long cachedKey = Long.MIN_VALUE;

    private long pageSignature() {
        CompPage p = currentPage();
        long h = 1125899906842597L;
        h = h * 31 + Double.doubleToLongBits(p.getWidth());
        h = h * 31 + Double.doubleToLongBits(p.getHeight());
        for (PlacedImage it : p.getItems()) {
            h = h * 31 + System.identityHashCode(it.getSource());
            h = h * 31 + Double.doubleToLongBits(it.getX());
            h = h * 31 + Double.doubleToLongBits(it.getY());
            h = h * 31 + Double.doubleToLongBits(it.getWidth());
            h = h * 31 + Double.doubleToLongBits(it.getHeight());
            h = h * 31 + it.getRotation();
        }
        return h;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        CompPage page = currentPage();
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double z = getZoom();
        int w = (int) Math.round(page.getWidth() * z);
        int h = (int) Math.round(page.getHeight() * z);

        // 大页面下限制内部渲染分辨率，避免内存与耗时过大
        double scale = RENDER_SCALE;
        if (Math.max(page.getWidth(), page.getHeight()) * scale > MAX_RENDER_SIDE) {
            scale = MAX_RENDER_SIDE / Math.max(page.getWidth(), page.getHeight());
        }
        long key = pageSignature() * 31 + Double.doubleToLongBits(scale);
        if (cachedRaster == null || cachedKey != key) {
            cachedRaster = page.render(scale);
            cachedKey = key;
        }

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(cachedRaster, 0, 0, w, h, null);
        g.setColor(new Color(0x88, 0x88, 0x88));
        g.drawRect(0, 0, w - 1, h - 1);

        for (PlacedImage it : page.getItems()) {
            if (it == selected) {
                Rectangle2D b = it.rotatedBounds();
                g.setColor(new Color(0x1E, 0x88, 0xE5));
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[] { 5f, 4f }, 0f));
                g.drawRect((int) Math.round(b.getX() * z), (int) Math.round(b.getY() * z),
                        (int) Math.round(b.getWidth() * z), (int) Math.round(b.getHeight() * z));
                g.setStroke(new BasicStroke());
                g.setColor(Color.WHITE);
                double[][] corners = {
                        { b.getMinX(), b.getMinY() }, { b.getMaxX(), b.getMinY() },
                        { b.getMaxX(), b.getMaxY() }, { b.getMinX(), b.getMaxY() } };
                for (double[] c : corners) {
                    int hx = (int) Math.round(c[0] * z) - 4;
                    int hy = (int) Math.round(c[1] * z) - 4;
                    g.fillRect(hx, hy, 9, 9);
                    g.setColor(new Color(0x1E, 0x88, 0xE5));
                    g.drawRect(hx, hy, 8, 8);
                    g.setColor(Color.WHITE);
                }
            }
        }

        // 拖拽落点提示（一个小的虚线框，示意松手后放这里）
        if (dropHint != null) {
            int gw = 140;
            int gh = 70;
            g.setColor(new Color(0x66, 0xBB, 0x6A, 70));
            g.fillRect(dropHint.x - gw / 2, dropHint.y - gh / 2, gw, gh);
            g.setColor(new Color(0x2E, 0x7D, 0x32));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[] { 5f, 4f }, 0f));
            g.drawRect(dropHint.x - gw / 2, dropHint.y - gh / 2, gw, gh);
            g.setStroke(new BasicStroke());
        }
        g.dispose();
    }
}
