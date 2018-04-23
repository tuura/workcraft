package org.workcraft.plugins.dfs.serialisation;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.workcraft.Info;
import org.workcraft.dom.Model;
import org.workcraft.dom.Node;
import org.workcraft.exceptions.ArgumentException;
import org.workcraft.plugins.dfs.ControlRegister;
import org.workcraft.plugins.dfs.CounterflowRegister;
import org.workcraft.plugins.dfs.Dfs;
import org.workcraft.plugins.dfs.PopRegister;
import org.workcraft.plugins.dfs.PushRegister;
import org.workcraft.plugins.dfs.Register;
import org.workcraft.plugins.dfs.interop.VerilogFormat;
import org.workcraft.serialisation.ModelSerialiser;
import org.workcraft.serialisation.ReferenceProducer;
import org.workcraft.util.LogUtils;
import org.workcraft.util.Pair;

public class VerilogSerialiser implements ModelSerialiser {

    private static final String KEYWORD_OUTPUT = "output";
    private static final String KEYWORD_INPUT = "input";
    private static final String KEYWORD_MODULE = "module";
    private static final String KEYWORD_ENDMODULE = "endmodule";

    private static final String SEPARATOR = "_";
    private static final String NAME_OUT = "OUT";
    private static final String NAME_IN = "IN";
    private static final String NAME_RI = "RI";
    private static final String NAME_AI = "AI";
    private static final String NAME_RO = "RO";
    private static final String NAME_AO = "AO";
    private static final String NAME_BUFFER = "BUFFER";
    private static final String PREFIX_CELEMENT = "CELEMENT" + SEPARATOR;
    private static final String PREFIX_INST = "INST_";
    private static final String PREFIX_WIRE = "WIRE_";
    private static final String PREFIX_OUT = NAME_OUT + SEPARATOR;
    private static final String PREFIX_IN = NAME_IN + SEPARATOR;
    private static final String PREFIX_RI = NAME_RI + SEPARATOR;
    private static final String PREFIX_AI = NAME_AI + SEPARATOR;
    private static final String PREFIX_RO = NAME_RO + SEPARATOR;
    private static final String PREFIX_AO = NAME_AO + SEPARATOR;

    @Override
    public ReferenceProducer serialise(Model model, OutputStream out, ReferenceProducer refs) {
        if (model instanceof Dfs) {
            PrintWriter writer = new PrintWriter(out);
            writer.write(Info.getGeneratedByText("// Verilog netlist ", "\n"));
            writeModule(writer, (Dfs) model);
            writer.close();
        } else {
            throw new ArgumentException("Model class not supported: " + model.getClass().getName());
        }
        return refs;
    }

    @Override
    public boolean isApplicableTo(Model model) {
        return model instanceof Dfs;
    }

    @Override
    public UUID getFormatUUID() {
        return VerilogFormat.getInstance().getUuid();
    }

    private void writeModule(PrintWriter out, Dfs dfs) {
        writeHeader(out, dfs);
        writeInstances(out, dfs);
        out.write(KEYWORD_ENDMODULE + "\n");
    }

    private void writeHeader(PrintWriter out, Dfs dfs) {
        String topName = dfs.getTitle();
        if ((topName == null) || topName.isEmpty()) {
            topName = "UNTITLED";
            LogUtils.logWarning("The top module does not have a name. Exporting as '" + topName + "' module.");
        }
        ArrayList<Pair<String, Boolean>> ports = new ArrayList<>();
        for (Node node: dfs.getAllNodes()) {
            String ref = dfs.getNodeReference(node);
            if (dfs.getPreset(node).isEmpty()) {
                ports.add(new Pair<>(PREFIX_IN + ref, false));
            }
            if (dfs.getPostset(node).isEmpty()) {
                ports.add(new Pair<>(PREFIX_OUT + ref, true));
            }
        }
        for (Node node: dfs.getAllRegisters()) {
            String ref = dfs.getNodeReference(node);
            if (dfs.getRPreset(node).isEmpty()) {
                ports.add(new Pair<>(PREFIX_RI + ref, false));
                ports.add(new Pair<>(PREFIX_AI + ref, true));
            }
            if (dfs.getRPostset(node).isEmpty()) {
                ports.add(new Pair<>(PREFIX_RO + ref, true));
                ports.add(new Pair<>(PREFIX_AO + ref, false));
            }
        }
        out.write(KEYWORD_MODULE + " " + topName + " (\n");
        boolean isFirstPort = true;
        for (Pair<String, Boolean> port: ports) {
            if (!isFirstPort) {
                out.write(",");
            }
            out.write("\n");
            out.write("    " + port.getFirst());
            isFirstPort = false;
        }
        out.write(" );\n");
        out.write("\n");
        for (Pair<String, Boolean> port: ports) {
            out.write("    ");
            if (port.getSecond()) {
                out.write(KEYWORD_OUTPUT);
            } else {
                out.write(KEYWORD_INPUT);
            }
            out.write(" " + port.getFirst() + ";\n");
        }
        out.write("\n");
    }

    private void writeInstances(PrintWriter out, Dfs dfs) {
        HashSet<Node> logicNodes = new HashSet<>();
        logicNodes.addAll(dfs.getLogics());
        logicNodes.addAll(dfs.getCounterflowLogics());
        for (Node node: logicNodes) {
            writeInstance(out, dfs, node);
        }
        for (Node node: dfs.getAllRegisters()) {
            writeInstance(out, dfs, node);
            writeCelementPred(out, dfs, node);
            writeCelementSucc(out, dfs, node);
        }
    }

    private void writeInstance(PrintWriter out, Dfs dfs, Node node) {
        Set<Node> preset = dfs.getPreset(node);
        Set<Node> postset = dfs.getPostset(node);
        String ref = dfs.getNodeReference(node);
        String instanceName = PREFIX_INST + ref;
        String className = node.getClass().getSimpleName().toUpperCase();
        int inCount = preset.isEmpty() ? 1 : preset.size();
        int outCount = postset.isEmpty() ? 1 : postset.size();
        String moduleName = className + SEPARATOR + inCount + SEPARATOR + outCount;
        out.write("    " + moduleName + " " + instanceName + " (");
        boolean isFirstContact = true;
        int inIndex = 0;
        if (preset.isEmpty()) {
            String inWireName = PREFIX_IN + ref;
            String inContactName = NAME_IN + inIndex++;
            writeContact(out, inContactName, inWireName, isFirstContact);
            isFirstContact = false;
        }
        for (Node predNode: preset) {
            String predRef = dfs.getNodeReference(predNode);
            String inWireName = PREFIX_WIRE + predRef + SEPARATOR + ref;
            String inContactName = NAME_IN + inIndex++;
            writeContact(out, inContactName, inWireName, isFirstContact);
            isFirstContact = false;
        }
        int outIndex = 0;
        if (postset.isEmpty()) {
            String outWireName = PREFIX_OUT + ref;
            String outContactName = NAME_OUT + outIndex++;
            writeContact(out, outContactName, outWireName, isFirstContact);
            isFirstContact = false;
        }
        for (Node succNode: postset) {
            String succRef = dfs.getNodeReference(succNode);
            String outWireName = PREFIX_WIRE + ref + SEPARATOR + succRef;
            String outContactName = NAME_OUT + outIndex++;
            writeContact(out, outContactName, outWireName, isFirstContact);
            isFirstContact = false;
        }
        if ((node instanceof Register)
                || (node instanceof CounterflowRegister)
                || (node instanceof ControlRegister)
                || (node instanceof PushRegister)
                || (node instanceof PopRegister)) {
            writeContact(out, NAME_RI, PREFIX_WIRE + PREFIX_RI + ref, isFirstContact);
            writeContact(out, NAME_AI, PREFIX_WIRE + PREFIX_AI + ref, isFirstContact);
            writeContact(out, NAME_RO, PREFIX_WIRE + PREFIX_RO + ref, isFirstContact);
            writeContact(out, NAME_AO, PREFIX_WIRE + PREFIX_AO + ref, isFirstContact);
            isFirstContact = false;
        }
        out.write(");\n");
    }

    private void writeContact(PrintWriter out, String contactName, String wireName, boolean isFirstContact) {
        if (!isFirstContact) {
            out.write(", ");
        }
        out.write("." + contactName + "(" + wireName + ")");
    }

    private void writeCelementPred(PrintWriter out, Dfs dfs, Node node) {
        ArrayList<String> inWireNames = new ArrayList<>();
        Set<Node> rPreset = dfs.getRPreset(node);
        if (rPreset.isEmpty()) {
            String ref = dfs.getNodeReference(node);
            String inWireName = PREFIX_RI + ref;
            inWireNames.add(inWireName);
        }
        for (Node predNode: rPreset) {
            String predRef = dfs.getNodeReference(predNode);
            String inWireName = PREFIX_WIRE + PREFIX_RO + predRef;
            inWireNames.add(inWireName);
        }
        String ref = dfs.getNodeReference(node);
        String instanceName = PREFIX_INST + PREFIX_RI + ref;
        String outWireName = PREFIX_WIRE + PREFIX_RI + ref;
        writeCelement(out, instanceName, inWireNames, outWireName);
    }

    private void writeCelementSucc(PrintWriter out, Dfs dfs, Node node) {
        ArrayList<String> inWireNames = new ArrayList<>();
        Set<Node> rPostset = dfs.getRPostset(node);
        if (rPostset.isEmpty()) {
            String ref = dfs.getNodeReference(node);
            String inWireName = PREFIX_AO + ref;
            inWireNames.add(inWireName);
        }
        for (Node succNode: rPostset) {
            String succRef = dfs.getNodeReference(succNode);
            String inWireName = PREFIX_WIRE + PREFIX_AI + succRef;
            inWireNames.add(inWireName);
        }
        String ref = dfs.getNodeReference(node);
        String instanceName = PREFIX_INST + PREFIX_AO + ref;
        String outWireName = PREFIX_WIRE + PREFIX_AO + ref;
        writeCelement(out, instanceName, inWireNames, outWireName);
    }

    private void writeCelement(PrintWriter out, String instanceName, ArrayList<String> inWireNames, String outWireName) {
        int inCount = inWireNames.size();
        if (inCount == 1) {
            out.write("    " + NAME_BUFFER + " " + instanceName + " (");
            String inWireName = inWireNames.get(0);
            writeContact(out, NAME_IN, inWireName, true);
            writeContact(out, NAME_OUT, outWireName, false);
            out.write(");\n");
        } else if (inCount > 1) {
            String moduleName = PREFIX_CELEMENT + inCount;
            out.write("    " + moduleName + " " + instanceName + " (");
            boolean isFirstContact = true;
            int inIndex = 0;
            for (String inWireName: inWireNames) {
                String inContactName = NAME_IN + inIndex++;
                writeContact(out, inContactName, inWireName, isFirstContact);
                isFirstContact = false;
            }
            writeContact(out, NAME_OUT, outWireName, isFirstContact);
            out.write(");\n");
        }
    }

}
