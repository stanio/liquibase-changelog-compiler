/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.command.ext;

import static javax.xml.XMLConstants.DEFAULT_NS_PREFIX;
import static javax.xml.XMLConstants.NULL_NS_URI;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.NamespaceSupport;

import liquibase.configuration.GlobalConfiguration;
import liquibase.configuration.LiquibaseConfiguration;
import liquibase.logging.LogService;

/**
 * Implements space and time efficient streaming API for writing XML
 * on top of {@code ContentHandler}/{@code TransformerHandler} combined
 * with {@code NamespaceSupport}.
 * <p>
 * REVISIT using StAX {@link javax.xml.stream.XMLStreamWriter} with (copy
 * of) {@code com.sun.xml.internal.txw2.output.IndentingXMLStreamWriter}.
 */
class SimpleXmlWriter {


    public enum OutputOption { PRETTY, XML_DECL, OMIT_NS_DECL }


    private static final String XML_VERSION = "1.1";
    private static final int INDENT_AMOUNT = 4;
    private static final String CDATA = "CDATA";

    private static EnumSet<OutputOption>
            defaultOptions = EnumSet.of(OutputOption.PRETTY, OutputOption.XML_DECL);

    private String charset;
    private TransformerHandler outputHandler;
    private Deque<String> contentStack = new ArrayDeque<>();
    private String deferredElement;
    private AttributesImpl attributes = new AttributesImpl();
    private NamespaceSupport namespaceSupport = new NamespaceSupport();
    private boolean omitNSDeclarations;

    SimpleXmlWriter() {
        //namespaceSupport.setNamespaceDeclUris(true);
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

    private void setUpWrite(String encoding, EnumSet<OutputOption> options) {
        this.charset = encoding;
        Transformer transformer = initOutputHandler().getTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, charset);
        transformer.setOutputProperty(OutputKeys.INDENT,
                options.contains(OutputOption.PRETTY) ? "yes" : "no");
        namespaceSupport.reset();
        omitNSDeclarations = options.contains(OutputOption.OMIT_NS_DECL);
    }

    private static String defaultCharset() {
        return LiquibaseConfiguration.getInstance()
                  .getConfiguration(GlobalConfiguration.class)
                  .getOutputEncoding();
    }

    private String xmlDecl() {
        return "<?xml version=\"" + XML_VERSION
                + "\" encoding=\"" + charset + "\"?>"
                + System.lineSeparator();
    }

    public void startDocument(OutputStream out) throws IOException {
        startDocument(out, defaultCharset(), defaultOptions);
    }

    void startDocument(OutputStream out, String encoding,
                       EnumSet<OutputOption> options)
            throws IOException {
        setUpWrite(encoding, options);
        if (options.contains(OutputOption.XML_DECL)) {
            out.write(xmlDecl().getBytes(charset));
        }
        outputHandler.setResult(new StreamResult(out));
    }

    public void startDocument(Writer out) throws IOException {
        startDocument(out, defaultOptions);
    }

    public void startDocument(Writer out, EnumSet<OutputOption> options)
            throws IOException {
        startDocument(out, defaultCharset(), options);
    }

    void startDocument(Writer out, String encoding,
                       EnumSet<OutputOption> options)
            throws IOException {
        setUpWrite(encoding, options);
        if (options.contains(OutputOption.XML_DECL)) {
            out.write(xmlDecl());
        }
        outputHandler.setResult(new StreamResult(out));
    }

    public void declarePrefix(String prefix, String uri) {
        namespaceSupport.declarePrefix(prefix, uri);
    }

    String qualifyName(String namespace, String name) {
        if (namespace == null) {
            return qualifyName(NULL_NS_URI, name);
        }
        if (namespace.equals(defaultNamespace())) {
            return name;
        }
        String prefix = namespaceSupport.getPrefix(namespace);
        if (prefix == null) {
            int n = 1;
            do {
                prefix = "ns" + n++;
            } while (namespaceSupport.getURI(prefix) != null);
            namespaceSupport.declarePrefix(prefix, namespace);
        }
        // REVISIT: May cache string concatenation results.
        return prefix + ":" + name;
    }

    private String defaultNamespace() {
        return namespaceSupport.getURI(DEFAULT_NS_PREFIX);
    }

    private void startElement(Supplier<String> qname) throws SAXException {
        writeDeferred();
        attributes.clear();
        deferredElement = qname.get();
        contentStack.push(deferredElement);
    }

    public void startElement(String qname) throws SAXException {
        startElement(() -> qname);
    }

    public void startElement(String namespace, String localName)
            throws SAXException {
        startElement(() -> qualifyName(namespace, localName));
    }

    public void startElement(String prefix, String namespace, String localName)
            throws SAXException {
        startElement(() -> {
            if (!namespace.equals(namespaceSupport.getURI(prefix))) {
                declarePrefix(prefix, namespace);
            }
            return localName;
        });
    }

    private void writeDeferred() throws SAXException {
        if (deferredElement != null) {
            addNamespacePrefies();
            outputHandler.startElement("", "", deferredElement, attributes);
            deferredElement = null;
        }
        namespaceSupport.pushContext();
    }

    private void addNamespacePrefies() {
        if (omitNSDeclarations) {
            return;
        }
        Enumeration<String> declaredPrefixes = namespaceSupport.getDeclaredPrefixes();
        while (declaredPrefixes.hasMoreElements()) {
            String prefix = declaredPrefixes.nextElement();
            attributes.addAttribute("", "",
                    prefix.equals(DEFAULT_NS_PREFIX) ? "xmlns" : "xmlns:" + prefix,
                    CDATA, namespaceSupport.getURI(prefix));
        }
    }

    public void addAttribute(String qname, String value) {
        attributes.addAttribute("", "", qname, CDATA, value);
    }

    public String currentElement() {
        return contentStack.peek();
    }

    public Attributes attributes() {
        return attributes;
    }

    public void writeText(String text) throws SAXException {
        writeDeferred();
        outputHandler.characters(text.toCharArray(), 0, text.length());
    }

    public void endElement() throws SAXException {
        writeDeferred();
        outputHandler.endElement("", "", contentStack.pop());
        namespaceSupport.popContext();
    }

    public void endDocument() throws SAXException {
        writeDeferred();
        outputHandler.endDocument();
    }

}
