package hageldave.jplotter.canvas;

import java.awt.Color;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import hageldave.imagingkit.core.Img;
import hageldave.jplotter.svg.SVGUtils;

public interface JPlotterCanvas {

	public void repaint();
	
	public void scheduleRepaint();
	
	/**
	 * En/disables SVG rendering as image.
	 * When rendering to SVG and this is enabled, instead of translating the 
	 * contents of the renderers into SVG elements, the current framebuffer image 
	 * is used and put into the dom.
	 * <p>
	 * This can be useful for example when too many SVG elements would be created
	 * resulting in a huge dom and file size when exporting as SVG.
	 * 
	 * @param enable true when no SVG elements should be created from the content
	 * of this FBOCanvas but instead a simple image element with the framebuffer's
	 * content.
	 */
	public void enableSvgAsImageRendering(boolean enable);
	
	/**
	 * @return true when enabled
	 * @see #enableSvgAsImageRendering(boolean)
	 */
	public boolean isSvgAsImageRenderingEnabled();
	
	/**
	 * Fetches the current contents of the framebuffer and returns them as an {@link Img}.
	 * @return image of the current framebuffer.
	 */
	public Img toImg();
	
	/**
	 * Reads the color value of the pixel at the specified location if areaSize == 1.
	 * This can be used to get the color or picking color under the mouse cursor.
	 * <p>
	 * Since the cursor placement may be inexact and thus miss the location the user
	 * was actually interested in, the areaSize parameter can be increased to create
	 * a window of pixels around the specified location.
	 * This window area will be examined and the most prominent non zero color value will
	 * be returned.
	 * @param x coordinate of the pixels location
	 * @param y coordinate of the pixels location
	 * @param picking whether the picking color or the visible color should be retrieved.
	 * @param areaSize width and height of the area around the specified location.
	 * @return the most prominent color in the area as integer packed ARGB value.
	 * If the returned value is to be used as an object id from picking color, then the
	 * alpha value probably has to be discarded first using {@code 0x00ffffff & returnValue}.
	 */
	public int getPixel(int x, int y, boolean picking, int areaSize);
	
	
	public int getWidth();
	
	public int getHeight();
	
	public Color getBackground();
	
	/**
	 * Creates a new SVG {@link Document} and renders this canvas as SVG elements.
	 * Will call {@link #paintToSVG(Document, Element, int, int)} after setting up
	 * the document and creating the initial elements.
	 * @return the created document
	 */
	public default Document paintSVG(){
		Document document = SVGUtils.createSVGDocument(getWidth(), getHeight());
		paintSVG(document, document.getDocumentElement());
		return document;
	}
	
	/**
	 * Renders this canvas as SVG elements under the specified parent element.
	 * Will call {@link #paintToSVG(Document, Element, int, int)} after creating 
	 * the initial elements.
	 * @param document document to create SVG elements with
	 * @param parent the parent node to which this canvas is supposed to be rendered
	 * to.
	 */
	public default void paintSVG(Document document, Element parent) {
		int w,h;
		if((w=getWidth()) >0 && (h=getHeight()) >0){
			if(SVGUtils.getDefs(document) == null){
				Element defs = SVGUtils.createSVGElement(document, "defs");
				defs.setAttributeNS(null, "id", "JPlotterDefs");
				document.getDocumentElement().appendChild(defs);
			}
			
			Element rootGroup = SVGUtils.createSVGElement(document, "g");
			parent.appendChild(rootGroup);
			rootGroup.setAttributeNS(null, "transform", "scale(1,-1) translate(0,-"+h+")");
			
			Element background = SVGUtils.createSVGElement(document, "rect");
			rootGroup.appendChild(background);
			background.setAttributeNS(null, "id", "background"+"@"+hashCode());
			background.setAttributeNS(null, "width", ""+w);
			background.setAttributeNS(null, "height", ""+h);
			background.setAttributeNS(null, "fill", SVGUtils.svgRGBhex(getBackground().getRGB()));
			
			paintToSVG(document, rootGroup, w,h);
		}
	}
	
	/**
	 * Renders this {@link FBOCanvas} in terms of SVG elements
	 * to the specified parent element of the specified SVG document.
	 * <p>
	 * This method has to be overridden when the implementing
	 * class can express its contents in terms of SVG.
	 * 
	 * @param doc document to create svg elements with
	 * @param parent to append svg elements to
	 * @param w width of the viewport (the width of this Canvas)
	 * @param h height of the viewport (the height of this Canvas)
	 */
	public default void paintToSVG(Document doc, Element parent, int w, int h){}
}
