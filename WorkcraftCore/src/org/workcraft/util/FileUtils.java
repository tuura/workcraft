/*
*
* Copyright 2008,2009 Newcastle University
*
* This file is part of Workcraft.
*
* Workcraft is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Workcraft is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Workcraft.  If not, see <http://www.gnu.org/licenses/>.
*
*/

package org.workcraft.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Scanner;

public class FileUtils {
    public static final String TEMP_DIRECTORY_PREFIX = "workcraft-";

    public static void copyFile(File in, File out) throws IOException {
        out.getParentFile().mkdirs();
        FileOutputStream outStream = new FileOutputStream(out);
        try {
            copyFileToStream(in, outStream);
        } finally {
            outStream.close();
        }
    }

    public static String getFileNameWithoutExtension(File file) {
        String name = file.getName();
        int k = name.lastIndexOf('.');
        if (k == -1) {
            return name;
        } else {
            return name.substring(0, k);
        }
    }

    public static void dumpString(File out, String string) throws IOException {
        FileOutputStream fos = new FileOutputStream(out);
        fos.write(string.getBytes());
        fos.close();
    }

    public static void copyFileToStream(File in, OutputStream out) throws IOException {
        FileInputStream is = new FileInputStream(in);
        FileChannel inChannel = is.getChannel();
        WritableByteChannel outChannel = Channels.newChannel(out);
        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (IOException e) {
            throw e;
        } finally {
            if (is != null) is.close();
            if (inChannel != null) inChannel.close();
            if (outChannel != null) outChannel.close();
        }
    }

    public static String getTempPrefix(String title) {
        String prefix = TEMP_DIRECTORY_PREFIX;
        if ((title != null) && !title.isEmpty()) {
            prefix = TEMP_DIRECTORY_PREFIX + title + "-";
        }
        return getCorrectTempPrefix(prefix);
    }

    private static String getCorrectTempPrefix(String prefix) {
        if (prefix == null) {
            prefix = "";
        }
        // Prefix must be without spaces (replace spaces with underscores).
        prefix = prefix.replaceAll("\\s", "_");
        // Prefix must be at least 3 symbols long (prepend short prefix with
        // underscores).
        while (prefix.length() < 3) {
            prefix = "_" + prefix;
        }
        return prefix;
    }

    public static File createTempFile(String prefix, String suffix) {
        return createTempFile(prefix, suffix, null);
    }

    public static File createTempFile(String prefix, String suffix, File directory) {
        File tempFile = null;
        prefix = getCorrectTempPrefix(prefix);
        String errorMessage = "Cannot create a temporary file with prefix '" + prefix + "'";
        if (suffix == null) {
            suffix = "";
        } else {
            errorMessage += " and suffix '" + suffix + "'.";
        }

        if (directory == null) {
            try {
                tempFile = File.createTempFile(prefix, suffix);
            } catch (IOException e) {
                throw new RuntimeException(errorMessage + ".");
            }
        } else {
            try {
                tempFile = File.createTempFile(prefix, suffix, directory);
            } catch (IOException e) {
                throw new RuntimeException(errorMessage + " under '" + directory.getAbsolutePath() + "' path.");
            }
        }
        tempFile.deleteOnExit();
        return tempFile;
    }

    public static File createTempDirectory(String prefix) {
        File tempDir = null;
        String errorMessage = "Cannot create a temporary directory with prefix '" + prefix + "'.";
        try {
            tempDir = File.createTempFile(getCorrectTempPrefix(prefix), "");
        } catch (IOException e) {
            throw new RuntimeException(errorMessage);
        }
        tempDir.delete();
        if (!tempDir.mkdir()) {
            throw new RuntimeException(errorMessage);
        }
        tempDir.deleteOnExit();
        return tempDir;
    }

    public static void copyAll(File source, File targetDir) throws IOException {
        if (!targetDir.isDirectory()) {
            throw new RuntimeException("Cannot copy files to a file that is not a directory.");
        }
        File target = new File(targetDir, source.getName());

        if (source.isDirectory()) {
            if (!target.mkdir()) {
                throw new RuntimeException("Cannot create directory " + target.getAbsolutePath());
            }
            for (File f : source.listFiles()) {
                copyAll(f, target);
            }
        } else {
            copyFile(source, target);
        }
    }

    public static void writeAllText(File file, String source) throws IOException {
        FileWriter writer = new FileWriter(file);
        writer.write(source);
        writer.close();
    }

    public static String readAllText(File file) throws IOException {
        InputStream stream = new FileInputStream(file);
        try {
            return readAllText(stream);
        } finally {
            stream.close();
        }
    }

    /**
     * Reads all text from the stream using the default charset. Does not close
     * the stream.
     */
    public static String readAllText(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder result = new StringBuilder();
        while (true) {
            String s = reader.readLine();
            if (s == null) {
                return result.toString();
            }
            result.append(s);
            result.append('\n');
        }
    }

    public static void moveFile(File from, File to) throws IOException {
        copyFile(from, to);
        from.delete();
    }

    public static byte[] readAllBytes(File in) throws IOException {
        ByteArrayOutputStream mem = new ByteArrayOutputStream();
        copyFileToStream(in, mem);
        return mem.toByteArray();
    }

    public static void writeAllBytes(byte[] bytes, File out) throws IOException {
        OutputStream stream = new FileOutputStream(out);
        stream.write(bytes);
        stream.close();
    }

    public static void appendAllText(File file, String text) throws IOException {
        FileWriter writer = new FileWriter(file, true);
        writer.write(text);
        writer.close();
    }

    public static String readAllTextFromSystemResource(String path) throws IOException {
        InputStream stream = ClassLoader.getSystemResourceAsStream(path);
        try {
            return readAllText(stream);
        } finally {
            stream.close();
        }
    }

    public static boolean fileContainsKeyword(File file, String keyword) {
        boolean result = false;
        Scanner scanner = null;
        try {
            scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.contains(keyword)) {
                    result = true;
                    break;
                }
            }
        } catch (FileNotFoundException e) {
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return result;
    }

    public static void deleteOnExitRecursively(File file) {
        if (file != null) {
            // Note that deleteOnExit() for a directory needs to be called BEFORE the deletion of its content.
            file.deleteOnExit();
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteOnExitRecursively(f);
                }
            }
        }
    }

}
