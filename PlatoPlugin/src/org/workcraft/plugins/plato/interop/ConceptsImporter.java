package org.workcraft.plugins.plato.interop;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.workcraft.dom.Model;
import org.workcraft.exceptions.DeserialisationException;
import org.workcraft.interop.Importer;
import org.workcraft.plugins.plato.exceptions.PlatoException;
import org.workcraft.plugins.plato.tasks.PlatoResultHandler;
import org.workcraft.plugins.plato.tasks.PlatoTask;
import org.workcraft.plugins.shared.tasks.ExternalProcessOutput;
import org.workcraft.plugins.stg.StgDescriptor;
import org.workcraft.plugins.stg.StgModel;
import org.workcraft.plugins.stg.interop.StgImporter;
import org.workcraft.tasks.Result;
import org.workcraft.tasks.Result.Outcome;
import org.workcraft.util.FileUtils;
import org.workcraft.workspace.ModelEntry;

public class ConceptsImporter implements Importer {

    @Override
    public ConceptsFormat getFormat() {
        return ConceptsFormat.getInstance();
    }

    @Override
    public ModelEntry importFrom(InputStream in) throws DeserialisationException, IOException {
        try {
            boolean system = FileUtils.containsKeyword(in, "system =");
            File file = FileUtils.createTempFile("plato-", ".hs");
            FileUtils.copyStreamToFile(in, file);
            PlatoTask task = new PlatoTask(file, new String[0], false, system);
            PlatoResultHandler monitor = new PlatoResultHandler(this);
            Result<? extends ExternalProcessOutput> result = task.run(monitor);
            if (result.getOutcome() == Outcome.SUCCESS) {
                String output = new String(result.getPayload().getStdout());
                if (output.startsWith(".model")) {
                    StgImporter importer = new StgImporter();
                    ByteArrayInputStream is = new ByteArrayInputStream(result.getPayload().getStdout());
                    StgModel stg = importer.importStg(is);
                    return new ModelEntry(new StgDescriptor(), (Model) stg);
                }
            }
            throw new PlatoException(result);
        } catch (PlatoException e) {
            e.handleConceptsError();
            throw new DeserialisationException();
        }
    }

}
