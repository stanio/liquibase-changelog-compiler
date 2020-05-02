/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.groovy;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import liquibase.changelog.ChangeLogChild;
import liquibase.changelog.ChangeLogInclude;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.exception.LiquibaseException;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import liquibase.serializer.core.xml.XMLChangeLogSerializer;
import liquibase.util.xml.DefaultXmlWriter;

public class CustomXMLChangeLogSerializer {

    private Path targetDir;
    private XMLChangeLogSerializer changeLogSerializer = new XMLChangeLogSerializer();

    private DefaultXmlWriter xmlWriter = new DefaultXmlWriter();
    private DocumentBuilder documentBuilder;

    public CustomXMLChangeLogSerializer() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        try {
            documentBuilder = factory.newDocumentBuilder();
            documentBuilder.setEntityResolver(
                    (publicId, systemId) -> new InputSource(new StringReader("")));
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public void serialize(Collection<String> classpath, String changeLogFile, String targetDir)
            throws LiquibaseException, IOException
    {
        URL[] urls = classpath.stream().map(path -> {
            try {
                return Paths.get(path).toAbsolutePath().toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }).toArray(URL[]::new);
        serialize(new URLClassLoader(urls), changeLogFile, targetDir);
    }

    public void serialize(ClassLoader classLoader, String changeLogFile, String targetDir)
            throws LiquibaseException, IOException
    {
        ResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor(classLoader);
        ChangeLogParser parser = ChangeLogParserFactory.getInstance().getParser(changeLogFile, resourceAccessor);
        DatabaseChangeLog databaseChangeLog = parser.parse(changeLogFile, new ChangeLogParameters(), resourceAccessor);
        serialize(databaseChangeLog, targetDir);
    }

    public void serialize(DatabaseChangeLog changeLog, String targetDir)
            throws IOException
    {
        this.targetDir = Paths.get(targetDir).toAbsolutePath();

        List<DatabaseChangeLog> includedLogs = new ArrayList<>();
        for (ChangeSet changeSet : changeLog.getChangeSets()) {
            DatabaseChangeLog item = changeSet.getChangeLog();
            if (includedLogs.lastIndexOf(item) < 0) {
                includedLogs.add(item);
            }
        }

        if (includedLogs.isEmpty()) {
            writeRootChangeLog(changeLog, null);
        } else if (includedLogs.size() > 1 || includedLogs.indexOf(changeLog) < 0) {
            writeRootChangeLog(changeLog, includedLogs.get(0));
        }

        for (int i = 0, n = includedLogs.size(); i < n; i++) {
            writeChangeLog(includedLogs.get(i),
                    (i + 1 < n) ? includedLogs.get(i + 1) : null);
        }
    }

    private void writeChangeLog(DatabaseChangeLog changeLog, DatabaseChangeLog nextLog)
            throws IOException
    {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(8 * 1024);
        writeChangeLog(changeLog, nextLog, buf);
        Path changeLogFile = targetDir.resolve(xmlExt(changeLog.getPhysicalFilePath()));
        Files.createDirectories(changeLogFile.getParent());
        try (OutputStream out = Files.newOutputStream(changeLogFile)) {
            setLogicalFilePath(new ByteArrayInputStream(buf.toByteArray()),
                               changeLog.getLogicalFilePath(), out);
        }
    }

    private void setLogicalFilePath(InputStream in,
                                    String logicalFilePath,
                                    OutputStream out)
            throws IOException
    {
        try {
            Document doc = documentBuilder.parse(in);
            doc.getDocumentElement().setAttribute("logicalFilePath", logicalFilePath);
            xmlWriter.write(doc, out);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeChangeLog(DatabaseChangeLog changeLog,
                                DatabaseChangeLog nextLog,
                                OutputStream out)
            throws IOException
    {
        List<ChangeSet> changeSets = changeLog.getChangeSets();
        if (nextLog == null) {
            changeLogSerializer.write(changeSets, out);
        } else {
            ChangeLogInclude include = new ChangeLogInclude();
            include.setFile(xmlExt(nextLog.getPhysicalFilePath()));
            List<ChangeLogChild> children = new ArrayList<>(changeSets.size() + 1);
            children.addAll(changeSets);
            children.add(include);
            changeLogSerializer.write(children, out);
        }
    }

    private void writeRootChangeLog(DatabaseChangeLog rootLog,
                                    DatabaseChangeLog includeLog)
            throws IOException
    {
        Path changeLogFile = targetDir.resolve(xmlExt(rootLog.getPhysicalFilePath()));
        Files.createDirectories(changeLogFile.getParent());
        try (OutputStream out = Files.newOutputStream(changeLogFile)) {
            if (includeLog == null) {
                changeLogSerializer.write(emptyList(), out);
            } else {
                ChangeLogInclude include = new ChangeLogInclude();
                include.setFile(xmlExt(includeLog.getPhysicalFilePath()));
                changeLogSerializer.write(singletonList(include), out);
            }
        }
    }

    private static String xmlExt(String path) {
        int dotIndex = path.lastIndexOf('.');
        return (dotIndex > 0) ? path.substring(0, dotIndex) + ".xml"
                              : path + ".xml";
    }

    public static void main(String[] args) throws Exception {
        // Just use the JVM classpath
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        CustomXMLChangeLogSerializer serializer = new CustomXMLChangeLogSerializer();
        serializer.serialize(contextClassLoader, args[0], args[1]);
    }

}
