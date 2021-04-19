package hageldave.jplotter.charts;

import hageldave.jplotter.canvas.BlankCanvas;
import hageldave.jplotter.canvas.BlankCanvasFallback;
import hageldave.jplotter.canvas.JPlotterCanvas;
import hageldave.jplotter.interaction.CoordSysMouseOver;
import hageldave.jplotter.interaction.CoordSysPanning;
import hageldave.jplotter.interaction.CoordSysScrollZoom;
import hageldave.jplotter.interaction.CoordSysViewSelector;
import hageldave.jplotter.misc.DefaultGlyph;
import hageldave.jplotter.renderables.Points;
import hageldave.jplotter.renderers.CoordSysRenderer;
import hageldave.jplotter.renderers.PointsRenderer;
import hageldave.jplotter.util.PickingRegistry;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * The ScatterPlot class provides an easy way to quickly create a ScatterPlot.
 * It includes a JPlotterCanvas, CoordSysRenderer and a PointsRenderer,
 * which are all set up automatically.
 * To edit those, they can be returned with their respective getter methods.
 * When data points are added to the ScatterPlot, they are stored in the pointMap in a {@link Dataset}.
 * <p>
 * To add a Dataset to the pointMap, an ID has to be defined as a key.
 * With this ID the Dataset can be removed later on.
 * <p>
 * In each {@link Dataset} there are the coordinates of the points,
 * the selected glyph & color and the {@link Points} stored.
 *
 * @author lucareichmann
 */
public class ScatterPlot {
    protected JPlotterCanvas canvas;
    protected CoordSysRenderer coordsys;
    protected PointsRenderer content;
    final protected HashMap<Integer, Dataset> pointMap;

    // TODO might be merged with hashmap
    final protected LinkedList<double[][]> allPoints;
    final protected PickingRegistry<Points.PointDetails> registry = new PickingRegistry<Points.PointDetails>();


    /**
     * A Dataset stores the point coordinates, the glyph & color selected by the user and
     * the {@link Points} created with the point coordinates.
     */
    private class Dataset {
        protected double[][] pointsCoordinates;
        protected DefaultGlyph glyph;
        protected Color color;
        protected Points points;

        protected Dataset (final double[][] pointsCoordinates, final DefaultGlyph glyph, final Color color, final Points points) {
            this.pointsCoordinates = pointsCoordinates;
            this.glyph = glyph;
            this.color = color;
            this.points = points;
        }

        public double[][] getPointsCoordinates () {
            return pointsCoordinates;
        }

        public DefaultGlyph getGlyph () {
            return glyph;
        }

        public Color getColor () {
            return color;
        }

        public Points getPoints () {
            return points;
        }
    }

    public ScatterPlot (final boolean useOpenGL) {
        this.allPoints = new LinkedList<double[][]>();
        this.pointMap = new HashMap<Integer, Dataset>();
        this.canvas = useOpenGL ? new BlankCanvas() : new BlankCanvasFallback();
        setupScatterPlot();
    }

    public ScatterPlot (final boolean useOpenGL, final JPlotterCanvas canvas) {
        this.allPoints = new LinkedList<double[][]>();
        this.pointMap = new HashMap<Integer, Dataset>();
        this.canvas = canvas;
        setupScatterPlot();
    }

    /**
     * Helper method to set the initial scatter plot.
     */
    protected void setupScatterPlot () {
        this.canvas.asComponent().setPreferredSize(new Dimension(400, 400));
        this.canvas.asComponent().setBackground(Color.WHITE);
        this.coordsys = new CoordSysRenderer();
        this.content = new PointsRenderer();
        this.coordsys.setCoordinateView(-1, -1, 1, 1);
        this.coordsys.setContent(content);
        this.canvas.setRenderer(coordsys);
    }

    /**
     * adds a set of points to the scatter plot.
     *
     * @param ID     the ID is the key with which the Dataset will be stored in the pointMap
     * @param points a double array containing the coordinates of the points TODO Spezifikation hier verbessern zu x,y
     * @param glyph  the data points will be visualized by that glyph
     * @param color  the color of the glyph
     * @return the old Scatterplot for chaining
     */
    public ScatterPlot addPoints (final int ID, final double[][] points, final DefaultGlyph glyph, final Color color) {
        ScatterPlot old = this;
        Points tempPoints = new Points(glyph);
        for (double[] entry : points) {
            double x = entry[0], y = entry[1];
            Points.PointDetails point = tempPoints.addPoint(x, y);
            point.setColor(color);
            addItemToRegistry(point);
        }
        // TODO dataset zurückgeben, statt ScatterPlot -> dann kann da Farbe manipuliert werden
        Dataset newSet = new Dataset(points, glyph, color, tempPoints);
        this.pointMap.put(ID, newSet);
        this.allPoints.add(points);
        this.content.addItemToRender(tempPoints);
        return old;
    }

    /**
     * Removes a {@link Dataset} from the pointMap.
     *
     * @param ID the Dataset with this ID will be removed from the pointMap
     * @return the old Scatterplot for chaining
     */
    public ScatterPlot removePoints (final int ID) {
        ScatterPlot old = this;
        this.pointMap.remove(ID);
        return old;
    }

    /**
     * Adds a scroll zoom to the Scatterplot
     *
     * @return the {@link CoordSysScrollZoom} so that it can be further customized
     */
    public CoordSysScrollZoom addScrollZoom () {
        return new CoordSysScrollZoom(this.canvas, this.coordsys).register();
    }

    /**
     * Adds panning functionality to the Scatterplot
     *
     * @return the {@link CoordSysPanning} so that it can be further customized
     */
    public CoordSysPanning addPanning () {
        return new CoordSysPanning(this.canvas, this.coordsys).register();
    }

    /**
     * Adds a zoom functionality by selecting a rectangle.
     *
     * @return the {@link CoordSysViewSelector} so that it can be further customized
     */
    public CoordSysViewSelector addZoomViewSelector () {
        return new CoordSysViewSelector(this.canvas, this.coordsys) {
            // TODO überlegen ob/was sinnvoll -> Konflikte mit anderen Interaktionen?!
            { extModifierMask = 0;/* no need for shift to be pressed */ }
            @Override
            public void areaSelected (double minX, double minY, double maxX, double maxY) {
                coordsys.setCoordinateView(minX, minY, maxX, maxY);
            }
        }.register();
    }

    public CoordSysMouseOver addMouseOver () {
        return new CoordSysMouseOver(this.canvas, this.coordsys, this.allPoints, this.registry) {
            @Override
            public void mouseOverPoint (Point mouseLocation, Point2D pointLocation, double[][] data, int dataIndex) {
                System.out.println("Mouse location: " + mouseLocation);
                System.out.println("Point location: " + pointLocation);
                System.out.println("Data array: " + Arrays.deepToString(data));
                System.out.println("Data index: " + dataIndex);
            }
        }.register();
    }

    public JPlotterCanvas getCanvas () {
        return canvas;
    }

    public CoordSysRenderer getCoordsys () {
        return coordsys;
    }

    public PointsRenderer getContent () {
        return content;
    }

    public HashMap<Integer, Dataset> getPointMap () {
        return pointMap;
    }

    /**
     * TODO
     * @param
     */
    protected void addItemToRegistry(Points.PointDetails item) {
                int tempID = this.registry.getNewID();
                item.setPickColor(tempID);
                this.registry.register(item, tempID);
    }

    public LinkedList<double[][]> getAllPoints () {
        return allPoints;
    }
}

