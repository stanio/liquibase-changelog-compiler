/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.command.ext;

import static javax.xml.XMLConstants.DEFAULT_NS_PREFIX;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
import static liquibase.serializer.LiquibaseSerializable.STANDARD_CHANGELOG_NAMESPACE;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xml.sax.SAXException;

import liquibase.ContextExpression;
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
import net.example.liquibase.command.ext.SimpleXmlWriter.OutputOption;

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

    private String basePath;
    private SimpleXmlWriter xmlWriter = new SimpleXmlWriter();

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
            basePath = null;
            xmlWriter.startDocument(out);
            writeChangeLog(null, children);
            xmlWriter.endDocument();
        } catch (SAXException e) {
            throw ioExceptionFor(e);
        }
    }

    @Override
    public String serialize(LiquibaseSerializable object, boolean pretty) {
        StringWriter buf = new StringWriter();
        try {
            basePath = null;
            EnumSet<OutputOption> options = EnumSet.of(OutputOption.OMIT_NS_DECL);
            if (pretty) options.add(OutputOption.PRETTY);
            xmlWriter.startDocument(buf, options);
            declareStandardPrefixes();
            writeObject(object);
            xmlWriter.endDocument();
        } catch (SAXException | IOException e) {
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
                basePath = databaseChangeLog.getLogicalFilePath();
                xmlWriter.startDocument(buf);
                writeChangeLog(log, structure.getContent(log));
                xmlWriter.endDocument();
            }
        } catch (SAXException | IOException e) {
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
            basePath = xmlExt(log.getPhysicalFilePath());
            Path changeLogFile = targetPath.resolve(basePath);
            Files.createDirectories(changeLogFile.getParent());
            try (OutputStream out = Files.newOutputStream(changeLogFile)) {
                basePath = basePath.replace('\\', '/');
                xmlWriter.startDocument(out);
                writeChangeLog(log, structure.getContent(log));
                xmlWriter.endDocument();
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
            throws SAXException
    {
        xmlWriter.startElement("databaseChangeLog");
        addChangeLogAttributes(changeLog);
        for (ChangeLogChild child : content) {
            writeObject(child);
        }
        xmlWriter.endElement();
    }

    private String declareStandardPrefixes() {
        xmlWriter.declarePrefix(DEFAULT_NS_PREFIX, STANDARD_CHANGELOG_NAMESPACE);

        StringBuilder schemaLocations = new StringBuilder(500);
        for (NamespaceDetails details : NamespaceDetailsFactory.getInstance().getNamespaceDetails()) {
            for (String namespace : details.getNamespaces()) {
                if (details.getPriority() > 0 && details.supports(this, namespace)) {
                    String shortName = details.getShortName(namespace);
                    String schemaUrl = details.getSchemaUrl(namespace);
                    if (shortName != null && !shortName.isEmpty()) {
                        xmlWriter.declarePrefix(shortName, namespace);
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

    private void addChangeLogAttributes(DatabaseChangeLog changeLog) {
        if (changeLog != null) {
            if (basePath != null && !basePath.equals(changeLog.getLogicalFilePath())) {
                xmlWriter.addAttribute("logicalFilePath", changeLog.getLogicalFilePath());
                basePath = changeLog.getLogicalFilePath();
            }
            ContextExpression contexts = changeLog.getContexts();
            if (contexts != null && !contexts.isEmpty()) {
                xmlWriter.addAttribute("context", contexts.toString());
            }
            if (changeLog.getObjectQuotingStrategy() != null
                    && changeLog.getObjectQuotingStrategy() != ObjectQuotingStrategy.LEGACY) {
                xmlWriter.addAttribute("objectQuotingStrategy",
                        changeLog.getObjectQuotingStrategy().toString());
            }
        }
        String schemaLocations = declareStandardPrefixes();
        if (schemaLocations.length() > 0) {
            xmlWriter.declarePrefix("xsi", W3C_XML_SCHEMA_INSTANCE_NS_URI);
            xmlWriter.addAttribute("xsi:schemaLocation", schemaLocations);
        }
    }

    private Set<String> addSerializableAttributes(LiquibaseSerializable object) {
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

            String attributeName = qualifyAttributeName(field,
                    object.getSerializableFieldNamespace(field),
                    object.getSerializedObjectNamespace());
            try {
                xmlWriter.addAttribute(attributeName, checkString(value.toString()));
            } catch (UnexpectedLiquibaseException e) {
                throw new UnexpectedLiquibaseException(e.getMessage()
                        + " on " + object.getSerializedObjectName() + "." + field
                        + ". To resolve, remove the invalid character on the database and try again");
            }
        }
        if (object instanceof ChangeSet) {
            ChangeSet changeSet = (ChangeSet) object;
            if (basePath != null
                    && !basePath.equals(changeSet.getFilePath())
                    && xmlWriter.attributes().getValue("logicalFilePath") == null) {
                xmlWriter.addAttribute("logicalFilePath", changeSet.getFilePath());
            }
            // TODO: Merge changelog context and include context, if necessary.
            // TODO: Merge changelog include labels, if necessary.
        } else if (object instanceof LoadDataChange) {
            // TODO: Adjust file, if necessary.
        } else if (object instanceof SQLFileChange) {
            // TODO: Adjust path, if necessary.
        } else if (object instanceof CreateViewChange) {
            // TODO: Adjust path, if necessary.
        } else if (object instanceof CreateProcedureChange) {
            // TODO: Adjust path, if necessary.
        }
        return remaining;
    }

    private void writeObject(LiquibaseSerializable object) throws SAXException {
        String namespace = object.getSerializedObjectNamespace();
        try {
            xmlWriter.startElement(namespace, object.getSerializedObjectName());
            for (String field : addSerializableAttributes(object)) {
                writeFieldValue(object.getSerializableFieldNamespace(field),
                                field, object.getSerializableFieldValue(field),
                                object.getSerializableFieldType(field),
                                namespace);
            }
            xmlWriter.endElement();
        } catch (UnexpectedLiquibaseException e) {
            if (object instanceof ChangeSet && e.getMessage().startsWith(INVALID_STRING_ENCODING_MESSAGE)) {
                throw new UnexpectedLiquibaseException(e.getMessage() + " in changeSet " + object
                        + ". To resolve, remove the invalid character on the database and try again");
            }
            throw e;
        }
    }

    private void writeFieldValue(String objectNamespace,
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
                writeFieldValue(objectNamespace, objectName, child, serializationType, parentNamespace);
            }
        } else if (value instanceof Map) {
            for (Map.Entry<?,?> entry : ((Map<?,?>) value).entrySet()) {
                // XXX: Some funny namespace handling
                xmlWriter.startElement(DEFAULT_NS_PREFIX, STANDARD_CHANGELOG_NAMESPACE, objectName);
                writeFieldValue(objectNamespace, "name", entry.getValue(), serializationType, objectNamespace);
                writeFieldValue(objectNamespace, "value", entry.getValue(), serializationType, objectNamespace);
                xmlWriter.endElement();
            }
        } else if (value instanceof Object[]) {
            if (serializationType.equals(SerializationType.NESTED_OBJECT)) {
                // XXX: More funny namespace handling
                String namespace = STANDARD_CHANGELOG_NAMESPACE;
                xmlWriter.startElement(DEFAULT_NS_PREFIX, namespace, objectName);
                for (Object child : (Object[]) value) {
                    writeFieldValue(namespace, objectName, child, serializationType, parentNamespace);
                }
                xmlWriter.endElement();
            } else {
                for (Object child : (Object[]) value) {
                    writeFieldValue(objectNamespace, objectName, child, serializationType, parentNamespace);
                }
            }
        } else if (serializationType.equals(SerializationType.NESTED_OBJECT)) {
            // XXX: A funny namespace handling
            xmlWriter.startElement(DEFAULT_NS_PREFIX, STANDARD_CHANGELOG_NAMESPACE, objectName);
            xmlWriter.writeText(value.toString()); // XXX: No checkString?
            xmlWriter.endElement();
        } else if (serializationType.equals(SerializationType.DIRECT_VALUE)) {
            try {
                xmlWriter.writeText(checkString(value.toString()));
            } catch (UnexpectedLiquibaseException e) {
                throw new UnexpectedLiquibaseException(e.getMessage() + " in text of " + objectName
                        + ". To resolve, remove the invalid character on the database and try again");
            }
        } else {
            String attributeName = qualifyAttributeName(objectName, objectNamespace, parentNamespace);
            try {
                xmlWriter.addAttribute(attributeName, checkString(value.toString()));
            } catch (UnexpectedLiquibaseException e) {
                if (e.getMessage().startsWith(INVALID_STRING_ENCODING_MESSAGE)) {
                    throw new UnexpectedLiquibaseException(e.getMessage() + " on " + xmlWriter.currentElement() + "."
                            + attributeName + ". To resolve, remove the invalid character on the database and try again");
                }
            }
        }
    }

    private String qualifyAttributeName(String objectName, String objectNamespace, String parentNamespace) {
        if (objectNamespace == null
                || objectNamespace.equals(parentNamespace)
                || objectNamespace.equals(STANDARD_CHANGELOG_NAMESPACE)) {
            return objectName;
        }
        return xmlWriter.qualifyName(objectNamespace, objectName);
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
