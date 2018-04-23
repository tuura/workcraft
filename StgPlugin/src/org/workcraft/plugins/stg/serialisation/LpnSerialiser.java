package org.workcraft.plugins.stg.serialisation;

import java.io.OutputStream;
import java.util.UUID;

import org.workcraft.dom.Model;
import org.workcraft.plugins.petri.PetriNetModel;
import org.workcraft.plugins.stg.StgModel;
import org.workcraft.plugins.stg.interop.LpnFormat;
import org.workcraft.plugins.stg.serialisation.SerialiserUtils.Style;
import org.workcraft.serialisation.ModelSerialiser;
import org.workcraft.serialisation.ReferenceProducer;

public class LpnSerialiser implements ModelSerialiser {

    @Override
    public ReferenceProducer serialise(Model model, OutputStream out, ReferenceProducer refs) {
        SerialiserUtils.writeModel(model, out, Style.LPN);
        return refs;
    }

    @Override
    public boolean isApplicableTo(Model model) {
        return (model instanceof StgModel) || (model instanceof PetriNetModel);
    }

    @Override
    public UUID getFormatUUID() {
        return LpnFormat.getInstance().getUuid();
    }

}
