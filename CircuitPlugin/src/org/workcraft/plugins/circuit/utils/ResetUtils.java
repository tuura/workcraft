package org.workcraft.plugins.circuit.utils;

import org.workcraft.dom.visual.BoundingBoxHelper;
import org.workcraft.dom.visual.MixUtils;
import org.workcraft.dom.visual.Touchable;
import org.workcraft.dom.visual.connections.VisualConnection;
import org.workcraft.exceptions.InvalidConnectionException;
import org.workcraft.formula.BooleanFormula;
import org.workcraft.formula.BooleanOperations;
import org.workcraft.formula.One;
import org.workcraft.formula.Zero;
import org.workcraft.plugins.circuit.*;
import org.workcraft.util.Hierarchy;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class ResetUtils {

    public static void insertReset(VisualCircuit circuit, String portName, boolean activeLow) {
        VisualFunctionContact resetPort = circuit.getOrCreateContact(null, portName, Contact.IOType.INPUT);
        for (VisualFunctionComponent component : circuit.getVisualFunctionComponents()) {
            boolean isSimpleGate = component.isGate() && (component.getVisualInputs().size() < 3);
            Collection<VisualFunctionContact> forceInitGateContacts = new HashSet<>();
            Collection<VisualFunctionContact> forceInitFuncContacts = new HashSet<>();
            for (VisualFunctionContact contact : component.getVisualFunctionContacts()) {
                if (contact.isOutput() && contact.isPin() && contact.getForcedInit()) {
                    if (isSimpleGate || contact.getReferencedFunctionContact().isSequential()) {
                        forceInitFuncContacts.add(contact);
                    } else {
                        forceInitGateContacts.add(contact);
                    }
                }
            }
            VisualContact resetContact = null;
            if (!forceInitFuncContacts.isEmpty()) {
                resetContact = circuit.getOrCreateContact(component, null, Contact.IOType.INPUT);
                try {
                    circuit.connect(resetPort, resetContact);
                } catch (InvalidConnectionException e) {
                    throw new RuntimeException(e);
                }
            }
            for (VisualFunctionContact contact : forceInitFuncContacts) {
                insertResetFunction(contact, resetContact, activeLow);
                component.setLabel("");
            }
            for (VisualFunctionContact contact : forceInitGateContacts) {
                insertResetGate(circuit, resetPort, contact, activeLow);
            }
        }
        forceInitResetCircuit(circuit, resetPort, activeLow);
        positionResetPort(circuit, resetPort);
    }

    private static void insertResetFunction(VisualFunctionContact contact, VisualContact resetContact, boolean activeLow) {
        BooleanFormula setFunction = contact.getSetFunction();
        BooleanFormula resetFunction = contact.getResetFunction();
        Contact resetVar = resetContact.getReferencedContact();
        if (activeLow) {
            if (contact.getInitToOne()) {
                if (setFunction != null) {
                    contact.setSetFunction(BooleanOperations.or(BooleanOperations.not(resetVar), setFunction));
                }
                if (resetFunction != null) {
                    contact.setResetFunction(BooleanOperations.and(resetVar, resetFunction));
                }
            } else {
                if (setFunction != null) {
                    contact.setSetFunction(BooleanOperations.and(resetVar, setFunction));
                }
                if (resetFunction != null) {
                    contact.setResetFunction(BooleanOperations.or(BooleanOperations.not(resetVar), resetFunction));
                }
            }
        } else {
            if (contact.getInitToOne()) {
                if (setFunction != null) {
                    contact.setSetFunction(BooleanOperations.or(resetVar, setFunction));
                }
                if (resetFunction != null) {
                    contact.setResetFunction(BooleanOperations.and(BooleanOperations.not(resetVar), resetFunction));
                }
            } else {
                if (setFunction != null) {
                    contact.setSetFunction(BooleanOperations.and(BooleanOperations.not(resetVar), setFunction));
                }
                if (resetFunction != null) {
                    contact.setResetFunction(BooleanOperations.or(resetVar, resetFunction));
                }
            }
        }
    }

    private static void insertResetGate(VisualCircuit circuit, VisualContact resetPort, VisualFunctionContact contact, boolean activeLow) {
        // Change connection scale mode to LOCK_RELATIVELY for cleaner relocation of components
        Collection<VisualConnection> connections = Hierarchy.getDescendantsOfType(circuit.getRoot(), VisualConnection.class);
        HashMap<VisualConnection, VisualConnection.ScaleMode> connectionToScaleModeMap
                = ConnectionUtils.replaceConnectionScaleMode(connections, VisualConnection.ScaleMode.LOCK_RELATIVELY);

        double gateSpace = 3.0;
        SpaceUtils.makeSpaceAfterContact(circuit, contact, gateSpace + 1.0);
        VisualJoint joint = CircuitUtils.detachJoint(circuit, contact);
        if (joint != null) {
            joint.setRootSpacePosition(getOffsetContactPosition(contact, gateSpace));
        }
        // Restore connection scale mode
        ConnectionUtils.restoreConnectionScaleMode(connectionToScaleModeMap);

        VisualFunctionComponent resetGate = createResetGate(circuit, contact.getInitToOne(), activeLow);
        GateUtils.insertGateAfter(circuit, resetGate, contact);
        connectHangingInputs(circuit, resetPort, resetGate);
        GateUtils.propagateInitialState(circuit, resetGate);
    }

    private static VisualFunctionComponent createResetGate(VisualCircuit circuit, boolean initToOne, boolean activeLow) {
        if (activeLow) {
            return initToOne ? GateUtils.createNandbGate(circuit) : GateUtils.createAndGate(circuit);
        } else {
            return initToOne ? GateUtils.createOrGate(circuit) : GateUtils.createNorbGate(circuit);
        }
    }

    private static Point2D getOffsetContactPosition(VisualContact contact, double space) {
        double d = contact.isPort() ? -space : space;
        double x = contact.getRootSpaceX() + d * contact.getDirection().getGradientX();
        double y = contact.getRootSpaceY() + d * contact.getDirection().getGradientY();
        return new Point2D.Double(x, y);
    }

    private static void connectHangingInputs(VisualCircuit circuit, VisualContact port, VisualFunctionComponent component) {
        for (VisualContact contact : component.getVisualInputs()) {
            if (!circuit.getPreset(contact).isEmpty()) continue;
            try {
                circuit.connect(port, contact);
            } catch (InvalidConnectionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void forceInitResetCircuit(VisualCircuit circuit, VisualFunctionContact resetPort, boolean activeLow) {
        resetPort.setInitToOne(!activeLow);
        resetPort.setForcedInit(true);
        resetPort.setSetFunction(activeLow ? One.instance() : Zero.instance());
        resetPort.setResetFunction(activeLow ? Zero.instance() : One.instance());
        for (VisualFunctionContact contact : circuit.getVisualFunctionContacts()) {
            if (contact.isPin() && contact.isOutput()) {
                contact.setForcedInit(false);
            }
        }
    }

    private static void positionResetPort(VisualCircuit circuit, VisualFunctionContact resetPort) {
        Collection<Touchable> nodes = new HashSet<>();
        nodes.addAll(Hierarchy.getChildrenOfType(circuit.getRoot(), VisualConnection.class));
        nodes.addAll(Hierarchy.getChildrenOfType(circuit.getRoot(), VisualCircuitComponent.class));
        nodes.addAll(Hierarchy.getChildrenOfType(circuit.getRoot(), VisualJoint.class));
        Rectangle2D modelBox = BoundingBoxHelper.mergeBoundingBoxes(nodes);

        Collection<VisualContact> driven = CircuitUtils.findDriven(circuit, resetPort, false);
        double y = driven.isEmpty() ? modelBox.getCenterY() : MixUtils.middleRootspacePosition(driven).getY();
        resetPort.setRootSpacePosition(new Point2D.Double(modelBox.getMinX(), y));

        VisualJoint joint = CircuitUtils.detachJoint(circuit, resetPort);
        if (joint != null) {
            joint.setRootSpacePosition(getOffsetContactPosition(resetPort, 0.5));
        }
    }

}
