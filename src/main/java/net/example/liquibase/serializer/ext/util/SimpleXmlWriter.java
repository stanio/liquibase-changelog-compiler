/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.serializer.ext.util;

import static javax.xml.XMLConstants.DEFAULT_NS_PREFIX;
import static javax.xml.XMLConstants.NULL_NS_URI;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Enumeration;
import java.util.function.Supplier;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.NamespaceSupport;

import liquibase.configuration.GlobalConfiguration;
import liquibase.configuration.LiquibaseConfiguration;
import liquibase.logging.LogService;

/**
 * Simplified StAX-like API for efficient writing of XML implemented
 * on top of {@code ContentHandler}/{@code TransformerHandler} combined
 * with {@code NamespaceSupport}.
 * <p>
 * Currently there's no simple way of producing pretty-formatted output
 * with {@link javax.xml.stream.XMLStreamWriter}.  It also requires
 * explicit {@code writeEmptyElement()} which is not always simple for
 * use in all cases.</p>
 */
public class SimpleXmlWriter {

    private static final String XML_VERSION = "1.1";
    private static final int INDENT_AMOUNT = 4;
    private static final String CDATA = "CDATA";

    private String charset;
    private StreamResult result;
    private TransformerHandler outputHandler;
    private Deque<String> contentStack = new ArrayDeque<>();
    private String deferredElement;
    private AttributesImpl attributes = new AttributesImpl();
    private NamespaceSupport namespaceContext = new NamespaceSupport();
    private boolean repairingNamespaces;

    public SimpleXmlWriter() {
        namespaceContext.setNamespaceDeclUris(true);
    }

    private TransformerHandler initOutputHandler() {
        if (outputHandler != null) {
            return outputHandler;
        }

        SAXTransformerFactory factory;
        try {
            factory = (SAXTransformerFactory) TransformerFactory.newInstance();
        } catch (ClassCastException e) {
            throw new TransformerFactoryConfigurationError(e);
        }
        try {
            factory.setAttribute("indent-number", INDENT_AMOUNT);
        } catch (IllegalArgumentException e) {
            // guess we can't set it, that's ok
            LogService.getLog(getClass()).debug("Could not set \"indent-number\"", e);
        }
        try {
            outputHandler = factory.newTransformerHandler();
        } catch (TransformerConfigurationException e) {
            throw new TransformerFactoryConfigurationError(e);
        }
        Transformer transformer = outputHandler.getTransformer();
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.VERSION, XML_VERSION);
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        try {
            transformer.setOutputProperty("{http://xml.apache.org/xslt}"
                    + "indent-amount", String.valueOf(INDENT_AMOUNT));
        } catch (IllegalArgumentException e) {
            LogService.getLog(getClass()).debug("Could not set \"indent-amount\"", e);
        }
        return outputHandler;
    }

    private void setUpWrite(String encoding, boolean indent, boolean nsDecl) {
        this.charset = encoding;
        Transformer transformer = initOutputHandler().getTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, charset);
        transformer.setOutputProperty(OutputKeys.INDENT, indent ? "yes" : "no");
        namespaceContext.reset();
        repairingNamespaces = nsDecl;
    }

    public void setUpWrite(OutputStream out) {
        setUpWrite(defaultCharset(), true, true);
        outputHandler.setResult(result = new StreamResult(out));
    }

    public void setUpWrite(Writer out) {
        setUpWrite(out, true, true);
    }

    public void setUpWrite(Writer out, boolean indent, boolean nsDecl) {
        setUpWrite(defaultCharset(), indent, nsDecl);
        outputHandler.setResult(result = new StreamResult(out));
    }

    private static String defaultCharset() {
        return LiquibaseConfiguration.getInstance()
                  .getConfiguration(GlobalConfiguration.class)
                  .getOutputEncoding();
    }

    public NamespaceSupport getNamespaceContext() {
        return namespaceContext;
    }

    public void setPrefix(String prefix, String uri) {
        namespaceContext.declarePrefix(prefix, uri);
    }

    private String prefixedName(String namespace, String name) {
        String prefix = namespaceContext.getPrefix(namespace);
        if (prefix == null) {
            int n = 1;
            do {
                prefix = "ns" + n++;
            } while (namespaceContext.getURI(prefix) != null);
            setPrefix(prefix, namespace);
        }
        // REVISIT: May cache string concatenation results.
        return prefix + ":" + name;
    }

    public void writeStartDocument() throws IOException {
        String xmlDecl = "<?xml version=\"" + XML_VERSION
                         + "\" encoding=\"" + charset + "\"?>"
                         + System.lineSeparator();
        if (result.getOutputStream() == null) {
            result.getWriter().write(xmlDecl);
        } else {
            result.getOutputStream().write(xmlDecl.getBytes(charset));
        }
    }

    private void writeStartElement(Supplier<String> qname) throws SAXException {
        writeDeferredElement();
        attributes.clear();
        deferredElement = qname.get();
        contentStack.push(deferredElement);
    }

    public void writeStartElement(String qname) throws SAXException {
        writeStartElement(() -> qname);
    }

    public void writeStartElement(String namespace, String localName)
            throws SAXException {
        writeStartElement(() -> {
            String uri = (namespace == null) ? NULL_NS_URI : namespace;
            if (uri.equals(namespaceContext.getURI(DEFAULT_NS_PREFIX))) {
                return localName;
            } else if (uri.equals(NULL_NS_URI)) {
                setPrefix(DEFAULT_NS_PREFIX, NULL_NS_URI);
                return localName;
            }
            return prefixedName(uri, localName);
        });
    }

    public void writeStartElement(String prefix, String namespace, String localName)
            throws SAXException {
        writeStartElement(() -> {
            if (!namespace.equals(namespaceContext.getURI(prefix))) {
                setPrefix(prefix, namespace);
            }
            return DEFAULT_NS_PREFIX.equals(prefix) ? localName : prefix + ":" + localName;
        });
    }

    private void writeDeferredElement() throws SAXException {
        if (deferredElement != null) {
            if (repairingNamespaces) {
                writeNamespaceDeclarations();
            }
            outputHandler.startElement("", "", deferredElement, attributes);
            deferredElement = null;
        }
        namespaceContext.pushContext();
    }

    private void writeNamespaceDeclarations() {
        Enumeration<String> declaredPrefixes = namespaceContext.getDeclaredPrefixes();
        while (declaredPrefixes.hasMoreElements()) {
            String prefix = declaredPrefixes.nextElement();
            attributes.addAttribute("", "",
                    prefix.equals(DEFAULT_NS_PREFIX) ? "xmlns" : "xmlns:" + prefix,
                    CDATA, namespaceContext.getURI(prefix));
        }
    }

    public void writeAttribute(String qname, String value) {
        attributes.addAttribute("", "", qname, CDATA, value);
    }

    public void writeAttribute(String namespace, String localName, String value) {
        String uri = (namespace == null) ? NULL_NS_URI : namespace;
        String qname = uri.equals(NULL_NS_URI) ? localName : prefixedName(uri, localName);
        writeAttribute(qname, value);
    }

    public void writeCharacters(String text) throws SAXException {
        writeDeferredElement();
        outputHandler.characters(text.toCharArray(), 0, text.length());
    }

    public void writeEndElement() throws SAXException {
        writeDeferredElement();
        outputHandler.endElement("", "", contentStack.pop());
        namespaceContext.popContext();
    }

    public void writeEndDocument() throws SAXException {
        writeDeferredElement();
        while (!contentStack.isEmpty()) {
            outputHandler.endElement("", "", contentStack.pop());
        }
        outputHandler.endDocument();
    }

}
