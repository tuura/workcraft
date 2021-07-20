package org.workcraft.plugins.circuit.stg;

import org.workcraft.dom.Container;
import org.workcraft.dom.Node;
import org.workcraft.dom.hierarchy.NamespaceHelper;
import org.workcraft.dom.visual.*;
import org.workcraft.dom.visual.connections.VisualConnection;
import org.workcraft.exceptions.InvalidConnectionException;
import org.workcraft.formula.BooleanFormula;
import org.workcraft.formula.BooleanVariable;
import org.workcraft.formula.Literal;
import org.workcraft.formula.Not;
import org.workcraft.formula.dnf.Dnf;
import org.workcraft.formula.dnf.DnfClause;
import org.workcraft.formula.dnf.DnfGenerator;
import org.workcraft.formula.visitors.StringGenerator;
import org.workcraft.plugins.circuit.*;
import org.workcraft.plugins.circuit.utils.CircuitUtils;
import org.workcraft.plugins.petri.VisualPlace;
import org.workcraft.plugins.petri.VisualTransition;
import org.workcraft.plugins.stg.*;
import org.workcraft.plugins.stg.converters.SignalStg;
import org.workcraft.types.Pair;
import org.workcraft.types.TwoWayMap;
import org.workcraft.utils.Geometry;
import org.workcraft.utils.Hierarchy;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;

public class CircuitToStgConverter {

    private static final double SCALE_X = 4.0;
    private static final double SCALE_Y = 6.0;
    private static final Point2D OFFSET_P1 = new Point2D.Double(0.0, -2.0);
    private static final Point2D OFFSET_P0 = new Point2D.Double(0.0, +2.0);
    private static final Point2D OFFSET_INIT_PLUS = new Point2D.Double(0.0, -2.0);
    private static final Point2D OFFSET_INIT_MINUS = new Point2D.Double(0.0, +2.0);
    private static final Point2D OFFSET_INC_PLUS = new Point2D.Double(0.0, -1.0);
    private static final Point2D OFFSET_INC_MINUS = new Point2D.Double(0.0, +1.0);

    private final VisualCircuit circuit;
    private final VisualStg stg;

    private final Map<VisualNode, Pair<VisualContact, Boolean>> nodeToDriverMap;
    private final TwoWayMap<VisualContact, SignalStg> driverToStgMap;

    public CircuitToStgConverter(VisualCircuit circuit) {
        this.circuit = circuit;
        this.stg = new VisualStg(new Stg());
        convertTitle();
        convertPages();
        HashSet<VisualContact> drivers = identifyDrivers();
        this.nodeToDriverMap = associateNodesToDrivers(drivers);
        this.driverToStgMap = convertDriversToStgs(drivers);
        connectDriverStgs(drivers);
        if (CircuitSettings.getSimplifyStg()) {
            // Remove dead transitions
            simplifyDriverStgs(drivers);
        }
        positionDriverStgs(drivers);
        groupDriverStgs(drivers);
        cleanupPages();
    }

    public CircuitToStgConverter(VisualCircuit circuit, VisualStg stg) {
        this.circuit = circuit;
        this.stg = stg;
        HashSet<VisualContact> drivers = identifyDrivers();
        this.nodeToDriverMap = associateNodesToDrivers(drivers);
        // STGs already exist, just associate them with the drivers
        this.driverToStgMap = associateDriversToStgs(drivers);
        positionDriverStgs(drivers);
        groupDriverStgs(drivers);
    }

    public VisualStg getStg() {
        return stg;
    }

    public boolean isDriver(VisualContact contact) {
        return driverToStgMap.containsKey(contact);

    }

    public SignalStg getSignalStg(VisualNode node) {
        SignalStg result = null;
        Pair<VisualContact, Boolean> driverAndInversion = nodeToDriverMap.get(node);
        if (driverAndInversion != null) {
            VisualContact driver = driverAndInversion.getFirst();
            result = driverToStgMap.getValue(driver);
        }
        return result;
    }

    public Pair<SignalStg, Boolean> getSignalStgAndInversion(VisualNode node) {
        Pair<SignalStg, Boolean> result = null;
        Pair<VisualContact, Boolean> driverAndInvert = nodeToDriverMap.get(node);
        SignalStg signal = null;
        boolean isInverted = false;
        if (driverAndInvert != null) {
            VisualContact driver = driverAndInvert.getFirst();
            signal = driverToStgMap.getValue(driver);
            isInverted = driverAndInvert.getSecond();
        }
        if (signal != null) {
            result = new Pair<>(signal, isInverted);
        }
        return result;
    }

    private void convertTitle() {
        stg.setTitle(circuit.getTitle());
    }

    private void convertPages() {
        NamespaceHelper.copyPageStructure(circuit, stg);
        HashMap<String, Container> refToPageMapping = NamespaceHelper.getRefToPageMapping(stg);
        // Create pages for environment components and multi-output components (empty pages can be removed later)
        for (VisualFunctionComponent srcComponent: circuit.getVisualFunctionComponents()) {
            if (srcComponent.getIsEnvironment() || (srcComponent.getVisualOutputs().size() > 1)) {
                Container dstContainer = null;
                Node srcParent = srcComponent.getParent();
                if (srcParent instanceof VisualPage) {
                    String containerRef = circuit.getMathReference(srcParent);
                    dstContainer = refToPageMapping.get(containerRef);
                }
                VisualPage dstPage = stg.createVisualPage(dstContainer);
                stg.setMathName(dstPage, circuit.getMathName(srcComponent));
            }
        }
    }

    private HashSet<VisualContact> identifyDrivers() {
        HashSet<VisualContact> result = new HashSet<>();
        for (VisualContact contact: circuit.getVisualFunctionContacts()) {
            VisualContact driver = CircuitUtils.findDriver(circuit, contact, true);
            if (driver == null) {
                driver = contact;
            }
            result.add(driver);
        }
        return result;
    }

    private HashMap<VisualNode, Pair<VisualContact, Boolean>> associateNodesToDrivers(HashSet<VisualContact> driverSet) {
        HashMap<VisualNode, Pair<VisualContact, Boolean>> result = new HashMap<>();
        for (VisualContact driver: driverSet) {
            if (!result.containsKey(driver)) {
                result.putAll(propagateDriverInversion(driver, new Pair<>(driver, false)));
            }
        }
        return result;
    }

    private HashMap<VisualNode, Pair<VisualContact, Boolean>> propagateDriverInversion(
            VisualNode node, Pair<VisualContact, Boolean> driverAndInversion) {

        HashMap<VisualNode, Pair<VisualContact, Boolean>> result = new HashMap<>();
        result.put(node, driverAndInversion);
        // Support for zero delay buffers and inverters.
        if (node instanceof VisualContact) {
            VisualContact contact = (VisualContact) node;
            Node parent = node.getParent();
            if (contact.isInput() && (parent instanceof VisualCircuitComponent)) {
                VisualFunctionComponent component = (VisualFunctionComponent) parent;
                if (component.getIsZeroDelay() && (component.isBuffer() || component.isInverter())) {
                    VisualContact driver = driverAndInversion.getFirst();
                    boolean isInverting = component.isInverter();
                    driverAndInversion = new Pair<>(driver, isInverting);
                    for (VisualContact c: component.getContacts()) {
                        if (c.isOutput()) {
                            node = c;
                            result.put(node, driverAndInversion);
                        }
                    }
                }
            }
        }
        for (VisualConnection connection: circuit.getConnections(node)) {
            if ((connection.getFirst() == node) && (connection instanceof VisualCircuitConnection)) {
                result.put(connection, driverAndInversion);
                VisualNode succNode = connection.getSecond();
                if (!result.containsKey(succNode)) {
                    result.putAll(propagateDriverInversion(succNode, driverAndInversion));
                }
            }
        }
        return result;
    }

    private TwoWayMap<VisualContact, SignalStg> convertDriversToStgs(HashSet<VisualContact> drivers) {
        TwoWayMap<VisualContact, SignalStg> result = new TwoWayMap<>();
        for (VisualContact driver: drivers) {
            VisualContact signal = CircuitUtils.findSignal(circuit, driver, true);
            if (signal.isDriver() || signal.isPort()) {
                boolean initToOne = signal.getReferencedComponent().getInitToOne();
                String signalRef = CircuitUtils.getSignalReference(circuit, signal);

                String parentRef = NamespaceHelper.getParentReference(signalRef);
                VisualPage container = stg.getVisualComponentByMathReference(parentRef, VisualPage.class);

                String signalName = NamespaceHelper.getReferenceName(signalRef);

                String zeroName = SignalStg.appendLowSuffix(signalName);
                VisualPlace zeroPlace = stg.createVisualPlace(zeroName, container);
                if (!initToOne) {
                    zeroPlace.getReferencedComponent().setTokens(1);
                }

                String oneName = SignalStg.appendHighSuffix(signalName);
                VisualPlace onePlace = stg.createVisualPlace(oneName, container);
                if (initToOne) {
                    onePlace.getReferencedComponent().setTokens(1);
                }

                SignalStg signalStg = new SignalStg(zeroPlace, onePlace);
                result.put(driver, signalStg);
            }
        }
        return result;
    }

    private void connectDriverStgs(HashSet<VisualContact> drivers) {
        for (VisualContact driver: drivers) {
            BooleanFormula setFunc = null;
            BooleanFormula resetFunc = null;
            if (driver instanceof VisualFunctionContact) {
                setFunc = ((VisualFunctionContact) driver).getSetFunction();
                resetFunc = ((VisualFunctionContact) driver).getResetFunction();
            }
            // Create complementary set/reset if only one of them is defined
            if ((setFunc != null) && (resetFunc == null)) {
                resetFunc = new Not(setFunc);
            } else if ((setFunc == null) && (resetFunc != null)) {
                setFunc = new Not(resetFunc);
            }
            Dnf setDnf = DnfGenerator.generate(setFunc);
            createSignalStgTransitions(driver, setDnf, SignalTransition.Direction.PLUS);

            Dnf resetDnf = DnfGenerator.generate(resetFunc);
            createSignalStgTransitions(driver, resetDnf, SignalTransition.Direction.MINUS);
        }
    }

    private void createSignalStgTransitions(VisualContact driver, Dnf dnf, SignalTransition.Direction direction) {
        VisualContact signal = CircuitUtils.findSignal(circuit, driver, true);
        SignalStg driverStg = driverToStgMap.getValue(driver);
        if ((signal != null) && (driverStg != null)) {
            createSignalStgTransitions(signal, driverStg, dnf, direction);
        }
    }

    private void createSignalStgTransitions(VisualContact signal, SignalStg driverStg, Dnf dnf, SignalTransition.Direction direction) {
        VisualPlace predPlace = direction == SignalTransition.Direction.PLUS ? driverStg.zero : driverStg.one;
        VisualPlace succPlace = direction == SignalTransition.Direction.PLUS ? driverStg.one : driverStg.zero;
        Collection<VisualSignalTransition> transitions = direction == SignalTransition.Direction.PLUS ? driverStg.riseList : driverStg.fallList;

        TreeSet<DnfClause> clauses = new TreeSet<>(
                (arg0, arg1) -> {
                    String st1 = StringGenerator.toString(arg0);
                    String st2 = StringGenerator.toString(arg1);
                    return st1.compareTo(st2);
                });

        clauses.addAll(dnf.getClauses());

        String signalRef = CircuitUtils.getSignalReference(circuit, signal);
        String signalName = NamespaceHelper.getReferenceName(signalRef);
        Signal.Type signalType = CircuitUtils.getSignalType(circuit, signal);
        String containerRef = NamespaceHelper.getParentReference(signalRef);
        VisualPage container = stg.getVisualComponentByMathReference(containerRef, VisualPage.class);
        for (DnfClause clause : clauses) {
            // In self-looped signals the read-arcs will clash with producing/consuming arcs:
            // 1) a read-arc from a preset place is redundant (is superseded by a consuming arc);
            // 2) a read-arc from a postset place makes the transition dead.
            boolean isDeadTransition = false;
            HashSet<VisualPlace> placesToRead = new HashSet<>();
            for (Literal literal : clause.getLiterals()) {
                BooleanVariable variable = literal.getVariable();
                VisualContact sourceContact = circuit.getVisualComponent((Contact) variable, VisualContact.class);
                Pair<VisualContact, Boolean> sourceDriverAndInversion = nodeToDriverMap.get(sourceContact);
                VisualContact sourceDriver = sourceDriverAndInversion.getFirst();
                boolean sourceInversion = sourceDriverAndInversion.getSecond();
                SignalStg sourceDriverStg = driverToStgMap.getValue(sourceDriver);
                if (sourceDriverStg != null) {
                    VisualPlace place = (literal.getNegation() != sourceInversion) ? sourceDriverStg.zero : sourceDriverStg.one;
                    if (place != predPlace) {
                        // 1) a read-arc from a preset place is redundant (is superseded by a consuming arc);
                        placesToRead.add(place);
                    }
                    if (place == succPlace) {
                        // 2) a read-arc from a postset place makes the transition dead.
                        isDeadTransition = true;
                    }
                }
            }

            if (!isDeadTransition) {
                VisualSignalTransition transition = stg.createVisualSignalTransition(signalName, signalType, direction, container);
                transition.setLabel(StringGenerator.toString(clause));
                transitions.add(transition);
                // Create read-arcs.
                for (VisualPlace place : placesToRead) {
                    try {
                        stg.connectUndirected(place, transition);
                    } catch (InvalidConnectionException e) {
                        throw new RuntimeException(e);
                    }
                }
                // Create producing/consuming arcs.
                try {
                    stg.connect(predPlace, transition);
                    stg.connect(transition, succPlace);
                } catch (InvalidConnectionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private TwoWayMap<VisualContact, SignalStg> associateDriversToStgs(HashSet<VisualContact> drivers) {
        TwoWayMap<VisualContact, SignalStg> result = new TwoWayMap<>();

        for (VisualContact driver: drivers) {
            String signalRef = CircuitUtils.getSignalReference(circuit, driver);

            String zeroRef = SignalStg.appendLowSuffix(signalRef);
            VisualPlace zeroPlace = stg.getVisualComponentByMathReference(zeroRef, VisualPlace.class);
            String oneRef = SignalStg.appendHighSuffix(signalRef);
            VisualPlace onePlace = stg.getVisualComponentByMathReference(oneRef, VisualPlace.class);

            if ((zeroPlace == null) || (onePlace == null)) {
                VisualSignalTransition plusTransition = null;
                VisualSignalTransition minusTransition = null;
                for (VisualSignalTransition transition: stg.getVisualSignalTransitions()) {
                    String transitionSignalRef = stg.getSignalReference(transition);
                    if (signalRef.equals(transitionSignalRef)) {
                        if (transition.getDirection() == SignalTransition.Direction.PLUS) {
                            plusTransition = transition;
                        }
                        if (transition.getDirection() == SignalTransition.Direction.MINUS) {
                            minusTransition = transition;
                        }
                    }
                }
                if (zeroPlace == null) {
                    VisualConnection connection = stg.getConnection(minusTransition, plusTransition);
                    if (connection instanceof VisualImplicitPlaceArc) {
                        VisualImplicitPlaceArc implicitPlace = (VisualImplicitPlaceArc) connection;
                        zeroPlace = stg.makeExplicit(implicitPlace);
                        String zeroName = NamespaceHelper.getReferenceName(zeroRef);
                        stg.setMathName(zeroPlace, zeroName);
                    }
                }
                if (onePlace == null) {
                    VisualConnection connection = stg.getConnection(plusTransition, minusTransition);
                    if (connection instanceof VisualImplicitPlaceArc) {
                        VisualImplicitPlaceArc implicitPlace = (VisualImplicitPlaceArc) connection;
                        onePlace = stg.makeExplicit(implicitPlace);
                        String oneName = NamespaceHelper.getReferenceName(oneRef);
                        stg.setMathName(onePlace, oneName);
                    }
                }
            }

            if ((zeroPlace != null) && (onePlace != null)) {
                SignalStg signalStg = new SignalStg(zeroPlace, onePlace);
                result.put(driver, signalStg);
                for (VisualSignalTransition transition: stg.getVisualSignalTransitions()) {
                    String transitionSignalRef = stg.getSignalReference(transition);
                    if (signalRef.equals(transitionSignalRef)) {
                        if (transition.getDirection() == SignalTransition.Direction.PLUS) {
                            signalStg.riseList.add(transition);
                        }
                        if (transition.getDirection() == SignalTransition.Direction.MINUS) {
                            signalStg.fallList.add(transition);
                        }
                    }
                }
            }
        }
        return result;
    }

    private void simplifyDriverStgs(HashSet<VisualContact> drivers) {
        HashSet<VisualNode> redundantTransitions = getDeadTransitions(drivers);
        redundantTransitions.addAll(getDuplicateTransitions(drivers));
        for (VisualContact driver: drivers) {
            SignalStg signalStg = driverToStgMap.getValue(driver);
            if (signalStg != null) {
                signalStg.riseList.removeAll(redundantTransitions);
                signalStg.fallList.removeAll(redundantTransitions);
            }
        }
        stg.remove(redundantTransitions);
    }

    private HashSet<VisualNode> getDeadTransitions(HashSet<VisualContact> drivers) {
        HashSet<VisualNode> result = new HashSet<>();
        for (VisualContact driver: drivers) {
            SignalStg signalStg = driverToStgMap.getValue(driver);
            if (signalStg != null) {
                HashSet<VisualNode> deadPostset = new HashSet<>(stg.getPostset(signalStg.zero));
                deadPostset.retainAll(stg.getPostset(signalStg.one));
                result.addAll(deadPostset);
            }
        }
        return result;
    }

    private HashSet<VisualNode> getDuplicateTransitions(HashSet<VisualContact> drivers) {
        HashSet<VisualNode> result = new HashSet<>();
        for (VisualContact driver: drivers) {
            SignalStg signalStg = driverToStgMap.getValue(driver);
            if (signalStg != null) {
                result.addAll(getDuplicates(signalStg.riseList));
                result.addAll(getDuplicates(signalStg.fallList));
            }
        }
        return result;
    }

    private HashSet<VisualSignalTransition> getDuplicates(Collection<VisualSignalTransition> transitions) {
        HashSet<VisualSignalTransition> result = new HashSet<>();
        for (VisualSignalTransition t1: transitions) {
            if (result.contains(t1)) continue;
            for (VisualSignalTransition t2: transitions) {
                if (t1 == t2) continue;
                Set<VisualNode> preset1 = stg.getPreset(t1);
                Set<VisualNode> postset1 = stg.getPostset(t1);
                Set<VisualNode> preset2 = stg.getPreset(t2);
                Set<VisualNode> postset2 = stg.getPostset(t2);
                if (preset1.equals(preset2) && postset1.equals(postset2)) {
                    result.add(t2);
                }
            }
        }
        return result;
    }

    private void positionDriverStgs(HashSet<VisualContact> drivers) {
        // Position STG pages according to the location circuit pages and components.
        for (VisualPage page: Hierarchy.getDescendantsOfType(stg.getRoot(), VisualPage.class)) {
            String pageRef = stg.getMathReference(page);
            VisualComponent circuitComponent = circuit.getVisualComponentByMathReference(pageRef, VisualComponent.class);
            if (circuitComponent != null) {
                Point2D pos = circuitComponent.getRootSpacePosition();
                page.setRootSpacePosition(new Point2D.Double(SCALE_X * pos.getX(), SCALE_Y * pos.getY()));
                page.copyStyle(circuitComponent);
            }
        }
        HashSet<VisualPlace> unmovedPlaces = new HashSet<>(
                Hierarchy.getChildrenOfType(stg.getRoot(), VisualPlace.class));
        HashSet<VisualTransition> unmovedTransition = new HashSet<>(
                Hierarchy.getChildrenOfType(stg.getRoot(), VisualTransition.class));
        // Position STG places and transitions according to the location circuit ports and pins.
        for (VisualContact driver: drivers) {
            SignalStg signalStg = driverToStgMap.getValue(driver);
            VisualContact signal = CircuitUtils.findSignal(circuit, driver, true);
            if ((signalStg != null) && (signal != null)) {
                Point2D centerPosition = getCenterRootSpacePosition(signal);
                signalStg.zero.setRootSpacePosition(Geometry.add(centerPosition, OFFSET_P0));
                signalStg.zero.setNamePositioning(Positioning.TOP);
                signalStg.zero.setLabelPositioning(Positioning.BOTTOM);
                signalStg.one.setRootSpacePosition(Geometry.add(centerPosition, OFFSET_P1));
                signalStg.one.setNamePositioning(Positioning.BOTTOM);
                signalStg.one.setLabelPositioning(Positioning.TOP);
                unmovedPlaces.remove(signalStg.zero);
                unmovedPlaces.remove(signalStg.one);

                centerPosition = Geometry.add(centerPosition, getDirectionOffset(signal));
                Point2D plusPosition = Geometry.add(centerPosition, OFFSET_INIT_PLUS);
                for (VisualSignalTransition transition: signalStg.riseList) {
                    transition.setRootSpacePosition(plusPosition);
                    plusPosition = Geometry.add(plusPosition, OFFSET_INC_PLUS);
                    unmovedTransition.remove(transition);
                }

                Point2D minusPosition = Geometry.add(centerPosition, OFFSET_INIT_MINUS);
                for (VisualSignalTransition transition: signalStg.fallList) {
                    transition.setRootSpacePosition(minusPosition);
                    minusPosition = Geometry.add(minusPosition, OFFSET_INC_MINUS);
                    unmovedTransition.remove(transition);
                }
            }
        }
        // Position remaining top-level places and transitions.
        HashSet<VisualComponent> movedComponents = new HashSet<>(
                Hierarchy.getChildrenOfType(stg.getRoot(), VisualComponent.class));
        movedComponents.removeAll(unmovedPlaces);
        movedComponents.removeAll(unmovedTransition);
        if (!movedComponents.isEmpty()) {
            Rectangle2D bb = BoundingBoxHelper.mergeBoundingBoxes(movedComponents);
            double xPlace = bb.getCenterX() - SCALE_X * 0.5 * unmovedPlaces.size();
            double yPlace = bb.getMinY() - 2.0 * SCALE_Y;
            for (VisualComponent component: unmovedPlaces) {
                Point2D pos = new Point2D.Double(xPlace, yPlace);
                component.setPosition(pos);
                xPlace += SCALE_X;
            }
            double xTransition = bb.getCenterX() - SCALE_X * 0.5 * unmovedTransition.size();
            double yTransition = bb.getMinY() - SCALE_Y;
            for (VisualComponent component: unmovedTransition) {
                Point2D pos = new Point2D.Double(xTransition, yTransition);
                component.setPosition(pos);
                xTransition += SCALE_X;
            }
        }
    }

    private Point2D getCenterRootSpacePosition(VisualContact signal) {
        double xPos = signal.getRootSpaceX();
        double yPos = signal.getRootSpaceY();
        if (!signal.isPort()) {
            Node parent = signal.getParent();
            if (parent instanceof VisualFunctionComponent) {
                VisualFunctionComponent component = (VisualFunctionComponent) parent;
                if (component.getVisualOutputs().size() == 1) {
                    xPos = component.getRootSpaceX();
                    yPos = component.getRootSpaceY();
                }
            }
        }
        return new Point2D.Double(SCALE_X * xPos, SCALE_Y * yPos);
    }

    private Point2D getDirectionOffset(VisualContact contact) {
        VisualContact.Direction direction = contact.getDirection();
        if (contact.isInput()) {
            direction = direction.flip();
        }
        switch (direction) {
        case WEST:
        case NORTH:
            return new Point2D.Double(6.0, 0.0);
        case EAST:
        case SOUTH:
            return new Point2D.Double(-6.0, 0.0);
        default:
            return new Point2D.Double(0.0, 0.0);
        }
    }

    private void groupDriverStgs(HashSet<VisualContact> drivers) {
        if (StgSettings.getGroupSignalConversion()) {
            for (VisualContact driver: drivers) {
                SignalStg signalStg = driverToStgMap.getValue(driver);
                if (signalStg != null) {
                    Collection<VisualNode> nodes = signalStg.getAllNodes();
                    Container container = Hierarchy.getNearestContainer(nodes);
                    stg.setCurrentLevel(container);
                    stg.select(nodes);
                    stg.groupSelection();
                }
            }
            stg.selectNone();
            stg.setCurrentLevel(stg.getRoot());
        }
    }

    private void cleanupPages() {
        NamespaceHelper.removeEmptyPages(stg);
    }

}
