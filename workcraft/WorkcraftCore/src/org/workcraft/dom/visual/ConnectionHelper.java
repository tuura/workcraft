package org.workcraft.dom.visual;

import org.workcraft.dom.visual.connections.*;
import org.workcraft.dom.visual.connections.VisualConnection.ConnectionType;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ConnectionHelper {
    private static final double SAME_ANCHOR_POINT_DISTANCE_THRESHOLD = 0.1;
    private static final double SAME_ANCHOR_POINT_GRADIENT_THRESHOLD = 0.1;

    private static boolean areDifferentAnchorPoints(Point2D p1, Point2D p2) {
        return p1.distance(p2) > SAME_ANCHOR_POINT_DISTANCE_THRESHOLD;
    }

    private static boolean canBeAnchorPoint(Point2D locationInRootSpace, VisualConnection connection) {
        Point2D first = connection.getFirstCenter();
        Point2D second = connection.getSecondCenter();
        return areDifferentAnchorPoints(locationInRootSpace, first) && areDifferentAnchorPoints(locationInRootSpace, second);
    }

    public static void addControlPoints(VisualConnection connection, List<Point2D> locationsInRootSpace) {
        if ((connection != null) && (connection.getGraphic() instanceof Polyline) && (locationsInRootSpace != null)) {
            Polyline polyline = (Polyline) connection.getGraphic();
            AffineTransform rootToLocalTransform = TransformHelper.getTransformFromRoot(connection);
            for (Point2D locationInRootSpace: locationsInRootSpace) {
                if (canBeAnchorPoint(locationInRootSpace, connection)) {
                    Point2D locationInLocalSpace = rootToLocalTransform.transform(locationInRootSpace, null);
                    polyline.addControlPoint(locationInLocalSpace);
                }
            }
        }
    }

    public static void prependControlPoints(VisualConnection connection, List<Point2D> locationsInRootSpace) {
        if ((connection != null) && (connection.getGraphic() instanceof Polyline) && (locationsInRootSpace != null)) {
            Polyline polyline = (Polyline) connection.getGraphic();
            AffineTransform rootToLocalTransform = TransformHelper.getTransformFromRoot(connection);
            int segment = 0;
            for (Point2D locationInRootSpace: locationsInRootSpace) {
                if (canBeAnchorPoint(locationInRootSpace, connection)) {
                    Point2D locationInLocalSpace = rootToLocalTransform.transform(locationInRootSpace, null);
                    polyline.insertControlPointInSegment(locationInLocalSpace, segment++);
                }
            }
        }
    }

    public static ControlPoint createControlPoint(VisualConnection connection, Point2D locationInRootSpace) {
        ControlPoint result = null;
        if ((connection != null) && (locationInRootSpace != null)) {
            AffineTransform rootToLocalTransform = TransformHelper.getTransformFromRoot(connection);
            Point2D locationInLocalSpace = rootToLocalTransform.transform(locationInRootSpace, null);

            ConnectionGraphic graphic = connection.getGraphic();
            Point2D locationOnConnection = graphic.getNearestPointOnCurve(locationInLocalSpace);
            connection.setConnectionType(ConnectionType.POLYLINE);
            Polyline polyline = (Polyline) connection.getGraphic();
            int segmentIndex = polyline.getNearestSegment(locationInLocalSpace, null);
            result = polyline.insertControlPointInSegment(locationOnConnection, segmentIndex);
        }
        return result;
    }

    public static LinkedList<Point2D> getPrefixControlPoints(VisualConnection connection, Point2D splitPointInLocalSpace) {
        LinkedList<Point2D> locationsInRootSpace = new LinkedList<>();
        if ((connection != null) && (connection.getGraphic() instanceof Polyline) && (splitPointInLocalSpace != null)) {
            Polyline polyline = (Polyline) connection.getGraphic();
            int splitIndex = polyline.getNearestSegment(splitPointInLocalSpace, null);
            AffineTransform localToRootTransform = TransformHelper.getTransformToRoot(connection);
            int index = -1;
            for (ControlPoint cp:  polyline.getControlPoints()) {
                index++;
                if (index < splitIndex) {
                    Point2D locationInLocalSpace = cp.getPosition();
                    if ((index < splitIndex - 1) || areDifferentAnchorPoints(locationInLocalSpace, splitPointInLocalSpace)) {
                        Point2D locationInRootSpace = localToRootTransform.transform(locationInLocalSpace, null);
                        locationsInRootSpace.add(locationInRootSpace);
                    }
                }
            }
        }
        return locationsInRootSpace;
    }

    public static LinkedList<Point2D> getSuffixControlPoints(VisualConnection connection, Point2D splitPointInLocalSpace) {
        LinkedList<Point2D> locationsInRootSpace = new LinkedList<>();
        if ((connection != null) && (connection.getGraphic() instanceof Polyline) && (splitPointInLocalSpace != null)) {
            Polyline polyline = (Polyline) connection.getGraphic();
            int splitIndex = polyline.getNearestSegment(splitPointInLocalSpace, null);
            AffineTransform localToRootTransform = TransformHelper.getTransformToRoot(connection);
            int index = -1;
            for (ControlPoint cp:  polyline.getControlPoints()) {
                index++;
                if (index >= splitIndex) {
                    Point2D locationInLocalSpace = cp.getPosition();
                    if ((index > splitIndex) || areDifferentAnchorPoints(locationInLocalSpace, splitPointInLocalSpace)) {
                        Point2D locationInRootSpace = localToRootTransform.transform(locationInLocalSpace, null);
                        locationsInRootSpace.add(locationInRootSpace);
                    }
                }
            }
        }
        return locationsInRootSpace;
    }

    public static LinkedList<Point2D> getMergedControlPoints(VisualTransformableNode mergeNode,
            VisualConnection firstConnection, VisualConnection secondConnection) {

        LinkedList<Point2D> result = getControlPoints(firstConnection);
        Point2D lastLocation = result.isEmpty() ? null : result.getLast();

        if (mergeNode != null) {
            Point2D nodeLocation = mergeNode.getRootSpacePosition();
            if ((lastLocation != null) && !areDifferentAnchorPoints(nodeLocation, lastLocation)) {
                result.removeLast();
            }
            result.add(nodeLocation);
            lastLocation = nodeLocation;
        }

        LinkedList<Point2D> secondLocations = getControlPoints(secondConnection);
        Point2D startLocation = secondLocations.isEmpty() ? null : secondLocations.getFirst();
        if ((startLocation != null) && (lastLocation != null) && !areDifferentAnchorPoints(lastLocation, startLocation)) {
            secondLocations.removeFirst();
        }
        result.addAll(secondLocations);
        return result;
    }

    public static Point2D getControlPoint(VisualTransformableNode node) {
        return node == null ? null : node.getRootSpacePosition();
    }

    public static LinkedList<Point2D> getControlPoints(VisualConnection connection) {
        LinkedList<Point2D> result = new LinkedList<>();
        if ((connection != null) && (connection.getGraphic() instanceof Polyline)) {
            Polyline polyline = (Polyline) connection.getGraphic();
            AffineTransform localToRootTransform = TransformHelper.getTransformToRoot(connection);
            for (ControlPoint cp : polyline.getControlPoints()) {
                Point2D location = localToRootTransform.transform(cp.getPosition(), null);
                result.add(location);
            }
        }
        return result;
    }

    public static void filterControlPoints(Polyline polyline) {
        filterControlPoints(polyline, SAME_ANCHOR_POINT_DISTANCE_THRESHOLD, SAME_ANCHOR_POINT_GRADIENT_THRESHOLD);
    }

    public static void filterControlPoints(Polyline polyline, double distanceThreshold, double gradientThreshold) {
        PartialCurveInfo curveInfo = polyline.getCurveInfo();
        // Forward filtering by distance
        Point2D startPos = polyline.getPointOnCurve(curveInfo.tStart);
        filterControlPointsByDistance(polyline, startPos, distanceThreshold, false);
        // Backward filtering by distance
        Point2D endPos = polyline.getPointOnCurve(curveInfo.tEnd);
        filterControlPointsByDistance(polyline, endPos, distanceThreshold, true);
        // Filtering by gradient
        int i = 0;
        while (i < polyline.getControlPointCount()) {
            Point2D predPos = startPos;
            if (i > 0) {
                ControlPoint pred = polyline.getControlPoint(i - 1);
                predPos = pred.getPosition();
            }
            Point2D succPos = endPos;
            if (i < polyline.getControlPointCount() - 1) {
                ControlPoint succ = polyline.getControlPoint(i + 1);
                succPos = succ.getPosition();
            }
            ControlPoint cur = polyline.getControlPoint(i);
            Point2D curPos = cur.getPosition();
            if (Math.abs(calcGradient(predPos, curPos, succPos)) < gradientThreshold) {
                polyline.remove(cur);
            } else {
                i++;
            }
        }
    }

    private static void filterControlPointsByDistance(Polyline polyline, Point2D startPos, double threshold, boolean reverse) {
        List<ControlPoint> controlPoints = new LinkedList<>(polyline.getControlPoints());
        if (reverse) {
            Collections.reverse(controlPoints);
        }
        Point2D predPos = startPos;
        for (ControlPoint cp:  controlPoints) {
            Point2D curPos = cp.getPosition();
            if (curPos.distance(predPos) < threshold) {
                polyline.remove(cp);
            } else {
                predPos = curPos;
            }
        }
    }

    public static void moveControlPoints(VisualConnection connection, Point2D offset) {
        if ((connection != null) && (offset != null)) {
            ConnectionGraphic graphic = connection.getGraphic();
            for (ControlPoint cp:  graphic.getControlPoints()) {
                Point2D p1 = cp.getPosition();
                Point2D p2 = new Point2D.Double(p1.getX() + offset.getX(), p1.getY() + offset.getY());
                cp.setPosition(p2);
            }
        }
    }

    public static double calcGradient(Point2D p1, Point2D p2, Point2D p3) {
        double p1x = p1.getX();
        double p1y = p1.getY();
        double p2x = p2.getX();
        double p2y = p2.getY();
        double p3x = p3.getX();
        double p3y = p3.getY();
        return p1x * (p2y - p3y) + p2x * (p3y - p1y) + p3x * (p1y - p2y);
    }

    public static Point2D getPredPoint(VisualConnection connection, Point2D splitPointInLocalSpace) {
        Point2D locationInRootSpace = null;
        if ((connection != null) && (connection.getGraphic() instanceof Polyline) && (splitPointInLocalSpace != null)) {
            Polyline polyline = (Polyline) connection.getGraphic();
            int splitIndex = polyline.getNearestSegment(splitPointInLocalSpace, null);
            AffineTransform localToRootTransform = TransformHelper.getTransformToRoot(connection);
            int index = -1;
            for (ControlPoint cp:  polyline.getControlPoints()) {
                index++;
                if (index < splitIndex) {
                    Point2D locationInLocalSpace = cp.getPosition();
                    if ((index < splitIndex - 1) || areDifferentAnchorPoints(locationInLocalSpace, splitPointInLocalSpace)) {
                        locationInRootSpace = localToRootTransform.transform(locationInLocalSpace, null);
                    }
                }
            }
        }
        if ((locationInRootSpace == null) && (connection.getFirst() instanceof VisualTransformableNode)) {
            VisualTransformableNode first = (VisualTransformableNode) connection.getFirst();
            locationInRootSpace = first.getRootSpacePosition();
        }
        return locationInRootSpace;
    }

    public static Point2D getSuccPoint(VisualConnection connection, Point2D splitPointInLocalSpace) {
        Point2D locationInRootSpace = null;
        if ((connection != null) && (connection.getGraphic() instanceof Polyline) && (splitPointInLocalSpace != null)) {
            Polyline polyline = (Polyline) connection.getGraphic();
            int splitIndex = polyline.getNearestSegment(splitPointInLocalSpace, null);
            AffineTransform localToRootTransform = TransformHelper.getTransformToRoot(connection);
            int index = -1;
            for (ControlPoint cp:  polyline.getControlPoints()) {
                index++;
                if (index >= splitIndex) {
                    Point2D locationInLocalSpace = cp.getPosition();
                    if ((index > splitIndex) || areDifferentAnchorPoints(locationInLocalSpace, splitPointInLocalSpace)) {
                        locationInRootSpace = localToRootTransform.transform(locationInLocalSpace, null);
                        break;
                    }
                }
            }
        }
        if ((locationInRootSpace == null) && (connection.getFirst() instanceof VisualTransformableNode)) {
            VisualTransformableNode second = (VisualTransformableNode) connection.getSecond();
            locationInRootSpace = second.getRootSpacePosition();
        }
        return locationInRootSpace;
    }

}
