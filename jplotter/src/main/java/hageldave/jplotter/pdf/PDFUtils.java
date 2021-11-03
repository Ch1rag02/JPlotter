package hageldave.jplotter.pdf;

import hageldave.jplotter.util.Utils;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShading;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType4;

import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.OutputStream;

public class PDFUtils {

    /**
     * Creates a point at the specified position with the given radius.
     *
     * @param cs content stream where the point is appended to
     * @param x x coordinate of the point
     * @param y y coordinate of the point
     * @param radius radius of the point
     * @return the resulting content stream
     * @throws IOException
     */
    public static PDPageContentStream createPDFPoint(PDPageContentStream cs,
                                                     float x, float y, float radius) throws IOException {
        final float k = 0.552284749831f;
        cs.moveTo(x - radius, y);
        cs.curveTo(x - radius, y + k * radius, x - k * radius, y + radius, x, y + radius);
        cs.curveTo(x + k * radius, y + radius, x + radius, y + k * radius, x + radius, y);
        cs.curveTo(x + radius, y - k * radius, x + k * radius, y - radius, x, y - radius);
        cs.curveTo(x - k * radius, y - radius, x - radius, y - k * radius, x - radius, y);
        return cs;
    }

    /**
     * Creates a simple segment in the pdf document.
     *
     * @param cs content stream where the segment is appended to
     * @param p0 starting point of the segment
     * @param p1 ending point of the segment
     * @return the resulting content stream
     * @throws IOException
     */
    public static PDPageContentStream createPDFSegment(PDPageContentStream cs, Point2D p0,
                                                       Point2D p1) throws IOException {
        cs.moveTo((float) p0.getX(), (float) p0.getY());
        cs.lineTo((float) p1.getX(), (float) p1.getY());
        return cs;
    }

    /**
     * Creates a cubic bézier curve in the pdf document.
     *
     * @param cs content stream where the curve is appended to
     * @param p0 starting point of the curve
     * @param cP0 first control point
     * @param cP1 second control point
     * @param p1 ending point of the curve
     * @return the resulting content stream
     * @throws IOException
     */
    public static PDPageContentStream createPDFCurve(PDPageContentStream cs, Point2D p0, Point2D cP0,
                                                    Point2D cP1, Point2D p1) throws IOException {
        cs.moveTo((float) p0.getX(), (float) p0.getY());
        cs.curveTo((float) cP0.getX(), (float) cP0.getY(), (float) cP1.getX(),
                (float) cP1.getY(), (float) p1.getX(), (float) p1.getY());
        return cs;
    }

    /**
     * Adds a gouraud shaded triangle to the pdf document.
     * More information about Gouraud shading: https://en.wikipedia.org/wiki/Gouraud_shading
     *
     * @param doc PDF document holding the content stream
     * @param cs content stream where the curve is appended to
     * @param p0 coordinates of first vertex of the triangle
     * @param p1 coordinates of second vertex of the triangle
     * @param p2 coordinates of third vertex of the triangle
     * @param c0 color of the 'first coordinate' vertex of the triangle
     * @param c1 color of the 'second coordinate' vertex of the triangle
     * @param c2 color of the 'third coordinate' vertex of the triangle
     * @return the resulting content stream
     * @throws IOException
     */
    public static PDPageContentStream createPDFShadedTriangle(PDDocument doc, PDPageContentStream cs, Point2D p0,
                                                              Point2D p1, Point2D p2, Color c0, Color c1, Color c2) throws IOException {
        PDShadingType4 gouraudShading = new PDShadingType4(doc.getDocument().createCOSStream());
        gouraudShading.setShadingType(PDShading.SHADING_TYPE4);
        gouraudShading.setBitsPerFlag(8);
        gouraudShading.setBitsPerCoordinate(16);
        gouraudShading.setBitsPerComponent(8);

        COSArray decodeArray = new COSArray();
        decodeArray.add(COSInteger.ZERO);
        decodeArray.add(COSInteger.get(0xFFFF));
        decodeArray.add(COSInteger.ZERO);
        decodeArray.add(COSInteger.get(0xFFFF));
        decodeArray.add(COSInteger.ZERO);
        decodeArray.add(COSInteger.ONE);
        decodeArray.add(COSInteger.ZERO);
        decodeArray.add(COSInteger.ONE);
        decodeArray.add(COSInteger.ZERO);
        decodeArray.add(COSInteger.ONE);
        gouraudShading.setDecodeValues(decodeArray);
        gouraudShading.setColorSpace(PDDeviceRGB.INSTANCE);

        OutputStream os = ((COSStream) gouraudShading.getCOSObject()).createOutputStream();
        MemoryCacheImageOutputStream mcos = new MemoryCacheImageOutputStream(os);

        // Vertex 1, starts with flag1
        // (flags always 0 for vertices of start triangle)
        mcos.writeByte(0);
        // x1 y1 (left corner)
        mcos.writeShort((int) p0.getX());
        mcos.writeShort((int) p0.getY());
        // r1 g1 b1 (red)
        mcos.writeByte(c0.getRed());
        mcos.writeByte(c0.getGreen());
        mcos.writeByte(c0.getBlue());

        // Vertex 2, starts with flag2
        mcos.writeByte(0);
        // x2 y2 (top corner)
        mcos.writeShort((int) p1.getX());
        mcos.writeShort((int) p1.getY());
        // r2 g2 b2 (green)
        mcos.writeByte(c1.getRed());
        mcos.writeByte(c1.getGreen());
        mcos.writeByte(c1.getBlue());

        // Vertex 3, starts with flag3
        mcos.writeByte(0);
        // x3 y3 (right corner)
        mcos.writeShort((int) p2.getX());
        mcos.writeShort((int) p2.getY());
        // r3 g3 b3 (blue)
        mcos.writeByte(c2.getRed());
        mcos.writeByte(c2.getGreen());
        mcos.writeByte(c2.getBlue());
        mcos.close();

        os.close();
        cs.shadingFill(gouraudShading);
        return cs;
    }

    public static void writeShadedTriangle(MemoryCacheImageOutputStream outputStream, Point2D p0,
                                           Point2D p1, Point2D p2, Color c0, Color c1, Color c2) throws IOException {
        // Vertex 1, starts with flag1
        // (flags always 0 for vertices of start triangle)
        outputStream.writeByte(0);
        // x1 y1 (left corner)
        outputStream.writeShort((int) p0.getX());
        outputStream.writeShort((int) p0.getY());
        // r1 g1 b1 (red)
        outputStream.writeByte(c0.getRed());
        outputStream.writeByte(c0.getGreen());
        outputStream.writeByte(c0.getBlue());

        // Vertex 2, starts with flag2
        outputStream.writeByte(0);
        // x2 y2 (top corner)
        outputStream.writeShort((int) p1.getX());
        outputStream.writeShort((int) p1.getY());
        // r2 g2 b2 (green)
        outputStream.writeByte(c1.getRed());
        outputStream.writeByte(c1.getGreen());
        outputStream.writeByte(c1.getBlue());

        // Vertex 3, starts with flag3
        outputStream.writeByte(0);
        // x3 y3 (right corner)
        outputStream.writeShort((int) p2.getX());
        outputStream.writeShort((int) p2.getY());
        // r3 g3 b3 (blue)
        outputStream.writeByte(c2.getRed());
        outputStream.writeByte(c2.getGreen());
        outputStream.writeByte(c2.getBlue());
    }

    /**
     * Creates a text string in the pdf document.
     *
     * @param doc PDF document holding the content stream
     * @param cs content stream where the curve is appended to
     * @param txt text string that should be rendered in the document
     * @param position position where the text should be rendered
     * @param color color of the text
     * @param fontSize size of font
     * @param style style of font
     * @param angle rotation of the text
     * @return the resulting content stream
     * @throws IOException
     */
    public static PDPageContentStream createPDFText(PDDocument doc, PDPageContentStream cs, String txt,
                                                    Point2D position, Color color, int fontSize, int style, float angle) throws IOException {
        cs.setNonStrokingColor(color);
        cs.stroke();
        // set correct font
        PDType0Font font;
        switch (style) {
            case 1:
                font = PDType0Font.load(doc, PDFUtils.class.getResourceAsStream("/font/UbuntuMono-B.ttf"));
                cs.setFont(font, fontSize);
                break;
            case 2:
                font = PDType0Font.load(doc, PDFUtils.class.getResourceAsStream("/font/UbuntuMono-RI.ttf"));
                cs.setFont(font, fontSize);
                break;
            case (1|2):
                font = PDType0Font.load(doc, PDFUtils.class.getResourceAsStream("/font/UbuntuMono-BI.ttf"));
                cs.setFont(font, fontSize);
                break;
            default:
                font = PDType0Font.load(doc, PDFUtils.class.getResourceAsStream("/font/UbuntuMono-R.ttf"));
                cs.setFont(font, fontSize);
                break;
        }
        cs.beginText();
        AffineTransform at = new AffineTransform(1, 0.0, 0.0,
               1, position.getX(), position.getY());
        at.rotate(angle);
        cs.setTextMatrix(at);
        cs.showText(txt);
        cs.endText();
        return cs;
    }

    /**
     * Creates a text string in the pdf document with angle 0.
     *
     * @param doc PDF document holding the content stream
     * @param cs content stream where the curve is appended to
     * @param txt text string that should be rendered in the document
     * @param position position where the text should be rendered
     * @param color color of the text
     * @param fontSize size of font
     * @param style style of font
     * @return the resulting content stream
     * @throws IOException
     */
    public static PDPageContentStream createPDFText(PDDocument doc, PDPageContentStream cs, String txt,
                                                    Point2D position, Color color, int fontSize, int style) throws IOException {
        return createPDFText(doc, cs, txt, position, color, fontSize, style, 0);
    }

    /**
     * Swaps between PDF and AWT coordinates, AWT coordinate system
     * has its origin in the top left corner of a component and downwards pointing
     * y axis, whereas PDF has its origin in the bottom left corner of the viewport
     * (at least in JPlotter) and upwards pointing y axis.
     *
     * @param point to swap the y axis of
     * @param page height of the page will be used to swap the y axis
     * @return point in coordinates of the other reference coordinate system.
     */
    public static Point2D transformPDFToCoordSys(Point2D point, PDPage page) {
        return Utils.swapYAxis(point, (int) page.getMediaBox().getHeight());
    }

    /**
     * Creates a polygon in the pdf document.
     * The x (& y) coordinates will be used counter clockwise.
     *
     * @param cs content stream where the polygon is appended to
     * @param x x coordinates of the polygon
     * @param y y coordinates of the polygon
     * @return the resulting content stream
     * @throws IOException
     */
    public static PDPageContentStream createPDFPolygon(PDPageContentStream cs, double[] x, double[] y) throws IOException {
            if (x.length != y.length) {
                throw new IllegalArgumentException("Length of x and y coordinate arrays have to be equal!");
            }
            for (int i = 0; i < x.length; i++) {
                if (i == 0) {
                    cs.moveTo((float) x[i], (float) y[i]);
                }
                else {
                    cs.lineTo((float) x[i], (float) y[i]);
                }
            }
            cs.closePath();
            return cs;
    }
}
