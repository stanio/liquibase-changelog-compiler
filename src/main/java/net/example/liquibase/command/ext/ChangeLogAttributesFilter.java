/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.command.ext;

import static liquibase.serializer.LiquibaseSerializable.STANDARD_CHANGELOG_NAMESPACE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.Attributes2Impl;
import org.xml.sax.helpers.XMLFilterImpl;

import liquibase.ContextExpression;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.logging.LogService;

/**
 * An {@code XMLFilter} implementation which sets {@code logicalFilePath}
 * attribute on the (root) document element.
 *
 * @see  #apply(DatabaseChangeLog, InputStream, OutputStream)
 */
class ChangeLogAttributesFilter extends XMLFilterImpl {
    /* Using an identity transformer with the simple transformation
     * done by an XMLFilter avoids buffering the whole source into
     * memory. */


    static final class ChangeSetKey {
        private final String id;
        private final String author;
        ChangeSetKey(String id, String author) {
            this.id = Objects.requireNonNull(id);
            this.author = Objects.requireNonNull(author);
        }
        static ChangeSetKey get(ChangeSet changeSet) {
            return new ChangeSetKey(changeSet.getId(), changeSet.getAuthor());
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (author == null ? 0 : author.hashCode());
            result = prime * result + (id == null ? 0 : id.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            ChangeSetKey other = (ChangeSetKey) obj;
            return id.equals(other.id) && author.equals(other.author);
        }
        @Override
        public String toString() {
            return id + ":" + author;
        }
    }


    private DatabaseChangeLog dbChangeLog;
    private Map<ChangeSetKey, String> logicalFilePaths;

    private Transformer identityTransformer;

    ChangeLogAttributesFilter() {
        // no-op
    }

    private void setChangeLog(DatabaseChangeLog changeLog) {
        this.dbChangeLog = changeLog;
    }

    private Transformer getIdentityTransformer() {
        if (identityTransformer == null) {
            TransformerFactory tf = TransformerFactory.newInstance();
            try {
                identityTransformer = tf.newTransformer();
            } catch (TransformerConfigurationException e) {
                throw new TransformerFactoryConfigurationError(e);
            }
            identityTransformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        }
        return identityTransformer;
    }
    
    public void setLogicalFilePaths(Map<ChangeSetKey, String> logicalFilePaths) {
        this.logicalFilePaths = logicalFilePaths;
    }

    /**
     * Processes the given input to set {@code logicalFilePath} attribute
     * on the document element, writing the result to the given output.
     * @param   in      ... ;
     * @param   out     ... .
     * @param   logicalFilePath  ... ;
     * 
     * @throws  IOException  if I/O error occurs.
     */
    public void apply(DatabaseChangeLog changeLog, InputStream in, OutputStream out)
            throws IOException
    {
        setChangeLog(changeLog);
        try {
            getIdentityTransformer()
                    .transform(new SAXSource(this, new InputSource(in)),
                               new StreamResult(out));
        } catch (TransformerException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException(e);
        }
    }

    @Override
    public void parse(InputSource input) throws SAXException, IOException {
        if (dbChangeLog == null) {
            throw new IllegalStateException("No changeLog for filter");
        }

        if (getParent() == null) {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setValidating(false);
            try {
                XMLReader xmlReader = spf.newSAXParser().getXMLReader();
                // No external entities processed.
                xmlReader.setEntityResolver(new EntityResolver() {
                    @Override public InputSource resolveEntity(String publicId, String systemId) {
                        return new InputSource(new StringReader(""));
                    }
                });
                super.setParent(xmlReader);
            } catch (ParserConfigurationException | SAXException e) {
                throw new FactoryConfigurationError(e);
            }
        }
        super.parse(input);
    }

    @Override
    public void startElement(String uri, String localName,
                             String qName, Attributes atts)
            throws SAXException
    {
        if (startChangeSet(uri, localName, qName, atts)
                || startChangeLog(uri, localName, qName, atts)) {
            return;
        }
        super.startElement(uri, localName, qName, atts);
    }

    private boolean startChangeLog(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if (!(localName.equals("databaseChangeLog")
                && uri.equals(STANDARD_CHANGELOG_NAMESPACE))) {
            return false;
        }

        Attributes2Impl augmented = new Attributes2Impl(atts);

        String existingPath = atts.getValue("", "logicalFilePath");
        if (existingPath == null) {
            augmented.addAttribute("", "logicalFilePath",
                    "logicalFilePath", "CDATA", dbChangeLog.getLogicalFilePath());
        } else if (!existingPath.equals(dbChangeLog.getLogicalFilePath())) {
            LogService.getLog(EnhancedXMLChangeLogSerializer.class)
                    .warning("Existing logicalFilePath: " + existingPath
                            + " doesn't match target path: " + dbChangeLog.getLogicalFilePath());
        }

        ContextExpression contexts = dbChangeLog.getContexts();
        if (!(contexts == null || contexts.isEmpty())) {
            String existingContexts = atts.getValue("", "contexts");
            if (existingContexts == null) {
                augmented.addAttribute("", "context", "context", "CDATA", contexts.toString());
            } else if (existingContexts.equals(contexts.toString())) {
                LogService.getLog(EnhancedXMLChangeLogSerializer.class)
                        .warning("Existing contexts: " + existingContexts
                                + " don't match target contexts: " + contexts);
            }
        }

        super.startElement(uri, localName, qName, augmented);
        return true;
    }

    private boolean startChangeSet(String uri, String localName, String qName, Attributes atts)
            throws SAXException
    {
        if (!(localName.equals("changeSet")
                && uri.equals(STANDARD_CHANGELOG_NAMESPACE))) {
            return false;
        }

        String filePath = logicalFilePaths.get(changeSetKey(atts));
        if (filePath == null) {
            return false;
        }


        String existingPath = atts.getValue("", "logicalFilePath");
        if (existingPath == null) {
            Attributes2Impl augmented = new Attributes2Impl(atts);
            augmented.addAttribute("", "logicalFilePath", "logicalFilePath", "CDATA", filePath);
            super.startElement(uri, localName, qName, augmented);
            return true;
        }

        if (!existingPath.equals(filePath)) {
            LogService.getLog(EnhancedXMLChangeLogSerializer.class)
                    .warning("Existing changeSet logicalFilePath: " + existingPath
                            + " doesn't match target path: " + filePath);
        }
        return false;
    }

    private static ChangeSetKey changeSetKey(Attributes atts) {
        return new ChangeSetKey(atts.getValue("", "id"), atts.getValue("", "author"));
    }

}
