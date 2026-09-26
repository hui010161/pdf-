package juanzi.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

import javax.swing.JScrollPane;
import javax.swing.JViewport;

/**
 * 找“真正能看见多少像素”的小工具。
 *
 * <p>面板放在 JScrollPane 里时，它的直接父容器是 JViewport（或视图面板），
 * 用 getParent().getWidth() 估算可视区域并不可靠：滚动条会占掉一部分，
 * 而且 JSplitPane 内部还包了一层。必须用 {@link JViewport#getExtentSize()}。</p>
 */
public final class ViewportUtil {

    private ViewportUtil() {
    }

    /** 返回该组件所在滚动视口的可视尺寸；没有滚动视口时退回父容器尺寸。 */
    public static Dimension viewportExtent(Component c) {
        JViewport vp = findViewport(c);
        if (vp != null) {
            Dimension d = vp.getExtentSize();
            if (d != null && d.width > 0 && d.height > 0) {
                // 垂直/水平滚动条会吃宽度，保守一点多留一点余地
                int w = d.width;
                int h = d.height;
                JScrollPane sp = (JScrollPane) javax.swing.SwingUtilities.getAncestorOfClass(JScrollPane.class, vp);
                if (sp != null) {
                    if (sp.getVerticalScrollBar().isVisible()) {
                        w -= sp.getVerticalScrollBar().getWidth();
                    }
                    if (sp.getHorizontalScrollBar().isVisible()) {
                        h -= sp.getHorizontalScrollBar().getHeight();
                    }
                }
                return new Dimension(Math.max(0, w), Math.max(0, h));
            }
        }
        Container p = c.getParent();
        if (p != null && p.getWidth() > 0 && p.getHeight() > 0) {
            return new Dimension(p.getWidth(), p.getHeight());
        }
        return new Dimension(0, 0);
    }

    /** 向上查找包含该组件的 JViewport。 */
    public static JViewport findViewport(Component c) {
        Container p = c.getParent();
        while (p != null) {
            if (p instanceof JViewport vp) {
                return vp;
            }
            p = p.getParent();
        }
        return null;
    }
}
