package juanzi.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SpinnerListModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import juanzi.model.ComposerStore;
import juanzi.model.PlacedImage;
import juanzi.model.SelectionItem;
import juanzi.pdf.PdfExporter;
import juanzi.pdf.SourceDoc;

/**
 * 主窗口：左边源 PDF 与预览、右边拼版工作区。
 *
 * <p>工具栏分两行，避免单行过长把窗口的最小宽度撑大（那样窗口就缩不小了）。</p>
 */
public class MainFrame extends JFrame {

    private final ComposerStore store = new ComposerStore();
    private final SourceListPanel sourceList = new SourceListPanel();
    private final PageViewPanel pageView = new PageViewPanel();
    private final SelectionListPanel library = new SelectionListPanel(store);
    private final ComposePanel composePanel = new ComposePanel(store);
    private final ComposeView canvas = composePanel.getCanvas();
    private final JLabel status = new JLabel("就绪");

    private final JSpinner dpiSpinner = new JSpinner(
            new SpinnerListModel(new Object[] { "150", "200", "300", "400" }));

    private float selectionDpi = 300f;

    private JSplitPane[] splitPanes;
    private double[] splitRatios;

    public MainFrame() {
        super("错题组卷器 — 导入 PDF / 框选 / 拖拽拼版 / 导出");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        dpiSpinner.setValue("300");
        dpiSpinner.setMaximumSize(new Dimension(70, 28));

        add(buildToolBar(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);

        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        add(status, BorderLayout.SOUTH);

        wireEvents();
        installShortcuts();

        setSize(1400, 920);
        setLocationRelativeTo(null);
        updateStatus();
    }

    // ---------- 界面搭建 ----------

    /** 工具栏分两行：第一行文件/编辑，第二行精度与缩放。 */
    private JComponent buildToolBar() {
        JToolBar row1 = new JToolBar();
        row1.setFloatable(false);
        row1.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));

        JButton importBtn = new JButton("导入 PDF…");
        importBtn.setToolTipText("可以一次选择多个 PDF 文件");
        importBtn.addActionListener(e -> importPdfs());
        row1.add(importBtn);

        JButton removeSrc = new JButton("移除源文件");
        removeSrc.addActionListener(e -> removeCurrentSource());
        row1.add(removeSrc);

        row1.addSeparator(new Dimension(14, 10));

        JButton insertBtn = new JButton("放入当前页");
        insertBtn.setToolTipText("把右下角选中的框选内容放到当前拼版页");
        insertBtn.addActionListener(e -> insertSelected());
        row1.add(insertBtn);

        row1.addSeparator(new Dimension(14, 10));

        JButton undo = new JButton("撤销");
        undo.addActionListener(e -> {
            store.undo();
            updateStatus();
        });
        JButton redo = new JButton("重做");
        redo.addActionListener(e -> {
            store.redo();
            updateStatus();
        });
        JButton del = new JButton("删除贴图");
        del.addActionListener(e -> {
            canvas.deleteSelected();
            updateStatus();
        });
        JButton rot = new JButton("旋转90°");
        rot.addActionListener(e -> {
            canvas.rotateSelected();
            updateStatus();
        });
        JButton front = new JButton("置顶");
        front.addActionListener(e -> {
            canvas.bringSelectedToFront();
            updateStatus();
        });
        row1.add(undo);
        row1.add(redo);
        row1.add(del);
        row1.add(rot);
        row1.add(front);

        JToolBar row2 = new JToolBar();
        row2.setFloatable(false);
        row2.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 6));

        row2.add(new JLabel("框选精度 "));
        row2.add(dpiSpinner);
        row2.add(new JLabel(" DPI"));
        dpiSpinner.setToolTipText("框选内容的分辨率，越高越清晰、文件越大");

        row2.addSeparator(new Dimension(14, 10));
        row2.add(new JLabel("源页 "));
        JButton sOut = new JButton("−");
        sOut.setToolTipText("缩小");
        sOut.addActionListener(e -> pageView.zoomOut());
        JButton sIn = new JButton("+");
        sIn.setToolTipText("放大");
        sIn.addActionListener(e -> pageView.zoomIn());
        JButton sFit = new JButton("适应窗口");
        sFit.setToolTipText("整页缩放到能全部看见（导入后默认就是这个）");
        sFit.addActionListener(e -> pageView.fitToWindow());
        JButton sWidth = new JButton("适应宽度");
        sWidth.setToolTipText("只保证整页宽度可见，适合一行行框选");
        sWidth.addActionListener(e -> pageView.fitToWidth());
        row2.add(sOut);
        row2.add(sIn);
        row2.add(sFit);
        row2.add(sWidth);

        row2.addSeparator(new Dimension(14, 10));
        row2.add(new JLabel("拼版 "));
        JButton cOut = new JButton("−");
        cOut.setToolTipText("缩小拼版页（觉得版面占地方就点这个）");
        cOut.addActionListener(e -> canvas.zoomOut());
        JButton cIn = new JButton("+");
        cIn.setToolTipText("放大");
        cIn.addActionListener(e -> canvas.zoomIn());
        JButton cFit = new JButton("适应窗口");
        cFit.setToolTipText("整页缩放到能全部看见");
        cFit.addActionListener(e -> canvas.fitToWindow());
        row2.add(cOut);
        row2.add(cIn);
        row2.add(cFit);

        row2.add(Box.createHorizontalGlue());
        JButton export = new JButton("导出 PDF");
        export.addActionListener(e -> exportPdf());
        row2.add(export);

        JPanel both = new JPanel();
        both.setLayout(new BoxLayout(both, BoxLayout.Y_AXIS));
        row1.setAlignmentX(LEFT_ALIGNMENT);
        row2.setAlignmentX(LEFT_ALIGNMENT);
        both.add(row1);
        both.add(row2);
        return both;
    }

    private JComponent buildBody() {
        // 各面板都可以按自己的习惯调宽高
        sourceList.setMinimumSize(new Dimension(100, 60));
        pageView.setMinimumSize(new Dimension(140, 120));
        library.setMinimumSize(new Dimension(120, 80));
        composePanel.setMinimumSize(new Dimension(260, 180));
        canvas.setMinimumSize(new Dimension(120, 100));

        JScrollPane pageScroll = new JScrollPane(pageView);
        pageScroll.getVerticalScrollBar().setUnitIncrement(24);
        pageScroll.getHorizontalScrollBar().setUnitIncrement(24);
        pageScroll.setBorder(BorderFactory.createTitledBorder("源页预览（按住左键拖拽框选题目）"));

        // ---- 左侧：源文件树 | 源页预览 ----
        JSplitPane sourceSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourceList, pageScroll);
        sourceSplit.setResizeWeight(0.0);
        sourceSplit.setOneTouchExpandable(true);
        sourceSplit.setContinuousLayout(true);
        sourceSplit.setBorder(null);

        // ---- 左半区：上面预览 + 下面框选内容库（上下也能拖）----
        JSplitPane sourceAndLibrary = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sourceSplit, library);
        sourceAndLibrary.setResizeWeight(0.72);
        sourceAndLibrary.setOneTouchExpandable(true);
        sourceAndLibrary.setContinuousLayout(true);
        sourceAndLibrary.setBorder(null);

        // ---- 整体：左半区 | 拼版工作区 ----
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourceAndLibrary, composePanel);
        mainSplit.setResizeWeight(0.55);
        mainSplit.setOneTouchExpandable(true);
        mainSplit.setContinuousLayout(true);

        // 分隔条初始位置按比例给，窗口变化时也按比例走
        splitPanes = new JSplitPane[] { sourceSplit, sourceAndLibrary, mainSplit };
        splitRatios = new double[] { 0.17, 0.72, 0.55 };
        for (int i = 0; i < splitPanes.length; i++) {
            final int idx = i;
            splitPanes[i].addPropertyChangeListener("dividerLocation", evt -> rememberDivider(idx));
        }
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layoutDividers();
            }
        });
        SwingUtilities.invokeLater(this::layoutDividers);

        return mainSplit;
    }

    /** 在 1..total-1 之间按比例放分隔条，避免窗口过窄时位置非法。 */
    private void layoutDividers() {
        if (splitPanes == null) {
            return;
        }
        for (int i = 0; i < splitPanes.length; i++) {
            JSplitPane sp = splitPanes[i];
            int total = sp.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
                    ? sp.getWidth() : sp.getHeight();
            if (total <= 0) {
                total = 1000;
            }
            int loc = (int) Math.round(total * splitRatios[i]);
            sp.setDividerLocation(Math.max(1, Math.min(loc, total - 1)));
        }
    }

    /** 分隔条位置变化时记住比例，窗口缩放后仍保持布局。 */
    private void rememberDivider(int index) {
        JSplitPane sp = splitPanes[index];
        int total = sp.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
                ? sp.getWidth() : sp.getHeight();
        if (total > 0) {
            splitRatios[index] = Math.max(0.05, Math.min(0.95, sp.getDividerLocation() / (double) total));
        }
    }

    private void wireEvents() {
        sourceList.setOnPageSelected(ref -> {
            try {
                // 切页时总是重新适应窗口，避免上一页的手动比例把新页撑出视野
                pageView.fitToWindow();
                pageView.showPage(ref, selectionDpi);
                updateStatus();
            } catch (Exception ex) {
                error("无法显示该页：" + ex.getMessage());
            }
        });
        pageView.setOnStatus(this::updateStatus);
        pageView.setOnSelectionCreated((item, pageRect) -> {
            store.addSelection(item);
            library.refresh();
            library.setSelectedItem(item);
            double pageW = pageView.getPageRef() == null ? 0
                    : pageView.getPageRef().doc.pageSize(pageView.getPageRef().pageIndex).getWidth();
            String extra = pageW > 0 && item.getPointWidth() > pageW - 20
                    ? String.format("（注意：比页宽 %.0fpt 还宽，放入时会被自动缩小，建议重新框选得更窄一些）", pageW)
                    : "";
            status.setText(String.format("已框选：%.0f × %.0f pt — 把它从右下角列表拖到拼版页即可 %s",
                    item.getPointWidth(), item.getPointHeight(), extra));
        });
        canvas.setOnStatus(this::updateStatus);
        dpiSpinner.addChangeListener(e -> {
            selectionDpi = Float.parseFloat(String.valueOf(dpiSpinner.getValue()));
            PageRef ref = pageView.getPageRef();
            if (ref != null) {
                try {
                    pageView.rerender(selectionDpi);
                } catch (Exception ex) {
                    error("重新渲染失败：" + ex.getMessage());
                }
            }
            updateStatus();
        });
        library.setOnInsert(this::insertSelected);
        library.setOnDelete(() -> {
            SelectionItem item = library.getSelected();
            if (item != null) {
                store.removeSelection(item);
                library.refresh();
                updateStatus();
            }
        });
    }

    private void installShortcuts() {
        JComponent root = getRootPane();
        int menu = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu), "undo", () -> {
            store.undo();
            updateStatus();
        });
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_Y, menu), "redo", () -> {
            store.redo();
            updateStatus();
        });
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "del", () -> {
            canvas.deleteSelected();
            updateStatus();
        });
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "rot", () -> canvas.rotateSelected());
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_O, menu), "open", this::importPdfs);
        // 缩放快捷键
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_0, menu), "fitCanvas", () -> canvas.fitToWindow());
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, menu), "zoomOutCanvas", () -> canvas.zoomOut());
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menu), "zoomInCanvas", () -> canvas.zoomIn());
    }

    private void bind(JComponent c, KeyStroke ks, String name, Runnable action) {
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        c.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ---------- 动作 ----------

    private void importPdfs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导入 PDF（可多选）");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("PDF 文件 (*.pdf)", "pdf"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        List<String> failed = new ArrayList<>();
        File[] files = chooser.getSelectedFiles();
        for (File f : files) {
            try {
                SourceDoc doc = new SourceDoc(f);
                sourceList.addDoc(doc);
            } catch (Exception ex) {
                failed.add(f.getName() + "：" + ex.getMessage());
            }
        }
        if (!failed.isEmpty()) {
            JOptionPane.showMessageDialog(this, "以下文件打开失败：\n" + String.join("\n", failed),
                    "部分文件失败", JOptionPane.WARNING_MESSAGE);
        }
        updateStatus();
    }

    private void removeCurrentSource() {
        PageRef ref = sourceList.selectedPage();
        if (ref == null) {
            return;
        }
        sourceList.removeDoc(ref.doc);
        pageView.clear();
        updateStatus();
    }

    private void insertSelected() {
        SelectionItem item = library.getSelected();
        if (item == null) {
            info("请先在右下角“框选内容”列表里选中一项");
            return;
        }
        canvas.place(item, null, null);
        updateStatus();
    }

    private void exportPdf() {
        if (totalItems() == 0) {
            info("拼版页还是空的：先在源页上框选，再把内容拖到拼版页。");
            return;
        }
        JFileChooser chooser = PdfExporter.newSaveChooser();
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File out = new File(PdfExporter.normalizeExtension(chooser.getSelectedFile().getAbsolutePath()));
        try {
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
            PdfExporter.export(store.getPages(), out);
        } catch (Exception ex) {
            error("导出失败：" + ex.getMessage());
            return;
        } finally {
            setCursor(java.awt.Cursor.getDefaultCursor());
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "已导出：\n" + out.getAbsolutePath() + "\n\n是否打开所在文件夹？",
                "导出成功", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            openFolder(out.getParentFile());
        }
    }

    private void openFolder(File dir) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir);
            }
        } catch (Exception ignored) {
            // 打开文件夹失败不影响导出结果
        }
    }

    private int totalItems() {
        int n = 0;
        for (juanzi.model.CompPage p : store.getPages()) {
            n += p.getItems().size();
        }
        return n;
    }

    // ---------- 状态栏 ----------

    private void updateStatus() {
        PageRef ref = pageView.getPageRef();
        StringBuilder sb = new StringBuilder();
        if (ref != null) {
            sb.append(String.format("源：%s 第 %d 页 | 框选精度 %.0f DPI | 源页缩放 %.0f%%",
                    ref.doc.getDisplayName(), ref.pageIndex + 1, selectionDpi,
                    pageView.getZoom() * 100));
        } else {
            sb.append("尚未导入 PDF");
        }
        juanzi.model.CompPage cur = canvas.currentPage();
        PlacedImage sel = canvas.getSelected();
        sb.append(String.format("  ||  拼版：第 %d/%d 页 %.0f×%.0fpt，共 %d 个贴图，缩放 %.0f%%",
                canvas.getPageIndex() + 1, store.getPages().size(),
                cur.getWidth(), cur.getHeight(), totalItems(), canvas.getZoom() * 100));
        if (sel != null) {
            sb.append(String.format("  | 选中 %.0f×%.0f pt%s", sel.getWidth(), sel.getHeight(),
                    sel.getRotation() != 0 ? " 旋转" + sel.getRotation() + "°" : ""));
        }
        status.setText(sb.toString());
    }

    private void info(String msg) {
        JOptionPane.showMessageDialog(this, msg, "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "出错了", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 使用默认外观
        }
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
