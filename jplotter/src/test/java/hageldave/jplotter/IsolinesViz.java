package hageldave.jplotter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.joml.Math;

import hageldave.jplotter.interaction.CoordSysPanning;
import hageldave.jplotter.interaction.CoordSysScrollZoom;
import hageldave.jplotter.renderables.Legend;
import hageldave.jplotter.renderables.Lines;
import hageldave.jplotter.renderables.Lines.SegmentDetails;
import hageldave.jplotter.renderables.Text;
import hageldave.jplotter.renderables.Triangles;
import hageldave.jplotter.renderables.Triangles.TriangleDetails;
import hageldave.jplotter.renderers.CompleteRenderer;
import hageldave.jplotter.util.Contours;
import hageldave.jplotter.util.Utils;

public class IsolinesViz {

	public static void main(String[] args) {
		JFrame frame = new JFrame("Iso Lines");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout());
		frame.getContentPane().setPreferredSize(new Dimension(350, 300));
		CoordSysCanvas canvas = new CoordSysCanvas();
		CompleteRenderer content = new CompleteRenderer();
		canvas.setContent(content);
		Legend legend = new Legend();
		canvas.setLegendRight(legend);
		canvas.setLegendRightWidth(50);
		canvas.setBackground(Color.WHITE);

		// setup content
		DoubleBinaryOperator f1 = (x,y)->Math.exp(-(x*x+y*y));
		DoubleBinaryOperator f2 = (x,y)->(x*y)-(y+1)*y;
		DoubleBinaryOperator f = (x,y)->f1.applyAsDouble(x, y)-f2.applyAsDouble(x, y);
		final int resolution = 200;
		double[][] X = new double[resolution][resolution];
		double[][] Y = new double[resolution][resolution];
		double[][] Z = new double[resolution][resolution];
		for(int j=0; j<X.length;j++) {
			for(int i=0; i<X[0].length; i++) {
				double x = i*8.0/(resolution-1) -4.0;
				double y = j*8.0/(resolution-1) -4.0;
				double z = f.applyAsDouble(x, y);
				X[j][i] = x;
				Y[j][i] = y;
				Z[j][i] = z;
			}
		}
		// make contour plot
		Lines contourlines = new Lines();
		Triangles contourbands = new Triangles();
		double[] isoValues = new double[] {
			-2,
			-1,
			-.5,
			0,
			.5,
			1,
			2,
		};
		int[] isoColors = new int[] {
				0xff000000,
				0xff330000,
				0xff660000,
				0xff993322,
				0xffcc6644,
				0xffff9966,
				0xffffcc88,
		};
		for(int i = isoValues.length-1; i >= 0; i--) {
			List<SegmentDetails> contours = Contours.computeContourLines(X, Y, Z, isoValues[i], isoColors[i]);
			contourlines.getSegments().addAll(contours);
			legend.addLineLabel(1, new Color(isoColors[i]), isoValues[i] < 0 ? ""+isoValues[i]:" "+isoValues[i]);
		}
		for(int i = 0; i < isoValues.length-1; i++) {
			List<TriangleDetails> contours = Contours.computeContourBands(X, Y, Z, isoValues[i], isoValues[i+1], isoColors[i], isoColors[i+1]);
			contourbands.getTriangleDetails().addAll(contours);
		}
		content.addItemToRender(contourlines).addItemToRender(contourbands);
		contourlines.setThickness(1);
		contourbands.setGlobalAlphaMultiplier(0.3);
		new CoordSysScrollZoom(canvas).register();
		new CoordSysPanning(canvas).register();
		Rectangle2D bounds = contourbands.getBounds();
		canvas.setCoordinateView(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
		
		Lines userContour = new Lines();
		Text userIsoLabel = new Text("", 10, Font.ITALIC, true);
		Triangles labelBackground = new Triangles().setGlobalAlphaMultiplier(0.5);
		CompleteRenderer overlay = new CompleteRenderer();
		canvas.setContent(content);
		canvas.setOverlay(overlay);
		content.addItemToRender(userContour);
		overlay.addItemToRender(userIsoLabel);
		overlay.addItemToRender(labelBackground);
		MouseAdapter contourPlacer = new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				if(e.getModifiersEx() == MouseEvent.BUTTON1_DOWN_MASK)
					calcContour(e.getPoint());
			};
			
			public void mouseDragged(MouseEvent e) {
				if(e.getModifiersEx() == MouseEvent.BUTTON1_DOWN_MASK)
					calcContour(e.getPoint());
			};
			
			void calcContour(Point mp){
				Point2D p = Utils.swapYAxis(mp,canvas.getHeight());
				Point2D coordsyspoint = canvas.transformToCoordSys(p); 
				double isoValue = f.applyAsDouble(coordsyspoint.getX(), coordsyspoint.getY());
				userIsoLabel
					.setTextString(String.format("%.3f", isoValue))
					.setColor(0xff8844bb)
					.setOrigin(p);
					
				labelBackground.removeAllTriangles().addQuad(userIsoLabel.getBounds(),Color.white,0);
				List<SegmentDetails> contourSegments = Contours.computeContourLines(X, Y, Z, isoValue, 0xff8844bb);
				userContour.removeAllSegments().getSegments().addAll(contourSegments);
				canvas.repaint();
			}
		};
		canvas.addMouseListener(contourPlacer);
		canvas.addMouseMotionListener(contourPlacer);

		frame.getContentPane().add(canvas, BorderLayout.CENTER);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				canvas.runInContext(()->canvas.close());
			}
		});
		SwingUtilities.invokeLater(()->{
			frame.pack();
			frame.setVisible(true);
			frame.transferFocus();
		});
	}

}
