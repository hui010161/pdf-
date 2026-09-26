package juanzi.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import juanzi.pdf.SourceDoc;

/**
 * 左侧：已导入 PDF 的目录树（文档 -> 页）。
 */
public class SourceListPanel extends JScrollPane {

    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("导入的 PDF");
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private final JTree tree = new JTree(model);
    private final List<SourceDoc> docs = new ArrayList<>();
    private Consumer<PageRef> onPageSelected;
    private boolean suppressEvents;

    public SourceListPanel() {
        super();
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(22);
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setLeafIcon(null);
        renderer.setOpenIcon(null);
        renderer.setClosedIcon(null);
        tree.setCellRenderer(renderer);
        tree.addTreeSelectionListener(e -> {
            if (suppressEvents || onPageSelected == null) {
                return;
            }
            PageRef ref = selectedPage();
            if (ref != null) {
                onPageSelected.accept(ref);
            }
        });
        setViewportView(tree);
        setBorder(javax.swing.BorderFactory.createTitledBorder("源文件"));
    }

    public void setOnPageSelected(Consumer<PageRef> c) {
        this.onPageSelected = c;
    }

    public List<SourceDoc> getDocs() {
        return docs;
    }

    public void addDoc(SourceDoc doc) throws java.io.IOException {
        docs.add(doc);
        DefaultMutableTreeNode docNode = new DefaultMutableTreeNode(doc);
        for (int i = 0; i < doc.getPageCount(); i++) {
            docNode.add(new DefaultMutableTreeNode(new PageRef(doc, i)));
        }
        model.insertNodeInto(docNode, root, root.getChildCount());
        tree.expandPath(new TreePath(docNode.getPath()));
        selectPage(new PageRef(doc, 0));
    }

    public void removeDoc(SourceDoc doc) {
        int idx = docs.indexOf(doc);
        if (idx < 0) {
            return;
        }
        docs.remove(idx);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(idx);
        model.removeNodeFromParent(node);
        doc.close();
    }

    public void selectPage(PageRef ref) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode docNode = (DefaultMutableTreeNode) root.getChildAt(i);
            for (int j = 0; j < docNode.getChildCount(); j++) {
                DefaultMutableTreeNode pageNode = (DefaultMutableTreeNode) docNode.getChildAt(j);
                if (pageNode.getUserObject() instanceof PageRef pr && pr.equals(ref)) {
                    suppressEvents = true;
                    tree.setSelectionPath(new TreePath(pageNode.getPath()));
                    tree.scrollPathToVisible(new TreePath(pageNode.getPath()));
                    suppressEvents = false;
                    return;
                }
            }
        }
    }

    public PageRef selectedPage() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object o = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return (o instanceof PageRef pr) ? pr : null;
    }
}
