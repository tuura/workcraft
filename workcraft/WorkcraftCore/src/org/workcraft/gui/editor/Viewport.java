package org.workcraft.gui.editor;

import org.workcraft.dom.visual.SizeHelper;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.LinkedList;

/**
 * The <code>Viewport</code> class represents a document viewport. It is used to map the
 * coordinates from user space (i.e. the coordinates as specified by the user) to screen space
 * (i.e. the portion of an on-screen window) and vice versa.
 */
public class Viewport {

    /**
     * The default value of the scale factor is such that there are 16 user space units visible
     *  across the vertical axis of the viewport.
     */
    private static final double DEFAULT_SCALE = 1.0 / 16;

    /**
     * The scaling factor per zoom level. Increasing the zoom level by 1 will effectively magnify all
     * objects by this factor, while decreasing it by 1 will shrink all objects by the same factor.
     */
    private static final double SCALE_FACTOR = Math.pow(2, 1.0 / 8);

    /**
     * The origin point in user space.
     */
    private static final Point2D ORIGIN = new Point2D.Double(0, 0);
    public static final double MIN_DIMENSION = 0.01;

    /**
     * Current horizontal view translation in user space.
     */
    private double tx = 0.0;

    /**
     * Current vertical view translation in user space.
     */
    private double ty = 0.0;

    /**
     * Current view scale factor.
     */
    private double s = DEFAULT_SCALE;

    /**
     * The transformation from user space to screen space such that the point (0, 0) in user space is
     * mapped into the centre of the viewport, the coordinate (1) on Y axis is mapped into the topmost
     * vertical coordinate of the viewport, the coordinate (-1) on Y axis is mapped into the bottom
     * vertical coordinate of the viewport, and the coordinates on the X axis are mapped in such a way
     * as to preserve the aspect ratio of the objects displayed.
     */
    private final AffineTransform userToScreenTransform;

    /**
     * The transformation of the user space that takes into account the current pan and zoom values.
     */
    private final AffineTransform viewTransform;

    /**
     * The concatenation of the user-to-screen and pan/zoom transforms.
     */
    private final AffineTransform finalTransform;

    /**
     * The reverse of the final (concatenated) transform.
     */
    private final AffineTransform finalInverseTransform;

    /**
     * The current viewport shape.
     */
    private final Rectangle shape;

    /**
     * The list of listeners to be notified in case of viewport parameters change.
     */
    private final LinkedList<ViewportListener> listeners;

    /**
     * Called when the viewport parameters such as pan and zoom are changed. Updates the corresponding
     * transforms, and notifies the change listeners.
     */
    private void viewChanged() {
        viewTransform.setToIdentity();
        viewTransform.scale(s, s);
        viewTransform.translate(tx, ty);

        updateFinalTransform();

        // notify listeners
        for (ViewportListener l : listeners) {
            l.viewChanged(this);
        }
    }

    /**
     * Recalculates the final transform by concatenating the user-to-screen transform and the view transform.
     */
    private void updateFinalTransform() {
        finalTransform.setTransform(userToScreenTransform);
        finalTransform.concatenate(viewTransform);
        try {
            finalInverseTransform.setTransform(finalTransform.createInverse());
        } catch (NoninvertibleTransformException e) {
            finalInverseTransform.setToIdentity();
        }
    }

    /**
     * Initialises the user-to-screen transform according to the viewport parameters,
     * and the view transform with the default values.
     * @param x
     *     The x-coordinate of the top-left corner of the viewport (in pixels).
     * @param y
     * The y-coordinate of the top-left corner of the viewport (in pixels).
     * @param w
     * The width of the viewport (in pixels).
     * @param h
     * The height of the viewport (in pixels)
     */
    public Viewport(int x, int y, int w, int h) {
        viewTransform = new AffineTransform();
        userToScreenTransform = new AffineTransform();
        finalTransform = new AffineTransform();
        finalInverseTransform = new AffineTransform();
        shape = new Rectangle();
        listeners = new LinkedList<>();
        viewChanged();
        setShape(x, y, w, h);
    }

    /**
     *      @return
     * The final transform as an AffineTransform object.
     */
    public AffineTransform getTransform() {
        return finalTransform;
    }

    /**
     * @return
     * The inverse of the final transform as an AffineTransform object.
     */
    public AffineTransform getInverseTransform() {
        return finalInverseTransform;
    }

    /**
     * Maps a point in user space into a point in screen space.
     * @param pointInUserSpace
     * The point in user space (in double precision)
     * @return
     * The corresponding point in screen space (in integer precision)
     */
    public Point userToScreen(Point2D pointInUserSpace) {
        Point result = new Point();
        finalTransform.transform(pointInUserSpace, result);
        return result;
    }

    public Rectangle userToScreen(Rectangle2D rectInUserSpace) {
        Point ul = userToScreen(new Point2D.Double(rectInUserSpace.getMinX(), rectInUserSpace.getMinY()));
        Point lr = userToScreen(new Point2D.Double(rectInUserSpace.getMaxX(), rectInUserSpace.getMaxY()));

        return new Rectangle(ul.x, ul.y, lr.x - ul.x, lr.y - ul.y);
    }

    /**
     * Maps a point in screen space into a point in user space.
     * @param pointInScreenSpace
     * The point in screen space (in integer precision)
     * @return
     * The corresponding point in user space (in double precision)
     */
    public Point2D screenToUser(Point pointInScreenSpace) {
        Point2D result = new Point2D.Double();
        finalInverseTransform.transform(pointInScreenSpace, result);
        return result;
    }

    /**
     * Calculates the size of one screen pixel in user space.
     * @return
     * X value of the returned point contains the horizontal pixel size,
     * Y value contains the vertical pixel size.
     * With the default user-to-screen transform these values are equal.
     */
    public Point2D pixelSizeInUserSpace() {
        Point originInScreenSpace = userToScreen(ORIGIN);
        originInScreenSpace.x += 1;
        originInScreenSpace.y += 1;
        return screenToUser(originInScreenSpace);
    }

    /**
     * Pans the viewport by the specified amount.
     * @param dx
     * The amount of horizontal panning required (in pixels)
     * @param dy
     * The amount of vertical panning required (in pixels)
     */
    public void pan(int dx, int dy) {
        Point originInScreenSpace = userToScreen(ORIGIN);
        originInScreenSpace.x += dx;
        originInScreenSpace.y += dy;
        Point2D panInUserSpace = screenToUser(originInScreenSpace);

        tx += panInUserSpace.getX();
        ty += panInUserSpace.getY();

        viewChanged();
    }

    private void setScale(double scale) {
        if (scale < 0.01f) {
            scale = 0.01f;
        }
        if (scale > 1.0f) {
            scale = 1.0f;
        }
        this.s = scale;
    }

    public void scale(double scale) {
        setScale(scale);
        viewChanged();
    }

    public void scaleDefault() {
        double minDimension = Math.max(1.0, Math.min(shape.width, shape.height));
        if (minDimension > MIN_DIMENSION) {
            setScale(10.0 * SizeHelper.getScreenDpmm() / minDimension);
        }
        viewChanged();
    }

    /**
     * Zooms the viewport by the specified amount of levels. One positive level is the magnification
     * by 2 to the power of 1/4. Thus, increasing the zoom by 4 levels magnifies the objects to twice
     * their size. One negative level results in the decreasing of the objects size by the same factor.
     * @param levels
     * The required change of the zoom level. Use positive value to zoom in, negative value to zoom out.
     */
    public void zoom(int levels) {
        setScale(s * Math.pow(SCALE_FACTOR, levels));
        viewChanged();
    }

    /**
     * Zooms the viewport by the specified amount of levels. One positive level is the magnification
     * by 2 to the power of 1/4. Thus, increasing the zoom by 4 levels magnifies the objects to twice
     * their size. One negative level results in the decreasing of the objects size by the same factor.
     *
     * Anchors the viewport to the specified point, i.e. ensures that the point given in screen space
     * does not change its coordinates in user space after zoom change is carried out, allowing to zoom
     * "into" or "out of" the specified point.
     *
     * @param levels
     * The required change of the zoom level. Use positive value to zoom in, negative value to zoom out.
     * @param anchor
     * The anchor point in screen space.
     */
    public void zoom(int levels, Point anchor) {
        Point2D anchorInUserSpace = screenToUser(anchor);
        setScale(s * Math.pow(SCALE_FACTOR, levels));

        Point2D anchorInNewSpace = screenToUser(anchor);

        tx += anchorInNewSpace.getX() - anchorInUserSpace.getX();
        ty += anchorInNewSpace.getY() - anchorInUserSpace.getY();

        viewChanged();
    }

    /**
     * Changes the shape of the viewport.
     * @param x
     *     The x-coordinate of the top-left corner of the new viewport (in pixels).
     * @param y
     * The y-coordinate of the top-left corner of the new viewport (in pixels).
     * @param width
     * The width of the new viewport (in pixels).
     * @param height
     * The height of the new viewport (in pixels)
     */
    public void setShape(int x, int y, int width, int height) {
        double newMinDimension = Math.min(width, height);
        double oldMinDimension = Math.min(shape.width, shape.height);
        if ((oldMinDimension > MIN_DIMENSION) && (newMinDimension > MIN_DIMENSION)) {
            scale(s * oldMinDimension / newMinDimension);
        }

        // Updates the corresponding transforms, and notifies the change listeners
        userToScreenTransform.setToIdentity();
        userToScreenTransform.translate(width / 2.0 + x, height / 2.0 + y);
        userToScreenTransform.scale(newMinDimension, newMinDimension);
        updateFinalTransform();

        shape.setBounds(x, y, width, height);

        // notify listeners
        for (ViewportListener l : listeners) {
            l.shapeChanged(this);
        }
    }

    /**
     * @return The current viewport shape.
     */
    public Rectangle getShape() {
        return new Rectangle(shape);
    }

    /**
     * Registers a new viewport listener that will be notified if viewport parameters change.
     * @param listener
     * The new listener.
     */
    public void addListener(ViewportListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener.
     * @param listener
     * The listener to remove.
     */
    public void removeListener(ViewportListener listener) {
        listeners.remove(listener);
    }

}
