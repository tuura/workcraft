package org.workcraft.plugins.circuit.commands;

import org.workcraft.dom.Container;
import org.workcraft.dom.hierarchy.NamespaceHelper;
import org.workcraft.dom.visual.VisualComponent;
import org.workcraft.dom.visual.VisualNode;
import org.workcraft.dom.visual.connections.VisualConnection;
import org.workcraft.exceptions.InvalidConnectionException;
import org.workcraft.formula.BooleanFormula;
import org.workcraft.formula.BooleanVariable;
import org.workcraft.formula.Not;
import org.workcraft.formula.jj.ParseException;
import org.workcraft.formula.visitors.StringGenerator;
import org.workcraft.plugins.circuit.*;
import org.workcraft.plugins.circuit.naryformula.SplitForm;
import org.workcraft.plugins.circuit.naryformula.SplitFormGenerator;
import org.workcraft.plugins.circuit.utils.CircuitUtils;
import org.workcraft.plugins.circuit.utils.GateUtils;
import org.workcraft.types.Pair;
import org.workcraft.utils.LogUtils;

import java.awt.geom.Point2D;
import java.util.*;

public class SplitGateTransformationCommand extends AbstractGateTransformationCommand {

    private static class NodeConnectionPair extends Pair<VisualNode, VisualConnection> {
        NodeConnectionPair(VisualNode first, VisualConnection second) {
            super(first, second);
        }
    }

    @Override
    public String getDisplayName() {
        return "Split multi-level gates (selected or all)";
    }

    @Override
    public String getPopupName() {
        return "Split multi-level gate";
    }

    @Override
    public Position getPosition() {
        return Position.TOP_MIDDLE;
    }

    @Override
    public void transformGate(VisualCircuit circuit, VisualFunctionComponent gate) {
        VisualFunctionContact outputContact = gate.getGateOutput();

        BooleanFormula setFunction = outputContact.getSetFunction();
        String str = CircuitUtils.gateToString(circuit, gate);
        if (setFunction == null) {
            LogUtils.logWarning("Gate " + str + " cannot be split as it does not have set functions defined");
            return;
        }

        BooleanFormula resetFunction = outputContact.getResetFunction();
        if ((setFunction != null) && (resetFunction != null)) {
            LogUtils.logWarning("Gate " + str + " cannot be split as it has both set and reset functions defined");
            return;
        }

        SplitForm functions = SplitFormGenerator.generate(setFunction);
        if (functions.getClauses().stream().filter(f -> !(f instanceof BooleanVariable)).count() < 2) {
            LogUtils.logWarning("Gate " + str + " cannot be split as it is too simple");
            return;
        }

        LogUtils.logInfo("Splitting multi-level gate " + str + " into:");
        splitComplexGate(circuit, gate, functions);
    }

    private static void splitComplexGate(VisualCircuit circuit, VisualFunctionComponent complexGate, SplitForm functions) {
        List<NodeConnectionPair> fromNodeConnections = getComponentDriverNodes(circuit, complexGate);
        Set<NodeConnectionPair> toNodeConnections = getComponentNonLoopDrivenNodes(circuit, complexGate);
        Container container = (Container) complexGate.getParent();
        VisualFunctionContact complexOutputContact = complexGate.getGateOutput();
        circuit.remove(complexGate);

        Stack<Set<NodeConnectionPair>> toNodeConnectionsStack = new Stack<>();
        toNodeConnectionsStack.push(toNodeConnections);

        Iterator<NodeConnectionPair> fromNodeConnectionIterator = fromNodeConnections.iterator();
        boolean isRootGate = true;
        VisualContact.Direction direction = complexOutputContact.getDirection();
        LinkedList<VisualFunctionComponent> nonRootGates = new LinkedList<>();
        for (BooleanFormula function : functions.getClauses()) {
            if (function instanceof BooleanVariable) {
                connectTerminal(circuit, fromNodeConnectionIterator, toNodeConnectionsStack);
            } else {
                VisualFunctionComponent simpleGate = insertGate(circuit, function, container, toNodeConnectionsStack, direction);
                simpleGate.copyStyle(complexGate);
                simpleGate.clearMapping();
                if (!isRootGate) {
                    nonRootGates.push(simpleGate);
                } else {
                    double x = complexGate.getX() + direction.getGradientX();
                    double y = complexGate.getY() + direction.getGradientY();
                    simpleGate.setPosition(new Point2D.Double(x, y));
                    VisualFunctionContact simpleOutputContact = simpleGate.getGateOutput();
                    // Update fromNodes for self-loops
                    fromNodeConnections.replaceAll(pair -> (pair.getFirst() == complexOutputContact)
                            ? new NodeConnectionPair(simpleOutputContact, pair.getSecond()) : pair);
                    simpleOutputContact.copyStyle(complexOutputContact);
                    isRootGate = false;
                }
                circuit.addToSelection(simpleGate);
            }
        }
        propagateInitValues(circuit, nonRootGates);
    }

    private static void connectTerminal(VisualCircuit circuit, Iterator<NodeConnectionPair> fromNodeConnectionIterator,
            Stack<Set<NodeConnectionPair>> toNodeConnectionsStack) {

        NodeConnectionPair fromNodeConnection = fromNodeConnectionIterator.next();
        if (!toNodeConnectionsStack.isEmpty()) {
            Set<NodeConnectionPair> toNodeConnections = toNodeConnectionsStack.pop();
            connectFanoutCopyFrom(circuit, fromNodeConnection, toNodeConnections);
        }
    }

    private static VisualFunctionComponent insertGate(VisualCircuit circuit, BooleanFormula function, Container container,
            Stack<Set<NodeConnectionPair>> toNodeConnectionsStack, VisualContact.Direction direction) {

        FunctionComponent mathGate = new FunctionComponent();
        Container mathContainer = NamespaceHelper.getMathContainer(circuit, container);
        mathContainer.add(mathGate);

        VisualFunctionComponent gate = circuit.createVisualComponent(mathGate, VisualFunctionComponent.class, container);
        VisualFunctionContact outputContact = createGateOutput(circuit, gate, function);
        LogUtils.logInfo("  - " + CircuitUtils.gateToString(circuit, gate));

        outputContact.setDirection(direction);
        if (!toNodeConnectionsStack.isEmpty()) {
            Set<NodeConnectionPair> toNodeConnections = toNodeConnectionsStack.pop();
            connectFanout(circuit, outputContact, toNodeConnections);
            if (toNodeConnections.size() == 1) {
                NodeConnectionPair nodeConnection = toNodeConnections.iterator().next();
                VisualComponent toNode = (VisualComponent) nodeConnection.getFirst();
                double x = toNode.getRootSpaceX() - direction.getGradientX();
                double y = toNode.getRootSpaceY() - direction.getGradientY();
                gate.setRootSpacePosition(new Point2D.Double(x, y));
            }
        }

        List<VisualContact> inputContacts = gate.getVisualInputs();
        Collections.reverse(inputContacts);
        for (VisualContact inputContact : inputContacts) {
            Set<NodeConnectionPair> toNodes = new HashSet<>();
            toNodes.add(new NodeConnectionPair(inputContact, null));
            toNodeConnectionsStack.push(toNodes);
        }
        return gate;
    }

    private static List<NodeConnectionPair> getComponentDriverNodes(VisualCircuit circuit, VisualFunctionComponent component) {
        List<NodeConnectionPair> result = new LinkedList<>();
        for (VisualContact inputContact : GateUtils.getOrderedInputs(component)) {
            VisualNode driver = null;
            VisualConnection visualConnection = null;
            for (VisualConnection connection : circuit.getConnections(inputContact)) {
                driver = connection.getFirst();
                visualConnection = connection;
                break;
            }
            result.add(new NodeConnectionPair(driver, visualConnection));
        }
        return result;
    }

    private static Set<NodeConnectionPair> getComponentNonLoopDrivenNodes(VisualCircuit circuit, VisualFunctionComponent component) {
        Set<NodeConnectionPair> result = new HashSet<>();
        for (VisualContact outputContact : component.getVisualOutputs()) {
            for (VisualConnection connection : circuit.getConnections(outputContact)) {
                if (!CircuitUtils.isSelfLoop(connection)) {
                    result.add(new NodeConnectionPair(connection.getSecond(), connection));
                }
            }
        }
        return result;
    }

    private static VisualFunctionContact createGateOutput(VisualCircuit circuit, VisualFunctionComponent component,
            BooleanFormula function) {

        String outputName = (function instanceof Not) ? "ON" : "O";
        VisualFunctionContact outputContact = circuit.getOrCreateContact(component, outputName, Contact.IOType.OUTPUT);
        try {
            String formulaString = StringGenerator.toString(function);
            BooleanFormula setFuncton = CircuitUtils.parsePinFuncton(circuit, component, formulaString);
            outputContact.setSetFunction(setFuncton);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return outputContact;
    }

    private static void connectFanoutCopyFrom(VisualCircuit circuit, NodeConnectionPair fromNodeConnection,
            Set<NodeConnectionPair> toNodeConnections) {

        for (NodeConnectionPair toNodeConnection : toNodeConnections) {
            if ((fromNodeConnection != null) && (toNodeConnection != null)) {
                try {
                    VisualNode fromNode = fromNodeConnection.getFirst();
                    VisualNode toNode = toNodeConnection.getFirst();
                    VisualConnection connection = circuit.connect(fromNode, toNode);
                    VisualConnection fromConnection = fromNodeConnection.getSecond();
                    connection.copyShape(fromConnection);
                    connection.copyStyle(fromConnection);
                } catch (InvalidConnectionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static void connectFanout(VisualCircuit circuit, VisualNode fromNode, Set<NodeConnectionPair> toNodeConnections) {
        for (NodeConnectionPair toNodeConnection: toNodeConnections) {
            if ((fromNode != null) && (toNodeConnection != null)) {
                try {
                    VisualNode toNode = toNodeConnection.getFirst();
                    VisualConnection toConnection = toNodeConnection.getSecond();
                    VisualConnection connection = circuit.connect(fromNode, toNode);
                    connection.copyShape(toConnection);
                    connection.copyStyle(toConnection);
                } catch (InvalidConnectionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static void propagateInitValues(VisualCircuit circuit, LinkedList<VisualFunctionComponent> gates) {
        for (VisualFunctionComponent gate: gates) {
            GateUtils.propagateInitialState(circuit, gate);
        }
    }

}