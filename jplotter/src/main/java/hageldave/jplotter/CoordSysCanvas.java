package hageldave.jplotter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.LinkedList;
import java.util.Objects;

import org.joml.Matrix3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.awt.GLData;

import hageldave.jplotter.Annotations.GLContextRequired;
import hageldave.jplotter.interaction.CoordSysPanning;
import hageldave.jplotter.interaction.CoordSysScrollZoom;
import hageldave.jplotter.renderables.CharacterAtlas;
import hageldave.jplotter.renderables.Legend;
import hageldave.jplotter.renderables.Lines;
import hageldave.jplotter.renderables.Text;
import hageldave.jplotter.renderers.AdaptableView;
import hageldave.jplotter.renderers.LinesRenderer;
import hageldave.jplotter.renderers.Renderer;
import hageldave.jplotter.renderers.TextRenderer;
import hageldave.jplotter.util.ExtendedWilkinson;
import hageldave.jplotter.util.Pair;
import hageldave.jplotter.util.PointeredPoint2D;
import hageldave.jplotter.util.TickMarkGenerator;
import hageldave.jplotter.util.TranslatedPoint2D;
import hageldave.jplotter.util.Utils;

/**
 * The CoordSysCanvas is an {@link FBOCanvas} that displays a coordinate system.
 * This coordinate system is enclosed by 4 axes that form a rectangle around the
 * area that displays the contents of the coordinate system.
 * <p>
 * The upper x-axis and right y-axis feature the labels (names) of the axes.
 * The lower x-axis and the left y-axis feature tick marks and labels that
 * help to orientate and read off coordinates.
 * The positioning and labeling of the tick marks is done by a {@link TickMarkGenerator}
 * which is per default an instance of {@link ExtendedWilkinson}.
 * For each tick a vertical or horizontal guide line is drawn across the area of the
 * coordinate system.
 * <p>
 * What coordinate range the coordinate system area corresponds to is controlled by
 * the coordinate view (see {@link #setCoordinateView(double, double, double, double)})
 * and defaults to [-1,1] for both axes.
 * The contents that are drawn inside the coordinate area are rendered by the content renderer
 * (see {@link #setContent(Renderer)}).
 * If that renderer implements the {@link AdaptableView} interface it will be passed the
 * view matrix corresponding to the coordinate view.
 * The content renderer will be able to draw within the viewport defined by the coordinate
 * system area of this Canvas.
 * <p>
 * Optionally a {@link Renderer} for drawing a legend (such as the {@link Legend} class)
 * can be set to either the bottom or right hand side of the coordinate system (can also
 * use both areas at once).
 * Use {@link #setLegendBottom(Renderer)} or {@link #setLegendRight(Renderer)} to do so.
 * The legend area size can be partially controlled by {@link #setLegendBottomHeight(int)}
 * and {@link #setLegendRightWidth(int)} if this is needed.
 * <p>
 * For interacting with this CoordSysCanvas there already exist implementations of MouseListeners
 * for panning and zooming (see {@link CoordSysPanning} and {@link CoordSysScrollZoom}).
 * 
 * @author hageldave
 */
public class CoordSysCanvas extends FBOCanvas {
	private static final long serialVersionUID = 1L;

	protected LinesRenderer preContentLinesR = new LinesRenderer();
	protected TextRenderer preContentTextR = new TextRenderer();
	protected LinesRenderer postContentLinesR = new LinesRenderer();
	protected TextRenderer postContentTextR = new TextRenderer();
	protected Renderer overlay;
	protected Renderer content=null;
	protected Renderer legendRight=null;
	protected Renderer legendBottom=null;
	
	protected int legendRightWidth = 70;
	protected int legendBottomHeight = 20;
	
	protected Rectangle legendRightViewPort = new Rectangle();
	protected Rectangle legendBottomViewPort = new Rectangle();

	protected Rectangle2D coordinateView = new Rectangle2D.Double(-1,-1,2,2);

	protected TickMarkGenerator tickMarkGenerator = new ExtendedWilkinson();

	protected Lines axes = new Lines();
	protected Lines ticks = new Lines();
	protected Lines guides = new Lines();
	protected LinkedList<Text> tickMarkLabels = new LinkedList<>();
	protected Text xAxisLabelText = new Text("", 12, Font.PLAIN, true);
	protected Text yAxisLabelText = new Text("", 12, Font.PLAIN, true);

	protected double[] xticks;
	protected double[] yticks;

	protected int viewportwidth=0;
	protected int viewportheight=0;
	protected boolean isDirty = true;

	protected Color tickColor = Color.DARK_GRAY;
	protected Color guideColor = new Color(0xdddddd);

	protected int leftPadding = 10;
	protected int rightPadding = 10;
	protected int topPadding = 10;
	protected int botPadding = 10;

	protected PointeredPoint2D coordsysAreaLB = new PointeredPoint2D();
	protected PointeredPoint2D coordsysAreaRT = Utils.copy(coordsysAreaLB);
	protected PointeredPoint2D coordsysAreaLT = new PointeredPoint2D(coordsysAreaLB.x, coordsysAreaRT.y);
	protected PointeredPoint2D coordsysAreaRB = new PointeredPoint2D(coordsysAreaRT.x, coordsysAreaLB.y);

	protected Matrix3f coordSysScaleMX = new Matrix3f();
	protected Matrix3f coordSysTransMX = new Matrix3f();
	/** 
	 * The transform that corresponds to this coordinate systems {@link #coordinateView}.
	 * coordSysViewMX = {@link #coordSysScaleMX} * {@link #coordSysTransMX}
	 * which is passed to the content renderer.
	 */
	protected Matrix3f coordSysViewMX = new Matrix3f();

	protected String xAxisLabel = null;
	protected String yAxisLabel = null;


	protected CoordSysCanvas(GLData data) {
		super(data);
		this.axes.addSegment(coordsysAreaLB, coordsysAreaRB, Color.BLACK);
		this.axes.addSegment(coordsysAreaLB, coordsysAreaLT, Color.BLACK);
		this.axes.addSegment(coordsysAreaLT, coordsysAreaRT, Color.GRAY);
		this.axes.addSegment(coordsysAreaRB, coordsysAreaRT, Color.GRAY);
		this.axes.setThickness(2);
		this.preContentLinesR
		.addItemToRender(guides)
		.addItemToRender(ticks);
		this.preContentTextR
		.addItemToRender(xAxisLabelText)
		.addItemToRender(yAxisLabelText);
		this.postContentLinesR.addItemToRender(axes);
	}

	public CoordSysCanvas() {
		this(new GLData());
	}

	/**
	 * Sets the {@link #isDirty} state of this CoordSysCanvas to true.
	 * This indicates that axis locations, tick marks, labels and guides
	 * have to be recomputed.
	 * This will be done during {@link #paintToFBO(int, int)} which
	 * will set the isDirty state back to false.
	 */
	public void setDirty() {
		this.isDirty = true;
	}

	/**
	 * Sets the content renderer that will draw into the area of the coordinate system.
	 * @param content the content renderer
	 * @return the previous content renderer (which may need to be closed to free GL resources),
	 * null if none was set
	 */
	public Renderer setContent(Renderer content) {
		Renderer old = this.content;
		this.content = content;
		return old;
	}
	
	/**
	 * Sets the renderer that will draw the legend to the right of the coordinate system.
	 * The view port area for this renderer will start at the top edge of the coordinate system,
	 * be {@link #getLegendRightWidth()} wide and extend to the bottom of the canvas (-padding).
	 * @param legend renderer for right side of coordinate system
	 * @return the previous legend renderer (which may need to be closed to free GL resources),
	 * null if none was set
	 */
	public Renderer setLegendRight(Renderer legend) {
		Renderer old = this.legendRight;
		this.legendRight= legend;
		return old;
	}
	
	/**
	 * Sets the renderer that will draw the legend below the coordinate system.
	 * The view port area for this renderer will start at the left edge of the coordinate system below
	 * the axis tick labels, will be as wide as the x-axis and {@link #getLegendBottomHeight()} high.
	 * @param legend renderer for bottom side of coordinate system
	 * @return the previous legend renderer (which may need to be closed to free GL resources),
	 * null if none was set
	 */
	public Renderer setLegendBottom(Renderer legend) {
		Renderer old = this.legendBottom;
		this.legendBottom = legend;
		return old;
	}
	
	/**
	 * Sets the overlay renderer that will draw at last in the sequence and thus
	 * overlays the whole rendering.
	 * @param overlayRenderer
	 * @return
	 */
	public Renderer setOverlay(Renderer overlayRenderer) {
		Renderer old = overlayRenderer;
		this.overlay = overlayRenderer;
		return old;
	}
	
	/**
	 * Sets the height of the legend area below the coordinate system.
	 * (width is determined by x-axis width)
	 * @param legendBottomHeight height of the bottom legend area.
	 * (default is 20px)
	 */
	public void setLegendBottomHeight(int legendBottomHeight) {
		this.legendBottomHeight = legendBottomHeight;
	}
	
	/**
	 * Sets the width of the legend area right to the coordinate system.
	 * (height is determined by the space available until the bottom of the canvas)
	 * @param legendRightWidth width of the right legend area.
	 * (default is 70 px)
	 */
	public void setLegendRightWidth(int legendRightWidth) {
		this.legendRightWidth = legendRightWidth;
	}
	
	/**
	 * @return width of the width of the right hand side legend area.
	 */
	public int getLegendRightWidth() {
		return legendRightWidth;
	}
	
	/**
	 * @return height of the bottom side legend area.
	 */
	public int getLegendBottomHeight() {
		return legendBottomHeight;
	}

	/**
	 * Sets up pretty much everything.
	 * <ul>
	 * <li>the bounds of the coordinate system frame ({@link #coordsysAreaLB}, {@link #coordsysAreaRT})</li>
	 * <li>the tick mark values and labels</li>
	 * <li>the tick mark guides</li>
	 * <li>the location of the axis labels</li>
	 * </ul>
	 */
	@GLContextRequired
	protected void setupCoordsysTicksGuidesAndLabels() {
		Pair<double[],String[]> xticksAndLabels = tickMarkGenerator.genTicksAndLabels(
				coordinateView.getMinX(), 
				coordinateView.getMaxX(), 
				5, 
				false);
		Pair<double[],String[]> yticksAndLabels = tickMarkGenerator.genTicksAndLabels(
				coordinateView.getMinY(), 
				coordinateView.getMaxY(), 
				5, 
				true);
		this.xticks = xticksAndLabels.first;
		this.yticks = yticksAndLabels.first;
		String[] xticklabels = xticksAndLabels.second;
		String[] yticklabels = yticksAndLabels.second;

		final int tickfontSize = 10;
		final int labelfontSize = 12;
		final int style = Font.PLAIN;
		final boolean antialiased = true;
		// find maximum length of y axis labels
		int maxYTickLabelWidth = 0;
		for(String label:yticklabels){
			int labelW = CharacterAtlas.boundsForText(label.length(), tickfontSize, style, antialiased).getBounds().width;
			maxYTickLabelWidth = Math.max(maxYTickLabelWidth, labelW);
		}
		int maxXTickLabelHeight = CharacterAtlas.boundsForText(1, tickfontSize, style, antialiased).getBounds().height;
		int maxLabelHeight = CharacterAtlas.boundsForText(1, labelfontSize, style, antialiased).getBounds().height;
		
		int legendRightW = Objects.nonNull(legendRight) ? legendRightWidth+4:0;
		int legendBotH = Objects.nonNull(legendBottom) ? legendBottomHeight+4:0;
		
		// move coordwindow origin so that labels have enough display space
		coordsysAreaLB.x[0] = maxYTickLabelWidth + leftPadding + 7;
		coordsysAreaLB.y[0] = maxXTickLabelHeight + botPadding + legendBotH + 6;
		// move opposing corner of coordwindow to have enough display space
		coordsysAreaRT.x[0] = getWidth()-rightPadding-maxLabelHeight-legendRightW-4;
		coordsysAreaRT.y[0] = getHeight()-topPadding-maxLabelHeight-4;

		// dispose of old stuff
		ticks.removeAllSegments();
		guides.removeAllSegments();
		for(Text txt:tickMarkLabels){
			preContentTextR.removeItemToRender(txt);
			txt.close();
		}
		tickMarkLabels.clear();

		// create new stuff
		double xAxisWidth = coordsysAreaLB.distance(coordsysAreaRB);
		double yAxisHeight = coordsysAreaLB.distance(coordsysAreaLT);
		// xaxis ticks
		for(int i=0; i<xticks.length; i++){
			// tick
			double m = (xticks[i]-coordinateView.getMinX())/coordinateView.getWidth();
			double x = coordsysAreaLB.getX()+m*xAxisWidth;
			Point2D onaxis = new Point2D.Double(x,coordsysAreaLB.getY());
			ticks.addSegment(onaxis, new TranslatedPoint2D(onaxis, 0,-4), tickColor);
			// label
			Text label = new Text(xticklabels[i], tickfontSize, style, antialiased);
			Dimension textSize = label.getTextSize();
			label.setOrigin(
					(int)(onaxis.getX()-textSize.getWidth()/2.0), 
					(int)(onaxis.getY()-6-textSize.getHeight()));
			tickMarkLabels.add(label);
			// guide
			guides.addSegment(onaxis, new TranslatedPoint2D(onaxis, 0, yAxisHeight), guideColor);
		}
		// yaxis ticks
		for(int i=0; i<yticks.length; i++){
			// tick
			double m = (yticks[i]-coordinateView.getMinY())/coordinateView.getHeight();
			Point2D onaxis = new TranslatedPoint2D(coordsysAreaLB, 0, m*yAxisHeight);
			ticks.addSegment(onaxis, new TranslatedPoint2D(onaxis, -4, 0), tickColor);
			// label
			Text label = new Text(yticklabels[i], tickfontSize, style, antialiased);
			Dimension textSize = label.getTextSize();
			label.setOrigin(new TranslatedPoint2D(onaxis, -7-textSize.getWidth(), -textSize.getHeight()/2.0));
			tickMarkLabels.add(label);
			// guide
			guides.addSegment(onaxis, new TranslatedPoint2D(coordsysAreaRB, 0, m*yAxisHeight), guideColor);
		}
		for(Text txt: tickMarkLabels){
			preContentTextR.addItemToRender(txt);
		}
		// axis labels
		xAxisLabelText.setTextString(getxAxisLabel());
		xAxisLabelText.setOrigin(new TranslatedPoint2D(coordsysAreaLT, xAxisWidth/2 - xAxisLabelText.getTextSize().width/2, 4));
		yAxisLabelText.setTextString(getyAxisLabel());
		yAxisLabelText.setAngle(-(float)Math.PI/2);
		yAxisLabelText.setOrigin(new TranslatedPoint2D(coordsysAreaRB, 4, yAxisHeight/2 + yAxisLabelText.getTextSize().width/2));
		
		// setup legend areas
		if(Objects.nonNull(legendRight)){
			legendRightViewPort.setBounds(
					(int)(yAxisLabelText.getOrigin().getX()+yAxisLabelText.getTextSize().getHeight()+4), 
					botPadding, 
					legendRightWidth, 
					(int)(coordsysAreaRT.getY()-botPadding)
			);
		} else {
			legendRightViewPort.setBounds(0, 0, 0, 0);
		}
		if(Objects.nonNull(legendBottom)){
			legendBottomViewPort.setBounds(
					(int)coordsysAreaLB.getX(),
					botPadding,
					(int)(coordsysAreaLB.distance(coordsysAreaRB)),
					legendBottomHeight
			);
		} else {
			legendBottomViewPort.setBounds(0, 0, 0, 0);
		}
	}

	/**
	 * @return "X" if {@link #xAxisLabel} is null or the actual axis label.
	 */
	public String getxAxisLabel() {
		return xAxisLabel == null ? "X":xAxisLabel;
	}

	/**
	 * @return "Y" if {@link #yAxisLabel} is null or the actual axis label.
	 */
	public String getyAxisLabel() {
		return yAxisLabel == null ? "Y":yAxisLabel;
	}

	/**
	 * Sets the specified string as the x axis label which appears on top of the coordinate system.
	 * Sets the {@link #isDirty} state of this {@link CoordSysCanvas} to true.
	 * @param xAxisLabel to set
	 */
	public void setxAxisLabel(String xAxisLabel) {
		this.xAxisLabel = xAxisLabel;
		setDirty();
	}

	/**
	 * Sets the specified string as the y axis label which appears to the right of the coordinate system.
	 * Sets the {@link #isDirty} state of this {@link CoordSysCanvas} to true.
	 * @param yAxisLabel to set
	 */
	public void setyAxisLabel(String yAxisLabel) {
		this.yAxisLabel = yAxisLabel;
		setDirty();
	}

	/**
	 * @return the current {@link TickMarkGenerator} which is {@link ExtendedWilkinson} by default.
	 */
	public TickMarkGenerator getTickMarkGenerator() {
		return tickMarkGenerator;
	}

	/**
	 * Sets the specified {@link TickMarkGenerator} for this {@link CoordSysCanvas}.
	 * Sets the {@link #isDirty} state of this {@link CoordSysCanvas} to true.
	 * @param tickMarkGenerator
	 */
	public void setTickMarkGenerator(TickMarkGenerator tickMarkGenerator) {
		this.tickMarkGenerator = tickMarkGenerator;
		setDirty();
	}

	/**
	 * Allocates GL resources, i.e. initializes its renderers.
	 */
	@Override
	public void initGL() {
		super.initGL();
		preContentLinesR.glInit();
		preContentTextR.glInit();
		postContentLinesR.glInit();
		postContentTextR.glInit();
		if(content != null)
			content.glInit();
		if(legendRight != null)
			legendRight.glInit();
		if(legendBottom != null)
			legendBottom.glInit();
	}

	/**
	 * Draws the coordinate system with its guides and labels
	 * and draws the contents into the coordinate system area.
	 */
	@Override
	public void paintToFBO(int w, int h) {
		if(isDirty || viewportwidth != w || viewportheight != h){
			// update axes
			axes.setDirty();
			setupCoordsysTicksGuidesAndLabels();
			viewportwidth = w;
			viewportheight = h;
			isDirty = false;
		}
		preContentLinesR.render(w, h);
		preContentTextR.render(w, h);
		// draw into the coord system
		if(content != null){
			content.glInit();
			int viewPortX = (int)coordsysAreaLB.getX();
			int viewPortY = (int)coordsysAreaLB.getY();
			int viewPortW = (int)coordsysAreaLB.distance(coordsysAreaRB);
			int viewPortH = (int)coordsysAreaLB.distance(coordsysAreaLT);
			GL11.glViewport(viewPortX,viewPortY,viewPortW,viewPortH);
			if(content instanceof AdaptableView){
				double scaleX = viewPortW/coordinateView.getWidth();
				double scaleY = viewPortH/coordinateView.getHeight();
				coordSysScaleMX.set((float)scaleX,0,0,  0,(float)scaleY,0,  0,0,1);
				coordSysTransMX.set(1,0,0,  0,1,0, -(float)coordinateView.getX(),-(float)coordinateView.getY(),1);
				((AdaptableView) content).setViewMX(coordSysScaleMX.mul(coordSysTransMX,coordSysViewMX), coordSysScaleMX, coordSysTransMX);
			}
			content.render(viewPortW, viewPortH);
			GL11.glViewport(0, 0, w, h);
		}
		postContentLinesR.render(w, h);
		postContentTextR.render(w, h);
		
		// draw legends
		if(Objects.nonNull(legendRight)){
			legendRight.glInit();;
			GL11.glViewport(legendRightViewPort.x, legendRightViewPort.y, legendRightViewPort.width, legendRightViewPort.height);
			legendRight.render(legendRightViewPort.width, legendRightViewPort.height);
			GL11.glViewport(0, 0, w, h);
		}
		if(Objects.nonNull(legendBottom)){
			legendBottom.glInit();;
			GL11.glViewport(legendBottomViewPort.x, legendBottomViewPort.y, legendBottomViewPort.width, legendBottomViewPort.height);
			legendBottom.render(legendBottomViewPort.width, legendBottomViewPort.height);
			GL11.glViewport(0, 0, w, h);
		}
		
		// draw overlay
		if(Objects.nonNull(overlay)){
			Rectangle overlayViewPort = this.getOverlayArea();
			int vpx,vpy,vpw,vph;
			if(Objects.nonNull(overlayViewPort)){
				vpx=0;
				vpy=0;
				vpw=w;
				vph=h;
			} else {
				vpx=overlayViewPort.x; 
				vpy=overlayViewPort.y; 
				vpw=overlayViewPort.width; 
				vph=overlayViewPort.height;
			}
			GL11.glViewport(vpx, vpy, vpw, vph);
			overlay.render(vpw, vph);
		}
	}

	/**
	 * Sets the coordinate view. This is the range of x and y coordinates that is displayed by this
	 * {@link CoordSysCanvas}. It is not the rectangular area in which the content appears on screen
	 * but what coordinates that area corresponds to as a coordinate system.
	 * <p>
	 * This determines what range of coordinates is visible when rendering the {@link #content}.
	 * By default the coordinate view covers the range [-1,1] for both x and y coordinates.
	 * When the resulting value ranges maxX-minX or maxY-minY fall below 1e-9, the method
	 * refuses to set the view accordingly to prevent unrecoverable zooming and inaccurate
	 * or broken renderings due to floating point precision.
	 * <p>
	 * This method also sets the {@link #isDirty} state of this {@link CoordSysCanvas} to true.
	 * 
	 * @param minX minimum x coordinate visible in the coordinate system
	 * @param minY minimum y coordinate visible in the coordinate system
	 * @param maxX maximum x coordinate visible in the coordinate system
	 * @param maxY maximum y coordinate visible in the coordinate system
	 */
	public void setCoordinateView(double minX, double minY, double maxX, double maxY){
		if(maxX-minX < 1e-9 || maxY-minY < 1e-9){
			System.err.printf("hitting coordinate area precision limit, x-range:%e, y-range:%e%n", maxX-minX, maxY-minY);
			return;
		}
		this.coordinateView = new Rectangle2D.Double(minX, minY, maxX-minX, maxY-minY);
		setDirty();
	}

	/**
	 * Returns the coordinate view, which is the range of x and y coordinates visible in the
	 * coordinate system.
	 * See {@link #setCoordinateView(double, double, double, double)}.
	 * @return the coordinate view
	 */
	public Rectangle2D getCoordinateView() {
		return coordinateView;
	}

	/**
	 * @return the area of this canvas in which the coordinate system contents are rendered.
	 * It is the viewPort for the {@link #content} renderer which is enclosed by
	 * the coordinate system axes.
	 */
	public Rectangle2D getCoordSysArea() {
		return new Rectangle2D.Double(
				coordsysAreaLB.getX(), 
				coordsysAreaLB.getY(), 
				coordsysAreaLB.distance(coordsysAreaRB), 
				coordsysAreaLB.distance(coordsysAreaLT)
				);
	}


	/**
	 * Returns the area for the {@link #overlay} to render in.
	 * By default returns null, which results in the whole canvas as area,
	 * can be overridden to fit your needs.
	 * @return null by default
	 */
	protected Rectangle getOverlayArea() {
		return null;
	}

	/**
	 * Transforms a mouse location on this Canvas to the corresponding
	 * coordinates in the coordinate system view.
	 * @param mousePoint to be transformed
	 * @return transformed location
	 */
	public Point2D transformMouseToCoordSys(Point mousePoint){
		Rectangle2D coordSysArea = getCoordSysArea();
		double x = mousePoint.getX()-coordSysArea.getMinX();
		double y = mousePoint.getY()-coordSysArea.getMinY();
		x /= coordSysArea.getWidth()-1;
		y /= coordSysArea.getHeight()-1;
		y = 1-y;
		Rectangle2D coordinateView = getCoordinateView();
		x = x*coordinateView.getWidth()+coordinateView.getMinX();
		y = y*coordinateView.getHeight()+coordinateView.getMinY();
		return new Point2D.Double(x, y);
	}

	/**
	 * Disposes of GL resources, i.e. closes its renderers and all resources 
	 * of the FBOCanvas that it is (see {@link FBOCanvas#close()}).
	 */
	@Override
	public void close() {
		if(Objects.nonNull(preContentTextR))
			preContentTextR.close();
		if(Objects.nonNull(preContentLinesR))
			preContentLinesR.close();
		if(Objects.nonNull(preContentTextR))
			postContentTextR.close();
		if(Objects.nonNull(preContentLinesR))
			postContentLinesR.close();
		if(Objects.nonNull(content))
			content.close();
		if(Objects.nonNull(legendRight))
			legendRight.close();
		if(Objects.nonNull(legendBottom))
			legendBottom.close();
		preContentTextR = null;
		preContentLinesR = null;
		postContentTextR = null;
		postContentLinesR = null;
		content = null;
		legendRight = null;
		legendBottom = null;
		super.close();
	}

}
