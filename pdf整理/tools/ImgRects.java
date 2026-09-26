import java.io.File;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

/** 打印每页图片对象实际占据的矩形（pt）与像素尺寸，用于核对排版。 */
public class ImgRects extends PDFStreamEngine {

    private final PDPage page;

    public ImgRects(PDPage page) {
        this.page = page;
        addOperator(new Concatenate(this));
        addOperator(new DrawObject(this));
        addOperator(new SetGraphicsStateParameters(this));
        addOperator(new Save(this));
        addOperator(new Restore(this));
        addOperator(new SetMatrix(this));
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws java.io.IOException {
        if ("Do".equals(operator.getName()) && !operands.isEmpty()
                && operands.get(0) instanceof COSName name) {
            PDXObject xo = getResources() == null ? null : getResources().getXObject(name);
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            double pw = page.getMediaBox().getWidth();
            double ph = page.getMediaBox().getHeight();
            if (xo instanceof PDImageXObject img) {
                double x = ctm.getTranslateX();
                double y = ctm.getTranslateY();
                double w = ctm.getScalingFactorX();
                double h = ctm.getScalingFactorY();
                boolean out = x + w > pw + 1 || y + h > ph + 1;
                System.out.printf("  image %s: x=%.1f y=%.1f w=%.1f h=%.1f  pix=%dx%d%s%n",
                        name.getName(), x, y, w, h, img.getWidth(), img.getHeight(),
                        out ? "   <== OUT OF PAGE" : "");
            } else if (xo instanceof PDFormXObject form) {
                System.out.println("  form " + name.getName());
                showForm(form);
            }
        } else {
            super.processOperator(operator, operands);
        }
    }

    public static void main(String[] args) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new File(args[0]))) {
            int i = 0;
            for (PDPage p : doc.getPages()) {
                System.out.println("page " + i++ + ":");
                ImgRects e = new ImgRects(p);
                e.processPage(p);
            }
        }
    }
}
