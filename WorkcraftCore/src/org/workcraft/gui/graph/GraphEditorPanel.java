package org.workcraft.gui.graph;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.workcraft.Framework;
import org.workcraft.dom.Connection;
import org.workcraft.dom.Node;
import org.workcraft.dom.visual.BoundingBoxHelper;
import org.workcraft.dom.visual.Dependent;
import org.workcraft.dom.visual.SizeHelper;
import org.workcraft.dom.visual.Touchable;
import org.workcraft.dom.visual.TransformHelper;
import org.workcraft.dom.visual.VisualComponent;
import org.workcraft.dom.visual.VisualModel;
import org.workcraft.dom.visual.VisualNode;
import org.workcraft.dom.visual.connections.ControlPoint;
import org.workcraft.dom.visual.connections.Polyline;
import org.workcraft.dom.visual.connections.VisualConnection;
import org.workcraft.dom.visual.connections.VisualConnection.ConnectionType;
import org.workcraft.gui.DesktopApi;
import org.workcraft.gui.MainWindow;
import org.workcraft.gui.MainWindowActions;
import org.workcraft.gui.Overlay;
import org.workcraft.gui.PropertyEditorWindow;
import org.workcraft.gui.ToolControlsWindow;
import org.workcraft.gui.Toolbox;
import org.workcraft.gui.actions.ActionButton;
import org.workcraft.gui.graph.tools.GraphEditor;
import org.workcraft.gui.graph.tools.GraphEditorTool;
import org.workcraft.gui.propertyeditor.ModelProperties;
import org.workcraft.gui.propertyeditor.Properties;
import org.workcraft.gui.propertyeditor.PropertyDescriptor;
import org.workcraft.observation.StateEvent;
import org.workcraft.observation.StateObserver;
import org.workcraft.plugins.shared.CommonEditorSettings;
import org.workcraft.plugins.shared.CommonVisualSettings;
import org.workcraft.util.Hierarchy;
import org.workcraft.workspace.WorkspaceEntry;

public class GraphEditorPanel extends JPanel implements StateObserver, GraphEditor {

    private static final String RESET_TO_DEFAULTS = "Reset to defaults";
    public static final String TITLE_SUFFIX_TEMPLATE = "template";
    public static final String TITLE_SUFFIX_MODEL = "model";
    public static final String TITLE_SUFFIX_SINGLE_ELEMENT = "single element";
    public static final String TITLE_SUFFIX_SELECTED_ELEMENTS = " selected elements";
    private static final int VIEWPORT_MARGIN = 25;

    private final class UpdateEditorActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent arg0) {
            if (updateEditorPanelRequested) {
                SwingUtilities.invokeLater(() -> updateEditor());
            }
        }
    }

    private final class UpdatePropertyActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent arg0) {
            if (updatePropertyViewRequested) {
                SwingUtilities.invokeLater(() -> updatePropertyView());
            }
        }
    }

    private final class TemplateResetActionListener implements ActionListener {
        private final VisualNode templateNode;
        private final VisualNode defaultNode;

        private TemplateResetActionListener(VisualNode templateNode, VisualNode defaultNode) {
            this.templateNode = templateNode;
            this.defaultNode = defaultNode;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            templateNode.copyStyle(defaultNode);
            updatePropertyViewRequested = true;
        }
    }

    public class GraphEditorFocusListener implements FocusListener {
        private final GraphEditorPanel editor;

        public GraphEditorFocusListener(GraphEditorPanel editor) {
            this.editor = editor;
        }

        @Override
        public void focusGained(FocusEvent e) {
            final Framework framework = Framework.getInstance();
            MainWindow mainWindow = framework.getMainWindow();
            mainWindow.requestFocus(editor);
            repaint();
        }

        @Override
        public void focusLost(FocusEvent e) {
            repaint();
        }
    }

    class Resizer implements ComponentListener {

        @Override
        public void componentHidden(ComponentEvent e) {
        }

        @Override
        public void componentMoved(ComponentEvent e) {
        }

        @Override
        public void componentResized(ComponentEvent e) {
            reshape();
            repaint();
        }

        @Override
        public void componentShown(ComponentEvent e) {
        }
    }

    private static final long serialVersionUID = 1L;

    public WorkspaceEntry we;

    protected final Toolbox toolbox;

    protected Viewport view;
    protected Grid grid;
    protected Ruler ruler;
    protected ActionButton centerButton;

    private static final int size = SizeHelper.getRulerSize();
    protected Stroke borderStroke = new BasicStroke(2);
    private final Overlay overlay = new Overlay();
    private boolean firstPaint = true;
    private boolean updateEditorPanelRequested = true;
    private boolean updatePropertyViewRequested = true;

    public GraphEditorPanel(WorkspaceEntry we) {
        super(new BorderLayout());
        this.we = we;

        we.addObserver(this);

        view = new Viewport(0, 0, getWidth(), getHeight());
        grid = new Grid();

        ruler = new Ruler(size);
        view.addListener(grid);
        grid.addListener(ruler);

        centerButton = new ActionButton(MainWindowActions.VIEW_PAN_CENTER, "");
        final Framework framework = Framework.getInstance();
        final MainWindow mainWindow = framework.getMainWindow();
        centerButton.addScriptedActionListener(mainWindow.getDefaultActionListener());
        centerButton.setSize(size, size);
        this.add(centerButton);

        toolbox = new Toolbox(this);

        GraphEditorPanelMouseListener mouseListener = new GraphEditorPanelMouseListener(this, toolbox);
        GraphEditorPanelKeyListener keyListener = new GraphEditorPanelKeyListener(this, toolbox);
        GraphEditorFocusListener focusListener = new GraphEditorFocusListener(this);

        addMouseMotionListener(mouseListener);
        addMouseListener(mouseListener);
        addMouseWheelListener(mouseListener);
        addKeyListener(keyListener);
        addFocusListener(focusListener);
        addComponentListener(new Resizer());

        add(overlay, BorderLayout.CENTER);

        // FIXME: timers need to be stopped at some point
        Timer updateEditorPanelTimer = new Timer(CommonEditorSettings.getRedrawInterval(), new UpdateEditorActionListener());
        updateEditorPanelTimer.start();
        Timer updatePropertyTimer = new Timer(CommonEditorSettings.getRedrawInterval(), new UpdatePropertyActionListener());
        updatePropertyTimer.start();

        // This is a hack to prevent editor panel from loosing focus on Ctrl-UP key combination
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, DesktopApi.getMenuKeyMask()), "doNothing");
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, DesktopApi.getMenuKeyMask()), "doNothing");
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, DesktopApi.getMenuKeyMask()), "doNothing");
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, DesktopApi.getMenuKeyMask()), "doNothing");
        this.getActionMap().put("doNothing", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                //do nothing
            }
        });
    }

    private void reshape() {
        view.setShape(0, 0, getWidth(), getHeight());
        ruler.setShape(0, 0, getWidth(), getHeight());
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        AffineTransform screenTransform = (AffineTransform) g2d.getTransform().clone();

        g2d.setBackground(CommonEditorSettings.getBackgroundColor());
        g2d.clearRect(0, 0, getWidth(), getHeight());

        if (CommonEditorSettings.getGridVisibility()) {
            grid.draw(g2d);
        }
        g2d.setTransform(screenTransform);

        if (firstPaint) {
            reshape();
            firstPaint = false;
        }
        g2d.transform(view.getTransform());

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        //g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        //g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        getModel().draw(g2d, toolbox.getSelectedTool().getDecorator(this));

        if (hasFocus()) {
            toolbox.getSelectedTool().drawInUserSpace(this, g2d);
        }
        g2d.setTransform(screenTransform);

        Boolean rulerVisibility = CommonEditorSettings.getRulerVisibility();
        if (rulerVisibility) {
            ruler.draw(g2d);
        }
        centerButton.setVisible(rulerVisibility);

        if (hasFocus()) {
            toolbox.getSelectedTool().drawInScreenSpace(this, g2d);
            g2d.setTransform(screenTransform);

            g2d.setStroke(borderStroke);
            g2d.setColor(CommonVisualSettings.getBorderColor());
            g2d.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        }

        paintChildren(g2d);
    }

    @Override
    /**
     * Redraw one pixel to force redrawing of the whole model. This is usually necessary
     * to recalculate bounding boxes of children components and correctly estimate the
     * bounding boxes of their parents.
     */
    public void forceRedraw() {
        super.paintImmediately(0, 0, 1, 1);
        repaint();
    }

    public VisualModel getModel() {
        return we.getModelEntry().getVisualModel();
    }

    public Viewport getViewport() {
        return view;
    }

    private Set<Point2D> calcConnectionSnaps(VisualConnection vc) {
        Set<Point2D> result = new HashSet<>();
        result.add(vc.getSecondCenter());
        result.add(vc.getFirstCenter());
        return result;
    }

    private Set<Point2D> getComponentSnaps(VisualComponent component) {
        Set<Point2D> result = new HashSet<>();
        AffineTransform localToRootTransform = TransformHelper.getTransformToRoot(component);
        Point2D pos = TransformHelper.transform(component, localToRootTransform).getCenter();
        result.add(pos);
        for (Connection connection: getModel().getConnections(component)) {
            if (connection instanceof VisualConnection) {
                VisualConnection vc = (VisualConnection) connection;
                if (vc.getConnectionType() == ConnectionType.POLYLINE) {
                    Polyline polyline = (Polyline) vc.getGraphic();
                    ControlPoint firstControlPoint = polyline.getFirstControlPoint();
                    if ((component == connection.getFirst()) && (firstControlPoint != null)) {
                        result.add(firstControlPoint.getPosition());
                    }
                    ControlPoint lastControlPoint = polyline.getLastControlPoint();
                    if ((component == connection.getSecond()) && (lastControlPoint != null)) {
                        result.add(lastControlPoint.getPosition());
                    }
                }
                result.addAll(calcConnectionSnaps(vc));
            }
        }
        return result;
    }

    private Set<Point2D> getControlPointSnaps(ControlPoint cp) {
        Set<Point2D> result = new HashSet<>();
        Node graphics = cp.getParent();
        if (graphics instanceof Polyline) {
            Polyline polyline = (Polyline) cp.getParent();
            result.add(cp.getPosition());
            result.add(polyline.getPrevAnchorPointLocation(cp));
            result.add(polyline.getNextAnchorPointLocation(cp));
        }
        Node parent = graphics.getParent();
        if (parent instanceof VisualConnection) {
            VisualConnection vc = (VisualConnection) parent;
            result.addAll(calcConnectionSnaps(vc));
        }
        return result;
    }

    @Override
    public Set<Point2D> getSnaps(VisualNode node) {
        Set<Point2D> result = new HashSet<>();
        if (node instanceof VisualComponent) {
            VisualComponent component = (VisualComponent) node;
            result.addAll(getComponentSnaps(component));
        } else if (node instanceof ControlPoint) {
            ControlPoint cp = (ControlPoint) node;
            result.addAll(getControlPointSnaps(cp));
        }
        return result;
    }

    @Override
    public Point2D snap(Point2D pos, Set<Point2D> snaps) {
        double x = grid.snapCoordinate(pos.getX());
        double y = grid.snapCoordinate(pos.getY());
        if (snaps != null) {
            for (Point2D snap: snaps) {
                if (Math.abs(pos.getX() - snap.getX()) < Math.abs(pos.getX() - x)) {
                    x = snap.getX();
                }
                if (Math.abs(pos.getY() - snap.getY()) < Math.abs(pos.getY() - y)) {
                    y = snap.getY();
                }
            }
        }
        return new Point2D.Double(x, y);
    }

    @Override
    public WorkspaceEntry getWorkspaceEntry() {
        return we;
    }

    private Properties propertiesWrapper(final ModelProperties mix) {
        return new Properties() {
            @Override
            public Collection<PropertyDescriptor> getDescriptors() {
                ArrayList<PropertyDescriptor> list = new ArrayList<>();
                for (final PropertyDescriptor d : mix.getDescriptors()) {
                    list.add(new PropertyDescriptor() {
                        @Override
                        public void setValue(Object value) throws InvocationTargetException {
                            we.saveMemento();
                            d.setValue(value);
                        }

                        @Override
                        public Object getValue() throws InvocationTargetException {
                            return d.getValue();
                        }

                        @Override
                        public Class<?> getType() {
                            return d.getType();
                        }

                        @Override
                        public String getName() {
                            return d.getName();
                        }

                        @Override
                        public Map<? extends Object, String> getChoice() {
                            return d.getChoice();
                        }

                        @Override
                        public boolean isWritable() {
                            return d.isWritable();
                        }

                        @Override
                        public boolean isCombinable() {
                            return d.isCombinable();
                        }

                        @Override
                        public boolean isTemplatable() {
                            return d.isTemplatable();
                        }
                    });
                }
                return list;
            }
        };
    }

    private ModelProperties getModelProperties() {
        ModelProperties properties = new ModelProperties();
        // Properties of the visual model
        Properties modelProperties = getModel().getProperties(null);
        properties.addAll(modelProperties.getDescriptors());
        // Properties of the math model
        Properties mathModelProperties = getModel().getMathModel().getProperties(null);
        properties.addAll(mathModelProperties.getDescriptors());
        return properties;
    }

    private ModelProperties getNodeProperties(Node node) {
        ModelProperties properties = new ModelProperties();
        // Properties of the visual node
        Properties nodeProperties = getModel().getProperties(node);
        properties.addApplicable(nodeProperties.getDescriptors());
        if (node instanceof Properties) {
            properties.addApplicable(((Properties) node).getDescriptors());
        }
        // Properties of the math node
        if (node instanceof Dependent) {
            for (Node mathNode : ((Dependent) node).getMathReferences()) {
                Properties mathNodeProperties = getModel().getMathModel().getProperties(mathNode);
                properties.addApplicable(mathNodeProperties.getDescriptors());
                if (mathNode instanceof Properties) {
                    properties.addApplicable(((Properties) mathNode).getDescriptors());
                }
            }
        }
        return properties;
    }

    private ModelProperties getSelectionProperties(Collection<Node> nodes) {
        ModelProperties allProperties = new ModelProperties();
        for (Node node: nodes) {
            Properties nodeProperties = getNodeProperties(node);
            allProperties.addApplicable(nodeProperties.getDescriptors());
        }
        return new ModelProperties(allProperties.getDescriptors());
    }

    public void updateToolsView() {
        final Framework framework = Framework.getInstance();
        final MainWindow mainWindow = framework.getMainWindow();

        JToolBar modelToolbar = mainWindow.getModelToolbar();
        modelToolbar.removeAll();
        toolbox.setToolsForModel(modelToolbar);

        GraphEditorTool selectedTool = toolbox.getSelectedTool();
        if (selectedTool != null) {
            JToolBar controlToolbar = mainWindow.getControlToolbar();
            controlToolbar.removeAll();
            selectedTool.updateControlsToolbar(controlToolbar, this);

            JPanel panel = selectedTool.getControlsPanel(this);
            ToolControlsWindow controlWindow = mainWindow.getControlsView();
            controlWindow.setContent(panel);
        }
    }

    public void updatePropertyView() {
        ModelProperties properties;
        final VisualNode defaultNode = we.getDefaultNode();
        final VisualNode templateNode = we.getTemplateNode();
        String title = MainWindow.TITLE_PROPERTY_EDITOR;
        if (templateNode != null) {
            properties = getNodeProperties(templateNode);
            for (final PropertyDescriptor pd: new LinkedList<>(properties.getDescriptors())) {
                if (!pd.isTemplatable()) {
                    properties.remove(pd);
                }
            }
            title += " [" + TITLE_SUFFIX_TEMPLATE + "]";
        } else {
            final VisualModel model = getModel();
            final Collection<Node> selection = model.getSelection();
            if (selection.size() == 0) {
                properties = getModelProperties();
                title += " [" + TITLE_SUFFIX_MODEL + "]";
            } else if (selection.size() == 1) {
                final Node node = selection.iterator().next();
                properties = getNodeProperties(node);
                title += " [" + TITLE_SUFFIX_SINGLE_ELEMENT + "]";
            } else {
                properties = getSelectionProperties(selection);
                final int nodeCount = selection.size();
                title += " [" + nodeCount + " " + TITLE_SUFFIX_SELECTED_ELEMENTS + "]";
            }
        }

        final Framework framework = Framework.getInstance();
        final MainWindow mainWindow = framework.getMainWindow();
        final PropertyEditorWindow propertyEditorWindow = mainWindow.getPropertyView();
        GraphEditorTool selectedTool = toolbox.getSelectedTool();
        if (!selectedTool.requiresPropertyEditor() || properties.getDescriptors().isEmpty()) {
            propertyEditorWindow.clearObject();
        } else {
            propertyEditorWindow.setObject(propertiesWrapper(properties));
            if ((templateNode != null) && (defaultNode != null)) {
                JButton resetButton = new JButton(RESET_TO_DEFAULTS);
                resetButton.addActionListener(new TemplateResetActionListener(templateNode, defaultNode));
                propertyEditorWindow.add(resetButton, BorderLayout.SOUTH);
                // A hack to display reset button: toggle its visibility a couple of times.
                resetButton.setVisible(false);
                resetButton.setVisible(true);
            }
        }
        mainWindow.setPropertyEditorTitle(title);
        updatePropertyViewRequested = false;
    }

    private void updateEditor() {
        super.repaint();
        updateEditorPanelRequested = false;
    }

    @Override
    public void repaint() {
        updateEditorPanelRequested = true;
    }

    @Override
    public void notify(StateEvent e) {
        updatePropertyViewRequested = true;
        updateEditorPanelRequested = true;
    }

    @Override
    public EditorOverlay getOverlay() {
        return overlay;
    }

    public Toolbox getToolBox() {
        return toolbox;
    }

    public void zoomIn() {
        SwingUtilities.invokeLater(() -> {
            getViewport().zoom(1);
            repaint();
            requestFocus();
        });
    }

    public void zoomOut() {
        SwingUtilities.invokeLater(() -> {
            getViewport().zoom(-1);
            repaint();
            requestFocus();
        });
    }

    public void zoomDefault() {
        SwingUtilities.invokeLater(() -> {
            getViewport().scaleDefault();
            repaint();
            requestFocus();
        });
    }

    public void zoomFit() {
        SwingUtilities.invokeLater(() -> {
            Viewport viewport = getViewport();
            Rectangle2D viewportBox = viewport.getShape();
            VisualModel model = getModel();
            Collection<Touchable> nodes = Hierarchy.getChildrenOfType(model.getRoot(), Touchable.class);
            if (!model.getSelection().isEmpty()) {
                nodes.retainAll(model.getSelection());
            }
            Rectangle2D modelBox = BoundingBoxHelper.mergeBoundingBoxes(nodes);
            if ((modelBox != null) && (viewportBox != null)) {
                double ratioX = 1.0;
                double ratioY = 1.0;
                if ((viewportBox.getWidth() > VIEWPORT_MARGIN) && (viewportBox.getHeight() > VIEWPORT_MARGIN)) {
                    if (viewportBox.getWidth() > viewportBox.getHeight()) {
                        ratioX = (viewportBox.getWidth() - VIEWPORT_MARGIN) / viewportBox.getHeight();
                        ratioY = (viewportBox.getHeight() - VIEWPORT_MARGIN) / viewportBox.getHeight();
                    } else {
                        ratioX = (viewportBox.getWidth() - VIEWPORT_MARGIN) / viewportBox.getWidth();
                        ratioY = (viewportBox.getHeight() - VIEWPORT_MARGIN) / viewportBox.getWidth();
                    }
                }
                Point2D ratio = new Point2D.Double(ratioX, ratioY);
                double scaleX = ratio.getX() / modelBox.getWidth();
                double scaleY = ratio.getY() / modelBox.getHeight();
                double scale = 2.0 * Math.min(scaleX, scaleY);
                viewport.scale(scale);
                panCenter();
            }
        });
    }

    public void panLeft() {
        SwingUtilities.invokeLater(() -> {
            getViewport().pan(20, 0);
            repaint();
            requestFocus();
        });
    }

    public void panUp() {
        SwingUtilities.invokeLater(() -> {
            getViewport().pan(0, 20);
            repaint();
            requestFocus();
        });
    }

    public void panRight() {
        SwingUtilities.invokeLater(() -> {
            getViewport().pan(-20, 0);
            repaint();
            requestFocus();
        });
    }

    public void panDown() {
        SwingUtilities.invokeLater(() -> {
            getViewport().pan(0, -20);
            repaint();
            requestFocus();
        });
    }

    public void panCenter() {
        SwingUtilities.invokeLater(() -> {
            Viewport viewport = getViewport();
            Rectangle2D viewportBox = viewport.getShape();
            VisualModel model = getModel();
            Collection<Touchable> nodes = Hierarchy.getChildrenOfType(model.getRoot(), Touchable.class);
            if (!model.getSelection().isEmpty()) {
                nodes.retainAll(model.getSelection());
            }
            Rectangle2D modelBox = BoundingBoxHelper.mergeBoundingBoxes(nodes);
            if ((modelBox != null) && (viewportBox != null)) {
                int viewportCenterX = (int) Math.round(viewportBox.getCenterX());
                int viewportCenterY = (int) Math.round(viewportBox.getCenterY());
                Point2D modelCenter = new Point2D.Double(modelBox.getCenterX(), modelBox.getCenterY());
                Point modelCenterInScreenSpace = viewport.userToScreen(modelCenter);
                viewport.pan(viewportCenterX - modelCenterInScreenSpace.x, viewportCenterY - modelCenterInScreenSpace.y);
                repaint();
                requestFocus();
            }
        });
    }

}
