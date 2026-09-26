package juanzi.ui;

import juanzi.pdf.SourceDoc;

/**
 * 指向某个源 PDF 的某一页。
 */
public class PageRef {

    public final SourceDoc doc;
    public final int pageIndex;

    public PageRef(SourceDoc doc, int pageIndex) {
        this.doc = doc;
        this.pageIndex = pageIndex;
    }

    @Override
    public String toString() {
        return "第 " + (pageIndex + 1) + " 页";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PageRef other)) {
            return false;
        }
        return other.doc == doc && other.pageIndex == pageIndex;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(doc) * 31 + pageIndex;
    }
}
