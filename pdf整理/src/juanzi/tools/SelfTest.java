package juanzi.tools;

import java.awt.image.BufferedImage;
import java.io.File;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import juanzi.model.CompPage;
import juanzi.model.PlacedImage;
import juanzi.model.SelectionItem;
import juanzi.pdf.PdfExporter;
import juanzi.pdf.SourceDoc;

/**
 * 无界面自检：模拟“导入 PDF -> 框选 -> 拼版 -> 导出”，用于验证整条链路。
 * 用法：java -cp build;lib\* juanzi.tools.SelfTest <源PDF> <输出PDF>
 */
public final class SelfTest {

    private SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        String src = args.length > 0 ? args[0] : "sample.pdf";
        String out = args.length > 1 ? args[1] : "out/self-test.pdf";
        float dpi = 300f;

        File outFile = new File(out);
        if (outFile.getParentFile() != null) {
            outFile.getParentFile().mkdirs();
        }

        SourceDoc doc = new SourceDoc(new File(src));
        System.out.println("[1] 导入成功：" + doc.getDisplayName() + "，共 " + doc.getPageCount() + " 页");

        // [2] 按 300 DPI 渲染源页，并模拟 5 次手动框选（每块 = 一个题号块）
        BufferedImage page0 = doc.render(0, dpi);
        int w = page0.getWidth();
        // 真实题块范围（由墨迹行检测得到，占页高比例：起, 止）
        double[][] boxes = {
                { 0.0325, 0.1080 },
                { 0.1568, 0.2340 },
                { 0.3184, 0.3777 },
                { 0.4641, 0.5296 },
                { 0.6209, 0.7018 } };
        java.util.List<SelectionItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < boxes.length; i++) {
            int y0 = (int) (page0.getHeight() * boxes[i][0]);
            int y1 = (int) (page0.getHeight() * boxes[i][1]);
            BufferedImage crop = page0.getSubimage(0, y0, w, y1 - y0);
            items.add(new SelectionItem("P1 第" + (i + 1) + "题", crop,
                    juanzi.model.ImageUtils.makeThumb(crop), w / dpi * 72.0, (y1 - y0) / dpi * 72.0));
        }
        System.out.println("[2] 框选完成：" + items.size() + " 块，每块 "
                + String.format("%.0f pt 宽", items.get(0).getPointWidth()));

        // 中间产物：把框选内容原样导出，便于对比
        File cropDir = new File(outFile.getParentFile(), "crops");
        cropDir.mkdirs();
        for (int i = 0; i < items.size(); i++) {
            javax.imageio.ImageIO.write(items.get(i).getImage(), "png",
                    new File(cropDir, "crop-" + (i + 1) + ".png"));
        }

        // [3] 拼版：按顺序放到页面上，超出页高就新开一页
        java.util.List<CompPage> pages = new java.util.ArrayList<>();
        CompPage current = CompPage.a4("第1页");
        pages.add(current);
        double gap = 26;
        double y = 30;
        for (SelectionItem item : items) {
            double h = item.getPointHeight();
            if (y + h > current.getHeight() - 20) {
                current = CompPage.a4("第" + (pages.size() + 1) + "页");
                pages.add(current);
                y = 30;
            }
            current.add(new PlacedImage(item, 25, y, item.getPointWidth(), h));
            y += h + gap;
        }
        System.out.println("[3] 拼版完成：" + pages.size() + " 页，贴图 "
                + pages.stream().mapToInt(p -> p.getItems().size()).sum() + " 个");

        // [4] 导出
        PdfExporter.export(pages, outFile);
        System.out.println("[4] 导出完成：" + outFile.getAbsolutePath() + " （"
                + outFile.length() / 1024 + " KB）");

        // [5] 回读校验
        try (PDDocument check = Loader.loadPDF(outFile)) {
            System.out.println("[5] 回读校验：页数=" + check.getNumberOfPages()
                    + "，首页尺寸=" + check.getPage(0).getMediaBox());
            PDFRenderer r = new PDFRenderer(check);
            BufferedImage rendered = r.renderImageWithDPI(0, 60);
            System.out.println("    首页预览 " + rendered.getWidth() + "x" + rendered.getHeight() + " px，校验通过");
        }
        doc.close();

        // [6] 输出预览图，便于肉眼确认版式
        File previewDir = new File(outFile.getParentFile(), "preview");
        previewDir.mkdirs();
        PdfExporter.exportPng(pages, previewDir, "check");
        System.out.println("[6] 预览图已生成：" + previewDir.getAbsolutePath());
    }
}
