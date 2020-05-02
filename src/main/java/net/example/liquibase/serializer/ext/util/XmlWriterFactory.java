/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.serializer.ext.util;

import java.io.OutputStream;
import java.io.Writer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import liquibase.configuration.GlobalConfiguration;
import liquibase.configuration.LiquibaseConfiguration;

public final class XmlWriterFactory {

    private static XMLOutputFactory outputFactory;

    private XmlWriterFactory() {
        // No instances
    }

    private static String defaultCharset() {
        return LiquibaseConfiguration.getInstance()
                  .getConfiguration(GlobalConfiguration.class)
                  .getOutputEncoding();
    }

    private static XMLOutputFactory outputFactory() {
        if (outputFactory == null) {
            outputFactory = XMLOutputFactory.newFactory();
        }
        return outputFactory;
    }

    private static XMLStreamWriter makePretty(XMLStreamWriter xmlOut) {
        // REVISIT: Have an AutoEmptyElementWriter, also.
        com.sun.xml.internal.txw2.output.IndentingXMLStreamWriter
                indentOut = new com.sun.xml.internal.txw2.output.IndentingXMLStreamWriter(xmlOut);
        indentOut.setIndentStep("    ");
        return indentOut;
    }

    public static synchronized XMLStreamWriter setUpWrite(OutputStream out) throws XMLStreamException {
        outputFactory().setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
        XMLStreamWriter xmlOut = outputFactory.createXMLStreamWriter(out, defaultCharset());
        return makePretty(xmlOut);
    }

    public static synchronized XMLStreamWriter setUpWrite(Writer out) throws XMLStreamException {
        return setUpWrite(out, true, true);
    }

    public static synchronized XMLStreamWriter setUpWrite(Writer out, boolean pretty, boolean nsDecl) throws XMLStreamException {
        outputFactory().setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, nsDecl);
        XMLStreamWriter xmlOut = outputFactory.createXMLStreamWriter(out);
        return pretty ? makePretty(xmlOut) : xmlOut;
    }

}
