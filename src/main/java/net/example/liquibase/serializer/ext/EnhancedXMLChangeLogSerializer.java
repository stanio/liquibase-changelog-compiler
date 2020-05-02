/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.serializer.ext;

import static javax.xml.XMLConstants.DEFAULT_NS_PREFIX;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
import static liquibase.serializer.LiquibaseSerializable.STANDARD_CHANGELOG_NAMESPACE;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;

import org.xml.sax.SAXException;

import liquibase.ContextExpression;
import liquibase.change.Change;
import liquibase.change.core.CreateProcedureChange;
import liquibase.change.core.CreateViewChange;
import liquibase.change.core.LoadDataChange;
import liquibase.change.core.SQLFileChange;
import liquibase.changelog.ChangeLogChild;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.parser.NamespaceDetails;
import liquibase.parser.NamespaceDetailsFactory;
import liquibase.serializer.LiquibaseSerializable;
import liquibase.serializer.LiquibaseSerializable.SerializationType;
import liquibase.serializer.core.xml.XMLChangeLogSerializer;
import net.example.liquibase.serializer.ext.util.SimpleXmlWriter;

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
public class EnhancedXMLChangeLogSerializer extends XMLChangeLogSerializer {

    private static boolean debugBase = true;

    private String currentLogicalPath;
    private DatabaseChangeLog currentChangeLog;
    //private String currentPhysicalBase;
    private SimpleXmlWriter xmlOut = new SimpleXmlWriter();
    private String currentElement;

    public EnhancedXMLChangeLogSerializer() {
        super((org.w3c.dom.Document) null);
    }

    @Override
    public int getPriority() {
        return super.getPriority() + 1;
    }

    @Override
    public <T extends ChangeLogChild> void write(List<T> children, OutputStream out)
            throws IOException
    {
        try {
            currentChangeLog = null;
            currentLogicalPath = null;
            xmlOut.setUpWrite(out);
            writeChangeLog(null, children);
        } catch (SAXException e) {
            throw ioExceptionFor(e);
        }
    }

    @Override
    public String serialize(LiquibaseSerializable object, boolean pretty) {
        StringWriter buf = new StringWriter();
        try {
            currentChangeLog = null;
            currentLogicalPath = null;
            xmlOut.setUpWrite(buf, pretty, false);
            declareNamespacePrefixes();
            writeObject(object);
            xmlOut.writeEndDocument();
        } catch (SAXException e) {
            throw new UnexpectedLiquibaseException(e);
        }
        return buf.toString();
    }

    @Override
    public String serialize(DatabaseChangeLog databaseChangeLog) {
        StringWriter buf = new StringWriter();
        try {
            ChangeLogContent structure = new ChangeLogContent(databaseChangeLog, true);
            for (DatabaseChangeLog log : structure.getChangeLogs()) {
                currentChangeLog = log;
                currentLogicalPath = databaseChangeLog.getLogicalFilePath();
                xmlOut.setUpWrite(buf);
                writeChangeLog(log, structure.getContent(log));
            }
        } catch (IOException | SAXException e) {
            throw new UnexpectedLiquibaseException(e);
        }
        return buf.toString();
    }

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
        Path targetPath = Paths.get(targetDir).toAbsolutePath();
        ChangeLogContent structure = new ChangeLogContent(changeLog, singleFile);
        for (DatabaseChangeLog log : structure.getChangeLogs()) {
            currentChangeLog = log;
            currentLogicalPath = xmlExt(log.getPhysicalFilePath());
            Path changeLogFile = targetPath.resolve(currentLogicalPath);
            Files.createDirectories(changeLogFile.getParent());
            try (OutputStream out = Files.newOutputStream(changeLogFile)) {
                currentLogicalPath = currentLogicalPath.replace('\\', '/');
                xmlOut.setUpWrite(out);
                writeChangeLog(log, structure.getContent(log));
            } catch (SAXException e) {
                throw ioExceptionFor(e);
            }

            if (debugBase) {
                Path standardLogFile = changeLogFile
                        .resolveSibling("base-" + changeLogFile.getFileName());
                try (OutputStream out = Files.newOutputStream(standardLogFile)) {
                    super.write(structure.getContent(log), out);
                }
            }
        }
    }

    private static IOException ioExceptionFor(SAXException e) {
        return (e.getCause() instanceof IOException)
                ? (IOException) e.getCause()
                : new IOException(e);
    }

    private void writeChangeLog(DatabaseChangeLog changeLog,
                                List<? extends ChangeLogChild> content)
            throws IOException, SAXException
    {
        xmlOut.writeStartDocument();
        xmlOut.writeStartElement("databaseChangeLog");
        writeChangeLogAttributes(changeLog);
        for (ChangeLogChild child : content) {
            writeObject(child);
        }
        xmlOut.writeEndElement();
        xmlOut.writeEndDocument();
    }

    private String declareNamespacePrefixes() {
        xmlOut.setPrefix(DEFAULT_NS_PREFIX, STANDARD_CHANGELOG_NAMESPACE);

        StringBuilder schemaLocations = new StringBuilder(500);
        for (NamespaceDetails details : NamespaceDetailsFactory.getInstance().getNamespaceDetails()) {
            for (String namespace : details.getNamespaces()) {
                if (details.getPriority() > 0 && details.supports(this, namespace)) {
                    String shortName = details.getShortName(namespace);
                    String schemaUrl = details.getSchemaUrl(namespace);
                    if (shortName != null && !shortName.isEmpty()) {
                        xmlOut.setPrefix(shortName, namespace);
                    }
                    if (schemaUrl != null) {
                        schemaLocations.append(namespace)
                                .append(' ').append(schemaUrl).append(' ');
                    }
                }
            }
        }
        return schemaLocations.toString().trim();
    }

    private void writeChangeLogAttributes(DatabaseChangeLog changeLog) {
        if (changeLog != null) {
            if (currentLogicalPath != null && !currentLogicalPath.equals(changeLog.getLogicalFilePath())) {
                xmlOut.writeAttribute("logicalFilePath", changeLog.getLogicalFilePath());
                currentLogicalPath = changeLog.getLogicalFilePath();
            }
            ContextExpression contexts = changeLog.getContexts();
            if (contexts != null && !contexts.isEmpty()) {
                xmlOut.writeAttribute("context", contexts.toString());
            }
            if (changeLog.getObjectQuotingStrategy() != null
                    && changeLog.getObjectQuotingStrategy() != ObjectQuotingStrategy.LEGACY) {
                xmlOut.writeAttribute("objectQuotingStrategy",
                        changeLog.getObjectQuotingStrategy().toString());
            }
        }
        String schemaLocations = declareNamespacePrefixes();
        if (schemaLocations.length() > 0) {
            xmlOut.setPrefix("xsi", W3C_XML_SCHEMA_INSTANCE_NS_URI);
            xmlOut.writeAttribute("xsi:schemaLocation", schemaLocations);
        }
    }

    private Set<String> writeSerializableAttributes(LiquibaseSerializable object) {
        Set<String> remaining = new LinkedHashSet<>();
        for (String field : object.getSerializableFields()) {
            Object value = object.getSerializableFieldValue(field);
            if (value == null
                    || value instanceof Collection
                    || value instanceof Map
                    || value instanceof LiquibaseSerializable
                    || value instanceof Object[]) {
                remaining.add(field);
                continue;
            }

            SerializationType type = object.getSerializableFieldType(field);
            if (type != SerializationType.NAMED_FIELD) {
                remaining.add(field);
                continue;
            }

            if (writeFilePathAttribute(object, field)
                    || writeChangeSetAttribute(object, field)) {
                continue;
            }
            writeAttribute(object.getSerializableFieldNamespace(field),
                           field, value, object.getSerializedObjectNamespace());
        }
        if (object instanceof ChangeSet) {
            Object changeSetLogicalPath = null;
            if (object.getSerializableFields().contains("logicalFilePath")) {
                changeSetLogicalPath = object.getSerializableFieldValue("logicalFilePath");
            }
            ChangeSet changeSet = (ChangeSet) object;
            if (changeSetLogicalPath == null
                    && currentLogicalPath != null
                    && !currentLogicalPath.equals(changeSet.getFilePath())) {
                xmlOut.writeAttribute("logicalFilePath", changeSet.getFilePath());
            }
        }
        return remaining;
    }

    private boolean writeChangeSetAttribute(LiquibaseSerializable object, String field) {
        if (!(object instanceof ChangeSet)) {
            return false;
        }
        ChangeSet changetSet = (ChangeSet) object;
        if (field.equals("context")
                && currentChangeLog != null
                && changetSet.getChangeLog() != currentChangeLog
                && changetSet.getChangeLog() != null) {
            List<ContextExpression> contexts = new ArrayList<>();
            addContexts(changetSet.getContexts(), contexts);
            DatabaseChangeLog currentLog = changetSet.getChangeLog();
            while (currentLog != null) {
                addContexts(currentLog.getContexts(), contexts);
                addContexts(currentLog.getIncludeContexts(), contexts);
                currentLog = currentLog.getParentChangeLog();
            }
            if (contexts.isEmpty()) {
                return true; // Nothing would be written
            }
            Collections.reverse(contexts);
            writeAttribute(object.getSerializableFieldNamespace(field), field,
                    toAndString(contexts), object.getSerializedObjectNamespace());
            return true;
        }
        // REVISIT: Introduce option to erase "context" and/or "labels",
        // while filtering out change-sets which do not match given
        // context and/or labels.
        return false;
    }

    private static void addContexts(ContextExpression expr,
                                    Collection<ContextExpression> contexts) {
        if (expr != null && !expr.isEmpty()) {
            contexts.add(expr);
        }
    }

    private static String toAndString(Collection<ContextExpression> contexts) {
        StringBuilder value = new StringBuilder();
        for (ContextExpression expr : contexts) {
            if (value.length() > 0) {
                value.append(" AND ");
            }
            value.append('(').append(expr.toString()).append(')');
        }
        return value.toString();
    }

    private boolean writeFilePathAttribute(LiquibaseSerializable object, String field) {
        String pathField;
        if (object instanceof LoadDataChange) {
            pathField = "file";
        } else if (object instanceof SQLFileChange
                || object instanceof CreateViewChange
                || object instanceof CreateProcedureChange) {
            pathField = "path";
        } else {
            return false;
        }

        Change change = (Change) object;
        if (field.equals(pathField)
                && Boolean.TRUE.equals(change.getSerializableFieldValue("relativeToChangelogFile"))) {
            writeAttribute(change.getSerializableFieldNamespace(pathField), pathField,
                    resolvePath(change, pathField), change.getSerializedObjectNamespace());
            return true;
        } else if (field.equals("relativeToChangelogFile")) {
            return true; // Don't write - imply default value of false.
        }
        return false;
    }

    private void writeObject(LiquibaseSerializable object) throws SAXException {
        String namespace = object.getSerializedObjectNamespace();
        try {
            xmlOut.writeStartElement(namespace, object.getSerializedObjectName());
            currentElement = object.getSerializedObjectName();
            for (String field : writeSerializableAttributes(object)) {
                writeField(object.getSerializableFieldNamespace(field),
                                field, object.getSerializableFieldValue(field),
                                object.getSerializableFieldType(field),
                                namespace);
            }
            xmlOut.writeEndElement();
        } catch (UnexpectedLiquibaseException e) {
            if (object instanceof ChangeSet && e.getMessage().startsWith(INVALID_STRING_ENCODING_MESSAGE)) {
                throw new UnexpectedLiquibaseException(e.getMessage() + " in changeSet " + object
                        + ". To resolve, remove the invalid character on the database and try again");
            }
            throw e;
        }
    }

    private void writeField(String objectNamespace,
                            String objectName,
                            Object value,
                            SerializationType serializationType,
                            String parentNamespace)
            throws SAXException
    {
        if (value == null) {
            return;
        }

        if (value instanceof LiquibaseSerializable) {
            writeObject((LiquibaseSerializable) value);
        } else if (value instanceof Collection) {
            for (Object child : (Collection<?>) value) {
                writeField(objectNamespace, objectName, child, serializationType, parentNamespace);
            }
        } else if (value instanceof Map) {
            for (Map.Entry<?,?> entry : ((Map<?,?>) value).entrySet()) {
                // XXX: Some funny namespace handling
                xmlOut.writeStartElement(DEFAULT_NS_PREFIX, STANDARD_CHANGELOG_NAMESPACE, objectName);
                if (serializationType == SerializationType.NESTED_OBJECT) {
                    writeField(objectNamespace, (String) entry.getKey(), entry.getValue(), serializationType, objectNamespace);
                } else {
                    writeField(objectNamespace, "name", entry.getKey(), SerializationType.NAMED_FIELD, objectNamespace);
                    writeField(objectNamespace, "value", entry.getValue(), serializationType, objectNamespace);
                }
                xmlOut.writeEndElement();
            }
        } else if (value instanceof Object[]) {
            if (serializationType.equals(SerializationType.NESTED_OBJECT)) {
                // XXX: More funny namespace handling
                String namespace = STANDARD_CHANGELOG_NAMESPACE;
                xmlOut.writeStartElement(DEFAULT_NS_PREFIX, namespace, objectName);
                for (Object child : (Object[]) value) {
                    writeField(namespace, objectName, child, serializationType, parentNamespace);
                }
                xmlOut.writeEndElement();
            } else {
                for (Object child : (Object[]) value) {
                    writeField(objectNamespace, objectName, child, serializationType, parentNamespace);
                }
            }
        } else if (serializationType.equals(SerializationType.NESTED_OBJECT)) {
            // XXX: A funny namespace handling
            xmlOut.writeStartElement(DEFAULT_NS_PREFIX, STANDARD_CHANGELOG_NAMESPACE, objectName);
            xmlOut.writeCharacters(value.toString()); // XXX: No checkString?
            xmlOut.writeEndElement();
        } else if (serializationType.equals(SerializationType.DIRECT_VALUE)) {
            try {
                xmlOut.writeCharacters(checkString(value.toString()));
            } catch (UnexpectedLiquibaseException e) {
                throw new UnexpectedLiquibaseException(e.getMessage() + " in text of " + objectName
                        + ". To resolve, remove the invalid character on the database and try again");
            }
        } else {
            writeAttribute(objectNamespace, objectName, value, parentNamespace);
        }
    }

    private void writeAttribute(String namespace, String fieldName, Object value, String parentNamespace) {
        String xmlNs = namespace;
        if (namespace == null
                || namespace.equals(parentNamespace)
                || namespace.equals(STANDARD_CHANGELOG_NAMESPACE)) {
            xmlNs = XMLConstants.NULL_NS_URI;
        }
        try {
            xmlOut.writeAttribute(xmlNs, fieldName, checkString(value.toString()));
        } catch (UnexpectedLiquibaseException e) {
            if (e.getMessage().startsWith(INVALID_STRING_ENCODING_MESSAGE)) {
                throw new UnexpectedLiquibaseException(e.getMessage() + " on " + currentElement + "."
                        + fieldName + ". To resolve, remove the invalid character on the database and try again");
            }
        }
    }

    private static String resolvePath(Change change, String pathField) {
        String filePath = (String) change.getSerializableFieldValue(pathField);
        ChangeSet changeSet = change.getChangeSet();
        String base = (changeSet.getChangeLog() == null)
                      ? base = changeSet.getFilePath() // XXX: changeSet logicalFilePath could be anything
                      : changeSet.getChangeLog().getPhysicalFilePath().replace('\\', '/');
        if (base == null || base.indexOf('/') < 0) {
            return base = "";
        }
        return base.replaceFirst("/[^/]*$", "") + '/' + filePath;
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

}
