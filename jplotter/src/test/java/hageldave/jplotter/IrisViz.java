package hageldave.jplotter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.w3c.dom.Document;

import hageldave.jplotter.canvas.BlankCanvas;
import hageldave.jplotter.canvas.CoordSysCanvas;
import hageldave.jplotter.canvas.FBOCanvas;
import hageldave.jplotter.interaction.CoordSysViewSelector;
import hageldave.jplotter.misc.CharacterAtlas;
import hageldave.jplotter.misc.DefaultGlyph;
import hageldave.jplotter.misc.Glyph;
import hageldave.jplotter.renderables.Legend;
import hageldave.jplotter.renderables.Lines;
import hageldave.jplotter.renderables.Points;
import hageldave.jplotter.renderables.Triangles;
import hageldave.jplotter.renderers.CompleteRenderer;
import hageldave.jplotter.svg.SVGUtils;

public class IrisViz {

	public static void main(String[] args) throws IOException {
		// setup content
		ArrayList<double[]> dataset = new ArrayList<>();
		//URL irissrc = new URL("https://archive.ics.uci.edu/ml/machine-learning-databases/iris/iris.data");
		try (	InputStream stream = IrisViz.class.getResourceAsStream("/iris.data");
				Scanner  sc = new Scanner(stream))
		{
			while(sc.hasNextLine()){
				String nextLine = sc.nextLine();
				if(nextLine.isEmpty()){
					continue;
				}
				String[] fields = nextLine.split(",");
				double[] values = new double[5];
				values[0] = Double.parseDouble(fields[0]);
				values[1] = Double.parseDouble(fields[1]);
				values[2] = Double.parseDouble(fields[2]);
				values[3] = Double.parseDouble(fields[3]);
				if(fields[4].contains("setosa")){
					values[4] = 0; // setosa class
				} else if(fields[4].contains("versicolor")) {
					values[4] = 1; // versicolor class
				} else {
					values[4] = 2; // virginica class
				}
				dataset.add(values);
			}
		}
		
		// done reading data, lets make the viz
		JFrame frame = new JFrame("Iris Dataset");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout());
		JPanel gridPane = new JPanel(new GridLayout(4, 4));
		gridPane.setBackground(Color.WHITE);
		frame.getContentPane().add(gridPane, BorderLayout.CENTER);
		JPanel header = new JPanel();
		header.setBackground(Color.WHITE);
		header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
		frame.getContentPane().add(header, BorderLayout.NORTH);
		
		LinkedList<FBOCanvas> canvasCollection = new LinkedList<>();
		String[] dimNames = new String[]{"sepal length","sepal width","petal length","petal width"};
		String[] perClassNames = new String[]{"Setosa", "Versicolor", "Virginica"};
		int[] perClassColors = new int[]{0xff66c2a5,0xfffc8d62,0xff8da0cb};
		Glyph[] perClassGlyphs = new Glyph[]{DefaultGlyph.CIRCLE_F, DefaultGlyph.SQUARE_F, DefaultGlyph.TRIANGLE_F};
		
		// add legend on top
		BlankCanvas legendCanvas = new BlankCanvas();
		canvasCollection.add(legendCanvas);
		legendCanvas.setPreferredSize(new Dimension(400, 16));
		Legend legend = new Legend();
		for(int c=0; c<3; c++){
			legend.addGlyphLabel(perClassGlyphs[c], new Color(perClassColors[c]), perClassNames[c]);
		}
		legendCanvas.setRenderer(legend);
		header.add(Box.createHorizontalStrut(30));
		header.add(legendCanvas);
		JLabel pointInfo = new JLabel("");
		pointInfo.setFont(new Font(CharacterAtlas.FONT_NAME,Font.PLAIN,10));
		pointInfo.setPreferredSize(new Dimension(300, pointInfo.getPreferredSize().height));
		header.add(pointInfo);
		
		ArrayList<Points[]> allPoints = new ArrayList<>();
		ArrayList<Triangles[]> allTris = new ArrayList<>();
		ArrayList<Lines[]> allLines = new ArrayList<>();
 		
		// make scatter plot matrix
		for(int j = 0; j < 4; j++){
			for(int i = 0; i < 4; i++){
				CoordSysCanvas canvas = new CoordSysCanvas();
				canvasCollection.add(canvas);
				canvas.setPreferredSize(new Dimension(250, 250));
				gridPane.add(canvas);
				canvas.setxAxisLabel(j==0 ? dimNames[i] : "");
				canvas.setyAxisLabel(i==3 ? dimNames[j] : "");
				CompleteRenderer content = new CompleteRenderer();

				double maxX,minX,maxY,minY;
				maxX = maxY = Double.NEGATIVE_INFINITY;
				minX = minY = Double.POSITIVE_INFINITY;
				if(i==j){
					// make histo when same dimension on x and y axis
					int numBuckets = 20;
					double[][] histo = mkHistogram(dataset, i, numBuckets);
					Lines[] lines = new Lines[]{new Lines(), new Lines(), new Lines()};
					allLines.add(lines);
					for(int c = 0; c < 3; c++){
						lines[c].setThickness(1.5f).addLineStrip(perClassColors[c], histo[3], histo[c]);
						content.addItemToRender(lines[c]);
					}
					Triangles[] perClassTris = new Triangles[]{new Triangles(),new Triangles(),new Triangles()};
					allTris.add(perClassTris);
					for(int k = 0; k < numBuckets-1; k++){
						for(int c = 0; c < 3; c++){
							perClassTris[c].addQuad(histo[3][k], 0, histo[3][k], histo[c][k], histo[3][k+1], histo[c][k+1], histo[3][k+1], 0, new Color(perClassColors[c]));
						}
					}
					for(int c = 0; c < 3; c++){
						content.addItemToRender(perClassTris[c].setGlobalAlphaMultiplier(0.1f));
					}
					minX = Arrays.stream(histo[3]).min().getAsDouble();
					maxX = Arrays.stream(histo[3]).max().getAsDouble();
					maxY = Math.max(maxY, Arrays.stream(histo[0]).max().getAsDouble());
					maxY = Math.max(maxY, Arrays.stream(histo[1]).max().getAsDouble());
					maxY = Math.max(maxY, Arrays.stream(histo[2]).max().getAsDouble());
					minY = 0;
				} else {
					// make scatter
					Points[] perClassPoints = new Points[]{
							new Points(perClassGlyphs[0]),
							new Points(perClassGlyphs[1]),
							new Points(perClassGlyphs[2])
					};
					allPoints.add(perClassPoints);
					content
					.addItemToRender(perClassPoints[0].setGlobalAlphaMultiplier(0.6f))
					.addItemToRender(perClassPoints[1].setGlobalAlphaMultiplier(0.6f))
					.addItemToRender(perClassPoints[2].setGlobalAlphaMultiplier(0.6f));
					for(int k = 0; k < dataset.size(); k++){
						double[] instance = dataset.get(k);
						int clazz = (int)instance[4];
						double x =instance[i];
						double y = instance[j];
						perClassPoints[clazz].addPoint(
								x,
								y,
								0, 
								1, 
								perClassColors[clazz], 
								k+1
								);
						maxX = Math.max(maxX, x);
						maxY = Math.max(maxY, y);
						minX = Math.min(minX, x);
						minY = Math.min(minY, y);
					}
					
					// hovering over point
					canvas.addMouseListener(new MouseAdapter() {
						@Override
						public void mouseClicked(MouseEvent e) {
							if(SwingUtilities.isRightMouseButton(e)){
								return;
							}
							Point2D location = canvas.transformAWT2CoordSys(e.getPoint());
							if(!canvas.getCoordinateView().contains(location)){
								pointInfo.setText("");
								recolorAll();
								return;
							}
							int pixel = canvas.getPixel(e.getX(), e.getY(), true, 1);
							if((pixel&0x00ffffff)==0){
								pixel = canvas.getPixel(e.getX(), e.getY(), true, 3);
							}
							if((pixel&0x00ffffff)==0){
								pointInfo.setText("");
								recolorAll();
								return;
							}
							int dataSetinstanceIDX = (pixel & 0x00ffffff)-1;
							double[] instance = dataset.get(dataSetinstanceIDX);
							pointInfo.setForeground(new Color(perClassColors[(int)instance[4]]));
							pointInfo.setText(""
									+ perClassNames[(int)instance[4]] 
									+ "  s.l=" + instance[0]
									+ "  s.w=" + instance[1]
									+ "  p.l=" + instance[2]
									+ "  p.w=" + instance[3]
							);
							desaturateExcept(pixel|0xff000000);
						}
						
						void recolorAll() {
							for(Points[] points:allPoints){
								for(int c=0; c<3; c++){
									int color = perClassColors[c];
									points[c].getPointDetails().forEach(p->{
										p.color = color;
										p.scale = 1;
									});
									points[c].setGlobalAlphaMultiplier(0.6).setDirty();
								}
							}
							for(Triangles[] tris:allTris){
								for(int c=0; c<3; c++){
									int color = perClassColors[c];
									tris[c].setDirty().getTriangleDetails().forEach(t->{
										t.c0=t.c1=t.c2 = color;
									});
								}
							}
							for(Lines[] lines:allLines){
								for(int c=0; c<3; c++){
									int color = perClassColors[c];
									lines[c].setDirty().getSegments().forEach(s->{
										s.color0=s.color1=color;
									});
								}
							}
							canvasCollection.forEach(cnvs->cnvs.repaint());
						}
						
						void desaturateExcept(int pick){
							int clazz = (int)dataset.get((pick&0x00ffffff)-1)[4];
							for(Points[] points:allPoints){
								for(int c=0; c<3; c++){
									int color = perClassColors[c];
									int desat = 0x33aaaaaa;
									points[c].getPointDetails().forEach(p->{
										p.color = p.pickColor==pick ? color:desat;
										p.scale = p.pickColor==pick ? 1.2f:1;
									});
									// bring picked point to front by sorting
									points[c].getPointDetails().sort((p1,p2)->{
										if(p1.pickColor==p2.pickColor) return 0;
										return p1.pickColor==pick ? 1:-1;
									});
									points[c].setGlobalAlphaMultiplier(1).setDirty();
								}
							}
							for(Triangles[] tris:allTris){
								for(int c=0; c<3; c++){
									int color = c==clazz ? perClassColors[c]:0xff777777;
									tris[c].setDirty().getTriangleDetails().forEach(t->{
										t.c0=t.c1=t.c2 = color;
									});
								}
							}
							for(Lines[] lines:allLines){
								for(int c=0; c<3; c++){
									int color = c==clazz ? perClassColors[c]:0xff777777;
									lines[c].setDirty().getSegments().forEach(s->{
										s.color0=s.color1=color;
									});
								}
							}
							canvasCollection.forEach(cnvs->cnvs.repaint());
						}
					});
					// selecting points (brush & link)
					new CoordSysViewSelector(canvas) {
						{extModifierMask=0;/* no shift needed */}
						public void areaSelectedOnGoing(double minX, double minY, double maxX, double maxY) {
							pointInfo.setText("");
							desaturateExcept(minX, minY, maxX, maxY);
						}
						public void areaSelected(double minX, double minY, double maxX, double maxY) {
							pointInfo.setText("");
							desaturateExcept(minX, minY, maxX, maxY);
						}
						void desaturateExcept(double minX, double minY, double maxX, double maxY){
							Rectangle2D r = new Rectangle2D.Double(minX, minY, maxX-minX, maxY-minY);
							Predicate<Point2D> isinselection = r::contains;
							TreeSet<Integer> pickIDs = Arrays.stream(perClassPoints)
									.flatMap(points->points.getPointDetails().stream())
									.filter(p->isinselection.test(p.location))
									.map(p->p.pickColor)
									.collect(Collectors.toCollection(TreeSet::new));
							Set<Integer> clazzes = pickIDs.stream()
									.map(id->(id&0x00ffffff)-1)
									.map(dataset::get)
									.map(inst->(int)inst[4])
									.collect(Collectors.toSet());
							for(Points[] points:allPoints){
								for(int c=0; c<3; c++){
									int color = perClassColors[c];
									int desat = 0x33aaaaaa;
									points[c].getPointDetails().forEach(p->{
										p.color = pickIDs.contains(p.pickColor) ? color:desat;
									});
									// bring picked point to front by sorting
									points[c].getPointDetails().sort((p1,p2)->{
										if(pickIDs.contains(p1.pickColor)==pickIDs.contains(p2.pickColor)) return 0;
										return pickIDs.contains(p1.pickColor) ? 1:-1;
									});
									points[c].setGlobalAlphaMultiplier(1).setDirty();
								}
							}
							for(Triangles[] tris:allTris){
								for(int c=0; c<3; c++){
									int color = clazzes.contains(c) ? perClassColors[c]:0xff777777;
									tris[c].setDirty().getTriangleDetails().forEach(t->{
										t.c0=t.c1=t.c2 = color;
									});
								}
							}
							for(Lines[] lines:allLines){
								for(int c=0; c<3; c++){
									int color = clazzes.contains(c) ? perClassColors[c]:0xff777777;
									lines[c].setDirty().getSegments().forEach(s->{
										s.color0=s.color1=color;
									});
								}
							}
							canvasCollection.forEach(cnvs->cnvs.repaint());
						}
					}.register();
				}
				canvas.setContent(content);
				canvas.setCoordinateView(minX, minY, maxX, maxY);
			}
		}
		
		for(FBOCanvas cnvs:canvasCollection){
			// add a pop up menu (on right click) for exporting to SVG
			PopupMenu menu = new PopupMenu();
			MenuItem svgExport = new MenuItem("SVG export");
			menu.add(svgExport);
			svgExport.addActionListener(e->{
				Document svg = SVGUtils.containerToSVG(frame.getContentPane());
				SVGUtils.documentToXMLFile(svg, new File("iris_export.svg"));
				System.out.println("exported iris_export.svg");
			});
			cnvs.add(menu);
			cnvs.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if(SwingUtilities.isRightMouseButton(e))
						menu.show(cnvs, e.getX(), e.getY());
				}
			});
		}
		
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				canvasCollection.forEach(c->c.runInContext(()->c.close()));
			}
		});
		SwingUtilities.invokeLater(()->{
			frame.pack();
			frame.setVisible(true);
			frame.transferFocus();
		});
	}
	
	static double[][] mkHistogram(ArrayList<double[]> dataset, int dim, int numbuckets){
		double min = dataset.stream().mapToDouble(instance->instance[dim]).min().getAsDouble();
		double max = dataset.stream().mapToDouble(instance->instance[dim]).max().getAsDouble();
		double range = max-min;
		double[] bucketVals = new double[numbuckets];
		for(int i = 0; i < numbuckets; i++){
			bucketVals[i] = i*range/(numbuckets-1) + min;
		}
		double[][] counts = new double[3][numbuckets];
		for(int i = 0; i < dataset.size(); i++){
			double[] instance = dataset.get(i);
				double v = instance[dim];
				int bucket = (int)((v-min)/range*numbuckets);
				counts[(int)instance[4]][bucket<numbuckets ? bucket : numbuckets-1]++;
		}
		return new double[][]{counts[0],counts[1],counts[2],bucketVals};
	}

}
