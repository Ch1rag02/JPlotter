package hageldave.jplotter.renderables;

import hageldave.jplotter.util.AlignmentConstants;
import hageldave.jplotter.util.Pair;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.*;

// renderable class for bars
public class BarGroup {
    final protected TreeMap<Integer, BarStruct> groupedBars = new TreeMap<>();
    protected PriorityQueue<BarStruct> sortedBars =
            new PriorityQueue<>(Comparator.comparingDouble(o -> o.ID));
    protected String label;

    public BarGroup() { }

    public BarGroup(final String label) {
        this.label = label;
    }

    public BarGroup addBar(final int ID, final double[] data, final Color color, final String descr) {
        double val = Arrays.stream(data).sum();
        return addData(new int[]{ID}, new double[]{val}, new Color[]{color}, new String[]{descr});
    }

    public BarGroup addBar(final int ID, final double val, final Color color, final String descr) {
        return addData(new int[]{ID}, new double[]{val}, new Color[]{color}, new String[]{descr});
    }

    public BarGroup addBar(final int ID, final double val, final Color color) {
        return addData(new int[]{ID}, new double[]{val}, new Color[]{color}, new String[]{""});
    }

    // what if stack is added, and user passes description?!
    public BarGroup addData(final int[] IDs, final double[] data, final Color[] color, final String[] descr) {
        if (!(IDs.length == data.length && data.length == color.length && color.length == descr.length))
            throw new IllegalArgumentException("All arrays have to have equal size!");
        for (int i = 0; i < data.length; i++) {
            if (this.groupedBars.containsKey(IDs[i])) {
                this.groupedBars.get(IDs[i]).stacks.add(new Stack(data[i], color[i]));
            } else {
                this.groupedBars.put(IDs[i], new BarStruct(data[i], color[i], descr[i], IDs[i]));
            }
        }
        return this;
    }

    public BarGroup removeBars(final int... IDs) {
        for (int ID: IDs) {
            this.groupedBars.remove(ID);
        }
        return this;
    }

    public BarGroup sortBars(final Comparator<BarStruct> comparator) {
        this.sortedBars = new PriorityQueue<>(comparator);
        return this;
    }

    /**
     * important! bounds do not represent the coordinate system
     * @return
     */
    public Rectangle2D getBounds(final int alignment) {
        double minValueBar = groupedBars.values().parallelStream()
                        .map(BarStruct::getBounds)
                        .mapToDouble(e->e.first)
                        .min().orElse(0);
        double maxValueBar = groupedBars.values().parallelStream()
                .map(BarStruct::getBounds)
                .mapToDouble(e->e.second)
                .max().orElse(0);
        double start = 0;
        double end = groupedBars.size();
        if (alignment == AlignmentConstants.VERTICAL) {
            return new Rectangle2D.Double(start, minValueBar, end, maxValueBar);
        } else if (alignment == AlignmentConstants.HORIZONTAL) {
            return new Rectangle2D.Double(minValueBar, start, maxValueBar, end);
        }
        return null;
    }

    protected void copyContent(final Collection<BarStruct> c1,
                               final Collection<BarStruct> c2) {
        c1.clear(); c1.addAll(c2);
    }

    public String getLabel() {
        return label;
    }

    public TreeMap<Integer, BarStruct> getGroupedBars() {
        return groupedBars;
    }

    public PriorityQueue<BarStruct> getSortedBars() {
        copyContent(this.sortedBars, groupedBars.values());
        return sortedBars;
    }

    /**
     * was wird in struct gespeichert?
     *
     * ID, stacks, descr, evt. barlength?
     *
     */
    public static class BarStruct {
        // currently non sortable
        final public LinkedList<Stack> stacks = new LinkedList<>();
        public String description;
        public int ID;

        public BarStruct(final double length, final Color color, final String description, final int ID) {
            this.stacks.add(new Stack(length, color));
            this.description = description;
            this.ID = ID;
        }

        public BarStruct addStack(final Stack stack) {
            this.stacks.add(stack);
            return this;
        }

        // needs implementation
        public BarStruct sortStacks() {
            return null;
        }

        public Pair<Double, Double> getBounds() {
            double minVal = 0; double maxVal = 0;
            double tempStackLength = 0;
            for (Stack stack: stacks) {
                if (stack.length > 0 && tempStackLength >= 0) {
                    maxVal += stack.length;
                } else if (stack.length < 0 && tempStackLength <= 0) {
                    minVal += stack.length;
                } else if (stack.length > 0 && tempStackLength < 0) {
                    if (maxVal + stack.length > maxVal) {
                        maxVal += stack.length;
                    }
                } else if (stack.length < 0 && tempStackLength > 0) {
                    if (minVal + stack.length < minVal) {
                        minVal += stack.length;
                    }
                }
                tempStackLength += stack.length;
            }
            return new Pair<>(minVal, maxVal);
        }
    }

    /**
     * was wird in Stack gespeichert? evt. umbenennen btw.
     *  - Farbe
     *  - länge
     *  -
     *
     */
    public static class Stack {
        public Color stackColor;
        public double length;
        public int pickColor;

        public Stack(final double length, final Color stackColor) {
            this.stackColor = stackColor;
            this.length = length;
        }

        /**
         * Sets the picking color.
         * When a non 0 transparent color is specified its alpha channel will be set to 0xff to make it opaque.
         * @param pickID picking color of the point (see {@link Points} for details)
         * @return this for chaining
         */
        public Stack setPickColor(int pickID){
            if(pickID != 0)
                pickID = pickID | 0xff000000;
            this.pickColor = pickID;
            return this;
        }
    }
}
