/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.command.ext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import liquibase.changelog.ChangeLogChild;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.serializer.core.xml.XMLChangeLogSerializer;

import net.example.liquibase.command.ext.ChangeLogAttributesFilter.ChangeSetKey;

/**
 * Implements full serialization of {@code DatabaseChangeLog} breaking
 * it to multiple files, preserving/setting {@code logicalFilePath} as
 * necessary.
 * <p>
 * As of this writing there's an empty implementation
 * {@link XMLChangeLogSerializer#serialize(DatabaseChangeLog)}
 * which interface doesn't suggest it could do the same.</p>
 * <p>
 * There is also a {@link liquibase.sdk.convert.ConvertCommand} which
 * appears to do a <i>poor-man's</i> conversion without preserving
 * {@code logicalFilePath}s.</p>
 */
public class EnhancedXMLChangeLogSerializer {

    private Path targetPath;
    private XMLChangeLogSerializer changeLogSerializer;
    private ChangeLogAttributesFilter changeLogAttributes;

    public EnhancedXMLChangeLogSerializer() {
        changeLogSerializer = new XMLChangeLogSerializer();
        changeLogAttributes = new ChangeLogAttributesFilter();
    }

    //public void serializeSingle(DatabaseChangeLog changeLog, String targetDir)
    //        throws IOException
    //{
    //    this.targetPath = Paths.get(targetDir).toAbsolutePath();
    //
    //    List<ChangeLogChild> content = new ArrayList<>();
    //    //ChangeLogContent.addDeclaredProperties(changeLog, content);
    //    content.add(changeLog.getPreconditions());
    //    Map<ChangeSetKey, String> logicalFileMap = new HashMap<>();
    //    String baseFile = changeLog.getLogicalFilePath();
    //    for (ChangeSet changeSet : changeLog.getChangeSets()) {
    //        if (!baseFile.equals(changeSet.getFilePath())) {
    //            logicalFileMap.put(ChangeSetKey.get(changeSet), changeSet.getFilePath());
    //        }
    //        content.add(changeSet);
    //    }
    //    writeChangeLog(changeLog, content, logicalFileMap);
    //}

    /**
     * Serializes the given change log to XML format writing multiple
     * files as necessary to preserve the {@code logicalFilePath}s of
     * the source change logs.
     *
     * @param   changeLog  the change log to serialize;
     * @param   targetDir  the base directory output files will be written to.
     * @throws  IOException  if I/O error occurs.
     */
    public void serialize(DatabaseChangeLog changeLog, String targetDir)
            throws IOException
    {
        serialize(changeLog, targetDir, false);
    }

    public void serialize(DatabaseChangeLog changeLog, String targetDir, boolean singleFile)
            throws IOException
    {
        this.targetPath = Paths.get(targetDir).toAbsolutePath();

        ChangeLogContent structure = new ChangeLogContent(changeLog, singleFile);
        Map<ChangeSetKey, String> logicalFileMap = structure.getLogicalFileMap();
        for (DatabaseChangeLog log : structure.getChangeLogs()) {
            writeChangeLog(log, structure.getContent(log), logicalFileMap);
        }
    }

    private void writeChangeLog(DatabaseChangeLog changeLog,
                                List<ChangeLogChild> content,
                                Map<ChangeSetKey, String> logicalFileMap)
            throws IOException
    {
        @SuppressWarnings("resource")
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        changeLogSerializer.write(content, buf);
        Path changeLogFile = targetPath.resolve(xmlExt(changeLog.getPhysicalFilePath()));
        Files.createDirectories(changeLogFile.getParent());
        try (OutputStream out = Files.newOutputStream(changeLogFile)) {
            changeLogAttributes.setLogicalFilePaths(logicalFileMap);
            changeLogAttributes.apply(changeLog, buf.toInputStream(), out);
        }
    }

    static String xmlExt(String path) {
        Path result = Paths.get(path);
        // Ensure path relative to the target base.
        if (result.isAbsolute()) {
            result = result.subpath(0, result.getNameCount());
        }
        String fileName = result.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        fileName = (dotIndex > 0) ? fileName.substring(0, dotIndex) + ".xml"
                                  : fileName + ".xml";
        return result.resolveSibling(fileName).toString();
    }


    private static class ByteArrayOutputStream extends java.io.ByteArrayOutputStream {
        ByteArrayOutputStream() {
            super(8 * 1024);
        }
        ByteArrayInputStream toInputStream() {
            return new ByteArrayInputStream(buf, 0, count);
        }
    }


}
