package org.workcraft.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.workcraft.PluginProvider;
import org.workcraft.exceptions.DeserialisationException;
import org.workcraft.interop.Format;
import org.workcraft.interop.FormatFileFilter;
import org.workcraft.interop.Importer;
import org.workcraft.plugins.PluginInfo;
import org.workcraft.workspace.ModelEntry;

public class ImportUtils {

    public static Importer chooseBestImporter(PluginProvider provider, File file) {
        for (PluginInfo<? extends Importer> info : provider.getPlugins(Importer.class)) {
            Importer importer = info.getSingleton();
            Format format = importer.getFormat();
            if (FormatFileFilter.checkFileFormat(file, format)) {
                return importer;
            }
        }
        return null;
    }

    public static ModelEntry importFromFile(Importer importer, File file)
            throws IOException, DeserialisationException {

        FileInputStream fileInputStream = new FileInputStream(file);
        ModelEntry model = importer.importFrom(fileInputStream);
        fileInputStream.close();
        return model;
    }

    public static ModelEntry importFromByteArray(Importer importer, byte[] array)
            throws IOException, DeserialisationException {

        ByteArrayInputStream inputStream = new ByteArrayInputStream(array);
        ModelEntry model = importer.importFrom(inputStream);
        inputStream.close();
        return model;
    }

}
