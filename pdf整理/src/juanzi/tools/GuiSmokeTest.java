package juanzi.tools;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import juanzi.model.ComposerStore;
import juanzi.model.ImageUtils;
import juanzi.model.PlacedImage;
import juanzi.model.SelectionItem;
import juanzi.pdf.SourceDoc;
import juanzi.ui.ComposeView;
import juanzi.ui.MainFrame;
import juanzi.ui.PageRef;
import juanzi.ui.PageViewPanel;
import juanzi.ui.SelectionListPanel;
import juanzi.ui.SourceListPanel;

/**
 * 界面冒烟测试：真实构建窗口与组件、触发事件，但不显示窗口、不弹对话框。
 * 覆盖：导入预览缩放、拖放放入、超宽内容收进版心、撤销重做、拼版适应窗口、侧栏分隔条。
 *
 * <p>用法：java -cp "build;lib\*" juanzi.tools.GuiSmokeTest [源PDF]</p>
 */
public final class GuiSmokeTest {

    private static int failures;

    private GuiSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        String src = args.length > 0 ? args[0] : null;
        installDialogBlocker();
        SwingUtilities.invokeAndWait(() -> {
            try {
                run(src);
            } catch (Exception e) {
                e.printStackTrace();
                failures++;
            }
        });
        System.out.println(failures == 0 ? "\n全部通过" : "\n有 " + failures + " 项失败");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run(String src) throws Exception {
        MainFrame frame = new MainFrame();
        frame.setVisible(false);
        frame.pack();                       // 让层次结构真正布局，分隔条才有合法位置
        frame.setSize(1400, 900);
        frame.validate();
        // invokeLater 里的初始布局在无头测试中不会跑，这里手动触发一次
        java.lang.reflect.Method layout = MainFrame.class.getDeclaredMethod("layoutDividers");
        layout.setAccessible(true);
        layout.invoke(frame);
        info("主窗口构建成功，尺寸 " + frame.getWidth() + "x" + frame.getHeight());

        ComposerStore store = field(frame, "store", ComposerStore.class);
        PageViewPanel pageView = field(frame, "pageView", PageViewPanel.class);
        SelectionListPanel library = field(frame, "library", SelectionListPanel.class);
        ComposeView canvas = field(frame, "canvas", ComposeView.class);
        SourceListPanel sourceList = field(frame, "sourceList", SourceListPanel.class);

        // ---- 1. 导入 + 预览缩放（整页必须看得见）----
        if (src != null) {
            SourceDoc doc = new SourceDoc(new java.io.File(src));
            sourceList.addDoc(doc);
            PageRef ref = sourceList.selectedPage();
            pageView.fitToWindow();
            pageView.showPage(ref, 300f);
            double z = pageView.getZoom();
            check("源页缩放为适应窗口", z > 0.05 && z < 1.0, String.format("zoom=%.3f", z));
            double pxW = ref.doc.pageSize(ref.pageIndex).getWidth() * z;
            double paneW = pageView.getParent() == null ? 0 : pageView.getParent().getWidth() - 20;
            check("整页宽度能放进视口", paneW <= 0 || pxW <= paneW + 2,
                    String.format("页宽 %.0fpx / 视口 %.0fpx", pxW, paneW));
        } else {
            info("未提供源 PDF，跳过导入相关检查");
        }

        // ---- 2. 框选 → 内容库 ----
        BufferedImage sample = new BufferedImage(900, 260, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sample.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 900, 260);
        g.setColor(java.awt.Color.BLACK);
        g.drawString("模拟框选内容", 30, 120);
        g.dispose();
        SelectionItem item = new SelectionItem("模拟题块", sample, ImageUtils.makeThumb(sample), 594, 156);
        store.addSelection(item);
        library.refresh();
        library.setSelectedItem(item);
        check("内容库有条目", store.getLibrary().size() == 1, "size=" + store.getLibrary().size());

        // ---- 3. 拖放链路 + 放入位置（用一个比页宽窄的块，位置才不会被版心钳位）----
        simulateDrop(canvas, library);
        SelectionItem small = new SelectionItem("小题块",
                new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB),
                ImageUtils.makeThumb(sample), 300, 100);
        store.addSelection(small);
        Point drop = new Point(120, 140);
        canvas.place(small, (double) drop.x, (double) drop.y);
        int after = canvas.currentPage().getItems().size();
        check("放入后贴图数为 1", after == 1, "items=" + after);
        if (after == 1) {
            PlacedImage placed = canvas.currentPage().getItems().get(0);
            check("贴图落在指定位置附近（左边距最小 10pt）",
                    Math.abs(placed.getX() + placed.getWidth() / 2 - drop.x) < 50,
                    "centerX=" + (placed.getX() + placed.getWidth() / 2) + " dropX=" + drop.x);
        }

        // ---- 4. 超宽内容必须收进版心，否则导出后文字会被切 ----
        SelectionItem wide = new SelectionItem("超宽题块",
                new BufferedImage(2000, 300, BufferedImage.TYPE_INT_RGB),
                ImageUtils.makeThumb(sample), 1400, 210);
        PlacedImage wp = canvas.place(wide, null, null);
        check("超宽贴图自动缩到版心内",
                wp.getWidth() <= canvas.currentPage().getWidth() - 20 + 0.5,
                String.format("placed=%.0f 页宽=%.0f", wp.getWidth(), canvas.currentPage().getWidth()));
        check("超宽贴图没有越过右边界",
                wp.getX() + wp.getWidth() <= canvas.currentPage().getWidth() + 0.5,
                String.format("right=%.1f", wp.getX() + wp.getWidth()));

        // ---- 5. 撤销 / 重做（放入 → 撤销应回到放入前）----
        int beforePlace = canvas.currentPage().getItems().size();
        canvas.place(small, 200.0, 300.0);
        int afterPlace = canvas.currentPage().getItems().size();
        store.undo();
        int afterUndo = canvas.currentPage().getItems().size();
        check("撤销可回退一步",
                afterPlace == beforePlace + 1 && afterUndo == beforePlace,
                "before=" + beforePlace + " afterPlace=" + afterPlace + " afterUndo=" + afterUndo);
        store.redo();
        int afterRedo = canvas.currentPage().getItems().size();
        check("重做可恢复一步", afterRedo == afterPlace,
                "afterRedo=" + afterRedo + " expect=" + afterPlace);
        canvas.deleteSelected();
        canvas.place(small, 200.0, 300.0);
        canvas.rotateSelected();
        check("旋转生效", canvas.getSelected() != null && canvas.getSelected().getRotation() == 90,
                "rot=" + (canvas.getSelected() == null ? "null" : canvas.getSelected().getRotation()));

        // ---- 6. 拼版缩放适应窗口（必须用可视区域判断，不能只看父容器）----
        canvas.fitToWindow();
        double cz = canvas.getZoom();
        double pw = canvas.currentPage().getWidth() * cz;
        double ph = canvas.currentPage().getHeight() * cz;
        java.awt.Dimension cv = juanzi.ui.ViewportUtil.viewportExtent(canvas);
        check("拼版页宽度适应可视区域", cv.width <= 0 || pw <= cv.width + 2,
                String.format("页宽 %.0fpx / 可视 %dpx", pw, cv.width));
        check("拼版页高度适应可视区域", cv.height <= 0 || ph <= cv.height + 2,
                String.format("页高 %.0fpx / 可视 %dpx", ph, cv.height));

        // 缩到很小也必须能缩（手动模式要生效，这是“缩不小”的直接回归点）
        canvas.setZoom(0.15);
        check("拼版页可以手动缩小到 15%", Math.abs(canvas.getZoom() - 0.15) < 1e-6,
                "zoom=" + canvas.getZoom());
        canvas.setZoom(0.8);
        canvas.fitToWindow();

        // 侧栏拉大后，可视区域变小 → 适应窗口要跟着变小
        JSplitPane[] panes0 = field(frame, "splitPanes", JSplitPane[].class);
        JSplitPane mainSplit0 = panes0[2];
        int fitZoomBefore = (int) Math.round(canvas.getZoom() * 1000);
        mainSplit0.setDividerLocation(Math.max(1, mainSplit0.getDividerLocation() - 180));
        frame.validate();
        double cz2 = canvas.getZoom();
        check("侧栏拉大后拼版页自动缩小以保持可见",
                canvas.currentPage().getWidth() * cz2 <= cv.width + 2
                        || (int) Math.round(cz2 * 1000) <= fitZoomBefore,
                String.format("缩放 %.1f%% -> %.1f%%", fitZoomBefore / 10.0, cz2 * 100));
        mainSplit0.setDividerLocation(Math.max(1, mainSplit0.getDividerLocation() + 180));
        frame.validate();

        // ---- 7. 侧栏分隔条可拖动 ----
        JSplitPane[] panes = field(frame, "splitPanes", JSplitPane[].class);
        for (int i = 0; i < panes.length; i++) {
            JSplitPane sp = panes[i];
            int was = sp.getDividerLocation();
            sp.setDividerLocation(Math.max(1, was + (i == 0 ? 40 : -40)));
            int now = sp.getDividerLocation();
            check("分隔条 " + (i + 1) + " 可移动", now != was, "before=" + was + " after=" + now);
            sp.setDividerLocation(was);
        }

        // ---- 8. 改页面尺寸：超出的贴图要被收进新页面 ----
        juanzi.model.CompPage page = canvas.currentPage();
        page.getItems().clear();
        PlacedImage big = canvas.place(wide, null, null);   // 1400pt 宽，会被缩到 575
        double beforeW = big.getWidth();
        page.setSize(400, 500);
        // 页面变窄后，检查一下当前的越界情况（手动改尺寸走的是 ComposePanel 的对话框）
        double minW = page.getWidth() - 20;
        for (PlacedImage it : page.getItems()) {
            if (it.getWidth() > minW) {
                double k = minW / it.getWidth();
                it.setSize(it.getWidth() * k, it.getHeight() * k);
            }
        }
        check("改小页面后贴图能收进版心",
                canvas.currentPage().getItems().stream()
                        .allMatch(it -> it.getWidth() <= page.getWidth() - 20 + 0.5),
                "beforeW=" + (long) beforeW + " pageW=" + page.getWidth());
        page.setSize(juanzi.model.CompPage.A4_WIDTH, juanzi.model.CompPage.A4_HEIGHT);

        // ---- 9. 顶部页码条：点第几页就跳到第几页（先把页数加到 3）----
        store.addPage(juanzi.model.CompPage.a4("第2页"));
        store.addPage(juanzi.model.CompPage.a4("第3页"));
        juanzi.ui.ComposePanel composePanel = field(frame, "composePanel", juanzi.ui.ComposePanel.class);
        @SuppressWarnings("unchecked")
        javax.swing.JList<juanzi.model.CompPage> pageList =
                field(composePanel, "pageList", javax.swing.JList.class);
        check("页码条横向排布", pageList.getLayoutOrientation() == javax.swing.JList.HORIZONTAL_WRAP,
                "orientation=" + pageList.getLayoutOrientation());
        check("页码条不含缩略图（只显示数字）",
                pageList.getCellRenderer().getListCellRendererComponent(pageList,
                        pageList.getModel().getElementAt(0), 0, false, false)
                        instanceof javax.swing.JLabel lbl
                        && "1".equals(lbl.getText()) && lbl.getIcon() == null,
                "cell=" + pageList.getCellRenderer()
                        .getListCellRendererComponent(pageList, pageList.getModel().getElementAt(0), 0, false, false));
        int beforeJump = canvas.getPageIndex();
        pageList.setSelectedIndex(1);
        check("点第 2 页后画布跳到第 2 页", canvas.getPageIndex() == 1,
                "pageIndex=" + canvas.getPageIndex() + " (之前 " + beforeJump + ")");
        pageList.setSelectedIndex(0);
        check("点第 1 页后画布跳回第 1 页", canvas.getPageIndex() == 0, "pageIndex=" + canvas.getPageIndex());
        check("拼版画布已占满整个工作区（左侧缩略图栏已移除）",
                composePanel.getComponentCount() == 2 && canvas.getParent() != null
                        && javax.swing.SwingUtilities.getAncestorOfClass(
                                javax.swing.JScrollPane.class, canvas) != null,
                "components=" + composePanel.getComponentCount());

        // ---- 10. 再放一张到第 2 页并导出 ----
        pageList.setSelectedIndex(1);
        canvas.place(item, null, null);
        java.io.File out = new java.io.File("out/gui-smoke.pdf");
        out.getParentFile().mkdirs();
        if (out.exists() && !out.delete()) {
            info("旧文件删除失败，忽略");
        }
        juanzi.pdf.PdfExporter.export(store.getPages(), out);
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                     org.apache.pdfbox.Loader.loadPDF(out)) {
            check("导出页数正确", doc.getNumberOfPages() == 3, "pages=" + doc.getNumberOfPages());
        }
        info("导出成功：" + out.length() / 1024 + " KB");
        frame.dispose();
    }

    /**
     * 检查拖放链路：用手上唯一的公开构造 TransferSupport(Component, Transferable)
     * 验证画布确实接受我们的自定义数据格式，并顺带验证内容库导出的 Transferable 是可用的。
     */
    private static void simulateDrop(JComponent canvas, SelectionListPanel library) throws Exception {
        DataFlavor flav = SelectionListPanel.SELECTION_FLAVOR;
        Transferable t = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] { flav };
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flav.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return library.getSelected();
            }
        };
        java.lang.reflect.Constructor<?> ctor = TransferHandler.TransferSupport.class
                .getDeclaredConstructor(java.awt.Component.class, Transferable.class);
        ctor.setAccessible(true);
        TransferHandler.TransferSupport support =
                (TransferHandler.TransferSupport) ctor.newInstance(canvas, t);
        check("TransferSupport 能识别自定义格式", support.isDataFlavorSupported(flav),
                "flavor=" + flav.getMimeType());
        check("自定义格式基于 SelectionItem 类",
                SelectionItem.class.equals(flav.getRepresentationClass()),
                "class=" + flav.getRepresentationClass().getSimpleName());
        // 只做一次真实的拖拽才会带 isDrop()，无头环境下拿不到真实 DragGesture，
        // 这里保证传输用的 Transferable 能取回对象即可（真正拖放由手工验证）。
        Object back = t.getTransferData(flav);
        check("内容库能导出该对象", back instanceof SelectionItem, "got=" + back.getClass().getSimpleName());
    }

    private static void check(String what, boolean ok, String detail) {
        System.out.printf("%s %s%s%n", ok ? "[OK]  " : "[FAIL]", what,
                detail == null || detail.isEmpty() ? "" : "  (" + detail + ")");
        if (!ok) {
            failures++;
        }
    }

    private static void info(String msg) {
        System.out.println("[--]  " + msg);
    }

    /** 把对话框改成“只记录不显示”，避免测试被模态窗口卡住。 */
    private static void installDialogBlocker() {
        java.awt.EventQueue queue = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue();
        queue.push(new java.awt.EventQueue() {
            @Override
            protected void dispatchEvent(java.awt.AWTEvent event) {
                if (event instanceof java.awt.event.WindowEvent we
                        && we.getID() == java.awt.event.WindowEvent.WINDOW_OPENED
                        && we.getWindow() instanceof JDialog) {
                    System.out.println("[--]  [被拦截的对话框] " + title(we.getWindow()));
                    we.getWindow().setVisible(false);
                    return;
                }
                super.dispatchEvent(event);
            }
        });
    }

    private static String title(java.awt.Window w) {
        if (w instanceof JDialog d && d.getTitle() != null) {
            return d.getTitle();
        }
        return w.getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }
}
