package juanzi.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 拼版工程：选区库 + 页面列表 + 撤销/重做。
 */
public class ComposerStore {

    /** 一次撤销快照。 */
    private static final class Snapshot {
        final List<CompPage> pages;
        final List<SelectionItem> library;

        Snapshot(List<CompPage> pages, List<SelectionItem> library) {
            this.pages = pages;
            this.library = library;
        }
    }

    private static final int MAX_UNDO = 100;

    private final List<CompPage> pages = new ArrayList<>();
    private final List<SelectionItem> library = new ArrayList<>();

    private final List<Snapshot> undoStack = new ArrayList<>();
    private final List<Snapshot> redoStack = new ArrayList<>();

    private final List<Runnable> pageListeners = new ArrayList<>();
    private final List<Runnable> libraryListeners = new ArrayList<>();

    public ComposerStore() {
        pages.add(CompPage.a4("第1页"));
    }

    // ---------- 监听 ----------

    public void addPageListener(Runnable r) {
        pageListeners.add(r);
    }

    public void addLibraryListener(Runnable r) {
        libraryListeners.add(r);
    }

    private void firePages() {
        for (Runnable r : new ArrayList<>(pageListeners)) {
            r.run();
        }
    }

    private void fireLibrary() {
        for (Runnable r : new ArrayList<>(libraryListeners)) {
            r.run();
        }
    }

    public void fireAll() {
        firePages();
        fireLibrary();
    }

    // ---------- 选区库 ----------

    public List<SelectionItem> getLibrary() {
        return library;
    }

    public void addSelection(SelectionItem item) {
        pushUndo();
        library.add(item);
        fireLibrary();
    }

    public void removeSelection(SelectionItem item) {
        pushUndo();
        library.remove(item);
        for (CompPage p : pages) {
            p.getItems().removeIf(pi -> pi.getSource() == item);
        }
        fireAll();
    }

    // ---------- 页面 ----------

    public List<CompPage> getPages() {
        return pages;
    }

    public int indexOf(CompPage page) {
        return pages.indexOf(page);
    }

    public CompPage getPage(int index) {
        return pages.get(index);
    }

    public void addPage(CompPage page) {
        addPage(pages.size(), page);
    }

    public void addPage(int index, CompPage page) {
        pushUndo();
        index = Math.max(0, Math.min(index, pages.size()));
        pages.add(index, page);
        renamePages();
        firePages();
    }

    public void removePage(CompPage page) {
        if (pages.size() <= 1) {
            return;
        }
        pushUndo();
        pages.remove(page);
        renamePages();
        firePages();
    }

    public void movePage(CompPage page, int delta) {
        int i = pages.indexOf(page);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= pages.size()) {
            return;
        }
        pushUndo();
        pages.remove(i);
        pages.add(j, page);
        renamePages();
        firePages();
    }

    private void renamePages() {
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).setTitle("第" + (i + 1) + "页");
        }
    }

    // ---------- 撤销 / 重做 ----------

    public void pushUndo() {
        undoStack.add(new Snapshot(copyPages(), new ArrayList<>(library)));
        if (undoStack.size() > MAX_UNDO) {
            undoStack.remove(0);
        }
        redoStack.clear();
    }

    /** 在连续操作（如拖动）开始时调用一次即可。 */
    public void beginEdit() {
        pushUndo();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.add(new Snapshot(copyPages(), new ArrayList<>(library)));
        restore(undoStack.remove(undoStack.size() - 1));
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.add(new Snapshot(copyPages(), new ArrayList<>(library)));
        restore(redoStack.remove(redoStack.size() - 1));
    }

    private void restore(Snapshot s) {
        pages.clear();
        pages.addAll(s.pages);
        library.clear();
        library.addAll(s.library);
        fireAll();
    }

    private List<CompPage> copyPages() {
        List<CompPage> copy = new ArrayList<>();
        for (CompPage p : pages) {
            copy.add(p.deepCopy());
        }
        return copy;
    }
}
