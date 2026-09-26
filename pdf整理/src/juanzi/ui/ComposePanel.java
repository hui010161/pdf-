package juanzi.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;

import juanzi.model.CompPage;
import juanzi.model.ComposerStore;

/**
 * 拼版工作区：顶部一排页码按钮（点第几页就跳到第几页）+ 功能按钮，下面整块都是拼版画布。
 *
 * <p>原先左侧的缩略图栏占地方又不好缩，已去掉；页序完全由顶部这排页码表示。</p>
 */
public class ComposePanel extends JPanel {

    private static final int CELL_WIDTH = 44;
    private static final int CELL_HEIGHT = 30;

    private final ComposerStore store;
    private final JList<CompPage> pageList = new JList<>(new DefaultListModel<>());
    private final ComposeView canvas;
    private final JLabel pageCount = new JLabel();

    public ComposePanel(ComposerStore store) {
        super(new BorderLayout());
        this.store = store;
        this.canvas = new ComposeView(store);

        // ---- 顶部：页码条 ----
        pageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pageList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        pageList.setVisibleRowCount(1);
        pageList.setFixedCellWidth(CELL_WIDTH);
        pageList.setFixedCellHeight(CELL_HEIGHT);
        pageList.setCellRenderer(new PageNumberRenderer());
        pageList.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        pageList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && pageList.getSelectedIndex() >= 0) {
                canvas.setPageIndex(pageList.getSelectedIndex());
                pageCount.setText(countText());
            }
        });

        JScrollPane pageScroll = new JScrollPane(pageList,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pageScroll.setBorder(null);
        pageScroll.setPreferredSize(new Dimension(320, CELL_HEIGHT + 14));
        pageScroll.getHorizontalScrollBar().setUnitIncrement(CELL_WIDTH);

        JPanel pageBar = new JPanel(new BorderLayout(6, 0));
        pageBar.setBorder(BorderFactory.createTitledBorder("拼版页（点数字跳页）"));
        pageBar.add(pageScroll, BorderLayout.CENTER);
        pageBar.add(pageCount, BorderLayout.EAST);
        pageCount.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

        // ---- 顶部右侧：功能按钮 ----
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
        JButton add = new JButton("新建页");
        add.setToolTipText("在当前页之后插入一个 A4 页");
        add.addActionListener(e -> addPage());
        JButton dup = new JButton("复制页");
        dup.setToolTipText("把当前页连同上面的贴图复制一份到后面");
        dup.addActionListener(e -> duplicatePage());
        JButton del = new JButton("删除页");
        del.setToolTipText("删除当前页");
        del.addActionListener(e -> removePage());
        JButton up = new JButton("上移");
        up.setToolTipText("当前页往前挪一页");
        up.addActionListener(e -> move(-1));
        JButton down = new JButton("下移");
        down.setToolTipText("当前页往后挪一页");
        down.addActionListener(e -> move(1));
        JButton size = new JButton("页面尺寸…");
        size.setToolTipText("改当前页的纸张尺寸（A4 / A4 横向 / B5 / 自定义）");
        size.addActionListener(e -> changePageSize());
        actions.add(add);
        actions.add(dup);
        actions.add(del);
        actions.add(up);
        actions.add(down);
        actions.add(size);

        JPanel top = new JPanel(new BorderLayout());
        top.add(pageBar, BorderLayout.CENTER);
        top.add(actions, BorderLayout.EAST);

        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.getVerticalScrollBar().setUnitIncrement(24);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(24);
        canvasScroll.setBorder(BorderFactory.createTitledBorder("拼版画布（把右侧“框选内容”拖到这里）"));

        add(top, BorderLayout.NORTH);
        add(canvasScroll, BorderLayout.CENTER);

        store.addPageListener(this::refresh);
        refresh();
    }

    public ComposeView getCanvas() {
        return canvas;
    }

    private String countText() {
        return String.format("共 %d 页 · 当前第 %d 页",
                store.getPages().size(), Math.max(1, pageList.getSelectedIndex() + 1));
    }

    private void addPage() {
        int idx = pageList.getSelectedIndex();
        if (idx < 0) {
            idx = store.getPages().size() - 1;
        }
        CompPage p = CompPage.a4("第" + (store.getPages().size() + 1) + "页");
        store.addPage(idx + 1, p);
        selectIndex(idx + 1);
    }

    private void duplicatePage() {
        CompPage cur = pageList.getSelectedValue();
        if (cur == null) {
            return;
        }
        int idx = store.indexOf(cur);
        store.addPage(idx + 1, cur.deepCopy());
        selectIndex(idx + 1);
    }

    private void removePage() {
        CompPage p = pageList.getSelectedValue();
        if (p == null) {
            return;
        }
        if (store.getPages().size() <= 1) {
            JOptionPane.showMessageDialog(this, "至少要保留一页", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int idx = store.indexOf(p);
        store.removePage(p);
        selectIndex(Math.min(idx, store.getPages().size() - 1));
    }

    private void move(int delta) {
        CompPage p = pageList.getSelectedValue();
        if (p == null) {
            return;
        }
        int target = store.indexOf(p) + delta;
        store.movePage(p, delta);
        if (target >= 0 && target < store.getPages().size()) {
            selectIndex(target);
        }
    }

    private void selectIndex(int index) {
        if (index < 0 || index >= pageList.getModel().getSize()) {
            return;
        }
        pageList.setSelectedIndex(index);
        pageList.ensureIndexIsVisible(index);
        canvas.setPageIndex(index);
        pageCount.setText(countText());
    }

    /** 改当前页的纸张尺寸；如果有贴图放不下，等比缩小到版心内。 */
    private void changePageSize() {
        CompPage page = pageList.getSelectedValue();
        if (page == null) {
            return;
        }
        Object[] presets = {
                "A4 纵向 595×842",
                "A4 横向 842×595",
                "B5 纵向 499×709",
                "自定义…" };
        Object choice = JOptionPane.showInputDialog(this,
                String.format("当前：%.0f × %.0f pt%n请选择新的页面尺寸：", page.getWidth(), page.getHeight()),
                "页面尺寸", JOptionPane.QUESTION_MESSAGE, null, presets, presets[0]);
        if (choice == null) {
            return;
        }
        double w;
        double h;
        switch (String.valueOf(choice)) {
            case "A4 横向 842×595" -> { w = 842; h = 595; }
            case "B5 纵向 499×709" -> { w = 499; h = 709; }
            case "自定义…" -> {
                javax.swing.JTextField wf = new javax.swing.JTextField(String.format("%.0f", page.getWidth()), 6);
                javax.swing.JTextField hf = new javax.swing.JTextField(String.format("%.0f", page.getHeight()), 6);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
                row.add(new javax.swing.JLabel("宽 pt"));
                row.add(wf);
                row.add(new javax.swing.JLabel("高 pt"));
                row.add(hf);
                int ok = JOptionPane.showConfirmDialog(this, row, "自定义页面尺寸",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (ok != JOptionPane.OK_OPTION) {
                    return;
                }
                try {
                    w = Double.parseDouble(wf.getText().trim());
                    h = Double.parseDouble(hf.getText().trim());
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "请输入数字", "提示", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (w < 100 || h < 100 || w > 3000 || h > 3000) {
                    JOptionPane.showMessageDialog(this, "宽度/高度请填 100 ~ 3000 pt",
                            "提示", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            default -> { w = 595; h = 842; }
        }

        double maxW = w - 20;
        double maxH = h - 10;
        int shrunk = 0;
        for (juanzi.model.PlacedImage it : page.getItems()) {
            double k = Math.min(1.0, Math.min(maxW / it.getWidth(), maxH / it.getHeight()));
            if (k < 1.0) {
                it.setSize(it.getWidth() * k, it.getHeight() * k);
                shrunk++;
            }
        }
        store.beginEdit();
        page.setSize(w, h);
        // 把越界的贴图拉回页面内
        for (juanzi.model.PlacedImage it : page.getItems()) {
            java.awt.geom.Rectangle2D b = it.rotatedBounds();
            double dx = 0;
            double dy = 0;
            if (b.getMinX() < 0) {
                dx = -b.getMinX();
            } else if (b.getMaxX() > w) {
                dx = w - b.getMaxX();
            }
            if (b.getMinY() < 0) {
                dy = -b.getMinY();
            } else if (b.getMaxY() > h) {
                dy = h - b.getMaxY();
            }
            if (dx != 0 || dy != 0) {
                it.moveBy(dx, dy);
            }
        }
        store.fireAll();
        if (shrunk > 0) {
            JOptionPane.showMessageDialog(this, "有 " + shrunk + " 个贴图超出新页面，已自动等比缩小。",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void refresh() {
        CompPage sel = pageList.getSelectedValue();
        DefaultListModel<CompPage> m = (DefaultListModel<CompPage>) pageList.getModel();
        m.clear();
        for (CompPage p : store.getPages()) {
            m.addElement(p);
        }
        int idx = sel == null ? -1 : store.indexOf(sel);
        if (idx < 0) {
            idx = Math.min(Math.max(0, canvas.getPageIndex()), store.getPages().size() - 1);
        }
        if (idx >= 0 && idx < m.size()) {
            pageList.setSelectedIndex(idx);
            pageList.ensureIndexIsVisible(idx);
        }
        pageCount.setText(countText());
        pageList.repaint();
        revalidate();
    }

    /** 页码按钮：只显示数字，选中态高亮。 */
    private static class PageNumberRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                     boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setText(String.valueOf(index + 1));
            setHorizontalAlignment(CENTER);
            setVerticalAlignment(CENTER);
            setHorizontalTextPosition(CENTER);
            setIcon(null);
            setToolTipText("跳到第 " + (index + 1) + " 页");
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(2, 2, 2, 2),
                    isSelected
                            ? BorderFactory.createLineBorder(new Color(0x1E, 0x88, 0xE5), 2)
                            : BorderFactory.createLineBorder(new Color(0xBB, 0xBB, 0xBB), 1)));
            setBackground(isSelected ? new Color(0xDD, 0xEE, 0xFF) : Color.WHITE);
            setOpaque(true);
            return this;
        }
    }
}
