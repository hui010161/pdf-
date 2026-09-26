package juanzi.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

import juanzi.model.ComposerStore;
import juanzi.model.SelectionItem;

/**
 * 右侧：框选内容库，可拖拽到拼版页。
 */
public class SelectionListPanel extends JPanel {

    /** 选区拖拽使用的数据格式。 */
    public static final DataFlavor SELECTION_FLAVOR =
            new DataFlavor(SelectionItem.class, "application/x-juanzi-selection");

    private final ComposerStore store;
    private final DefaultListModel<SelectionItem> model = new DefaultListModel<>();
    private final JList<SelectionItem> list = new JList<>(model);
    private Runnable onInsert;
    private Runnable onDelete;

    public SelectionListPanel(ComposerStore store) {
        super(new BorderLayout(0, 4));
        this.store = store;
        setBorder(BorderFactory.createTitledBorder("框选内容（拖到右侧拼版页）"));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ThumbRenderer());
        list.setFixedCellHeight(66);
        list.setDragEnabled(true);
        list.setTransferHandler(new DragOutHandler());

        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(240, 300));
        add(sp, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton insert = new JButton("放入当前页");
        insert.addActionListener(e -> {
            if (onInsert != null) {
                onInsert.run();
            }
        });
        JButton del = new JButton("删除");
        del.addActionListener(e -> {
            if (onDelete != null) {
                onDelete.run();
            }
        });
        buttons.add(insert);
        buttons.add(del);
        add(buttons, BorderLayout.SOUTH);

        store.addLibraryListener(this::refresh);
    }

    public void setOnInsert(Runnable r) {
        this.onInsert = r;
    }

    public void setOnDelete(Runnable r) {
        this.onDelete = r;
    }

    public SelectionItem getSelected() {
        return list.getSelectedValue();
    }

    public void setSelectedItem(SelectionItem item) {
        if (item != null && model.contains(item)) {
            list.setSelectedValue(item, true);
        }
    }

    public void refresh() {
        SelectionItem selected = list.getSelectedValue();
        model.clear();
        for (SelectionItem it : store.getLibrary()) {
            model.addElement(it);
        }
        if (selected != null && model.contains(selected)) {
            list.setSelectedValue(selected, true);
        } else if (!model.isEmpty()) {
            list.setSelectedIndex(model.size() - 1);
        }
    }

    /** 列表项：缩略图 + 名称 + 尺寸。 */
    private static class ThumbRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                     boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            SelectionItem item = (SelectionItem) value;
            setText(String.format("<html><b>%s</b><br><font color='#666666'>%.0f × %.0f pt</font></html>",
                    item.getName(), item.getPointWidth(), item.getPointHeight()));
            setIcon(new javax.swing.ImageIcon(item.getThumbnail()));
            setIconTextGap(8);
            setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
            return this;
        }
    }

    /** 从列表往外拖。 */
    private class DragOutHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            SelectionItem item = list.getSelectedValue();
            if (item == null) {
                return null;
            }
            return new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] { SELECTION_FLAVOR };
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return SELECTION_FLAVOR.equals(flavor);
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    if (!SELECTION_FLAVOR.equals(flavor)) {
                        throw new UnsupportedFlavorException(flavor);
                    }
                    return item;
                }
            };
        }
    }

    /** 供预览使用的小图面板。 */
    static class ThumbLabel extends JLabel {
        private final BufferedImage img;

        ThumbLabel(BufferedImage img) {
            this.img = img;
            setPreferredSize(new Dimension(img.getWidth(), img.getHeight()));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }
    }
}
