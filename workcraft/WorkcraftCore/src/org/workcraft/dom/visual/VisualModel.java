package org.workcraft.dom.visual;

import org.workcraft.commands.AbstractLayoutCommand;
import org.workcraft.dom.Container;
import org.workcraft.dom.Model;
import org.workcraft.dom.Node;
import org.workcraft.dom.math.MathConnection;
import org.workcraft.dom.math.MathModel;
import org.workcraft.dom.math.MathNode;
import org.workcraft.dom.visual.connections.VisualConnection;
import org.workcraft.exceptions.InvalidConnectionException;
import org.workcraft.gui.properties.ModelProperties;
import org.workcraft.gui.tools.Decorator;
import org.workcraft.gui.tools.GraphEditorTool;
import org.workcraft.observation.ObservableState;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.List;

public interface VisualModel extends Model<VisualNode, VisualConnection>, ObservableState {
    void createDefaultStructure();
    MathModel getMathModel();
    void draw(Graphics2D g, Decorator decorator);

    void setCurrentLevel(Container group);
    Container getCurrentLevel();

    String getMathReference(Node node);
    String getMathName(Node node);
    void setMathName(Node node, String name);

    void validateConnection(VisualNode first, VisualNode second) throws InvalidConnectionException;
    VisualConnection connect(VisualNode first, VisualNode second) throws InvalidConnectionException;
    VisualConnection connect(VisualNode first, VisualNode second, MathConnection connection) throws InvalidConnectionException;

    void validateUndirectedConnection(VisualNode first, VisualNode second) throws InvalidConnectionException;
    VisualConnection connectUndirected(VisualNode first, VisualNode second) throws InvalidConnectionException;

    <T extends VisualComponent> T createVisualComponent(MathNode refNode, Class<T> type, Container container);
    <T extends VisualComponent> T createVisualComponent(MathNode refNode, Class<T> type);
    <T extends VisualComponent> T getVisualComponent(MathNode refNode, Class<T> type);
    <T extends VisualComponent> T getVisualComponentByMathReference(String ref, Class<T> type);

    <T extends VisualReplica> T createVisualReplica(VisualComponent master, Class<T> type, Container container);
    VisualPage createVisualPage(Container container);

    void selectAll();
    void selectNone();
    void selectInverse();
    void select(VisualNode node);
    void select(Collection<? extends VisualNode> nodes);
    void addToSelection(VisualNode node);
    void addToSelection(Collection<? extends VisualNode> nodes);
    void removeFromSelection(VisualNode node);
    void removeFromSelection(Collection<? extends VisualNode> nodes);
    void deleteSelection();

    Collection<VisualNode> getSelection();

    boolean isGroupable(VisualNode node);
    VisualGroup groupSelection();
    VisualPage groupPageSelection();
    void ungroupSelection();

    Collection<VisualNode> hitBox(Point2D p1, Point2D p2);
    AbstractLayoutCommand getBestLayouter();
    Rectangle2D getBoundingBox();
    Point2D getNodeSpacePosition(Point2D rootspacePosition, VisualTransformableNode node);

    void registerGraphEditorTools();
    void addGraphEditorTool(GraphEditorTool tool);
    void removeGraphEditorTool(GraphEditorTool tool);
    List<GraphEditorTool> getGraphEditorTools();

    ModelProperties getProperties(VisualNode node);

    default void afterPaste() {
    }

}
