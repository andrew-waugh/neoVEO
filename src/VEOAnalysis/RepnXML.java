/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.AnalysisBase;
import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import VERSCommon.VEOFailure;
import VERSCommon.VEOFatal;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * This class represents an object that is an XML document. When an instance of
 * the class is created, the XML document is opened and validated against the
 * DTD or schema.
 *
 * @author Andrew Waugh
 */
abstract class RepnXML extends AnalysisBase implements ErrorHandler {

    private static final String CLASSNAME = "RepnXML";
    private Document doc;    // internal DOM representation of XML document
    private DocumentBuilder parser; // parser
    private DocumentBuilderFactory dbf; // DOM factory
    private boolean contentsAvailable; // true if contents are available
    private NodeList elements;  // list of elements in document order
    private int currentElement; // current element
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.RepnXML");

    /**
     * Create a RepnXML instance. This creates the parser for the document, but
     * does not parse the document. The method parse() must be called to parse
     * the document.
     *
     * @param id the identifier to use in describing this
     * @param results the results summary to build
     * @throws VERSCommon.VEOFatal if prevented from continuing processing at
     * all
     */
    protected RepnXML(String id, ResultSummary results) throws VEOFatal {
        super(id, results);

        // create parser
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setValidating(false);   // will specify a schema to validate the XML
        try {
            parser = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new VEOFatal(CLASSNAME, 1, "Failed to construct an XML parser", e);
        }
        contentsAvailable = false;
    }

    /**
     * Free resources associated with this object. This instance should not be
     * called again.
     */
    @Override
    public void abandon() {
        super.abandon();
        doc = null;
        dbf = null;
        parser = null;
        elements = null;
        contentsAvailable = false;
    }

    /**
     * Create a representation from an XML file. This parses the XML file into
     * an internal DOM structure and generates a list of elements that can be
     * stepped through. Errors will occur if the file is not valid XML, or if
     * the file was valid XML, but not valid according to the schema.
     *
     * @param file XML file to be parsed and validated.
     * @param schemaFile a file containing the XML schema
     * @return false if the representation could not be constructed
     */
    final boolean parse(Path file, Path schemaFile) {
        SchemaFactory sf;
        Schema schema;
        Validator validator;
        Element e;
        String av;

        // sanity check...
        assert (file != null);
        assert (schemaFile != null);

        if (contentsAvailable) {
            LOG.log(Level.WARNING, VEOFailure.getMessage(CLASSNAME, "parse", 1, id, "Calling parse() twice"));
            return false;
        }

        // check that the file exists and is an ordinary file
        if (!Files.exists(file)) {
            addError(new VEOFailure(CLASSNAME, "parse", 2, id, "File '" + file.toString() + "' does not exist"));
            return false;
        }
        if (!Files.isRegularFile(file)) {
            addError(new VEOFailure(CLASSNAME, "parse", 3, id, "File '" + file.toString() + "' is not a regular file"));
            return false;
        }

        // parse the schema and construct a validator
        sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            schema = sf.newSchema(schemaFile.toFile());
        } catch (SAXException se) {
            addError(new VEOFailure(CLASSNAME, "parse", 4, id, "Failed to parse schema '" + schemaFile.toString() + "'", se));
            return false;
        }
        validator = schema.newValidator();

        // parse the file
        doc = null;
        parser.setErrorHandler(this);
        try {
            InputSource is = new InputSource(new FileInputStream(file.toFile()));
            is.setEncoding("UTF-8");
            doc = parser.parse(is);
        } catch (SAXParseException spe) {
            addError(new VEOFailure(CLASSNAME, "parse", 5, id, "Parse error when parsing file '" + spe.getSystemId() + "' (line " + spe.getLineNumber() + " column " + spe.getColumnNumber() + ")", spe));
            return false;
        } catch (SAXException se) {
            addError(new VEOFailure(CLASSNAME, "parse", 6, id, "Problem when parsing file", se));
            return false;
        } catch (IOException ioe) {
            addError(new VEOFailure(CLASSNAME, "parse", 7, id, "System error when parsing file '" + file.toString() + "'", ioe));
            return false;
        }

        // check the overall properties of the XML file. These warnings are
        // unlikely to occur; the XML prolog (?xml version="1.0 encoding="UTF-8"?>
        // is optional. If not present, or only partially present, the defaults
        // are 1.0 and UTF-8, so these tests will almost always be false. If the
        // version is anything other than 1.0, the parser will complain (and not
        // get to this test). The tests are just for completeness.
        if (!doc.getXmlVersion().equals("1.0")) {
            addWarning(new VEOFailure(CLASSNAME, "parse", 8, id, "Problem in reading XML file. xml version must be '1.0' not " + doc.getXmlVersion()));
        }
        if (!doc.getInputEncoding().equals("UTF-8")) {
            addWarning(new VEOFailure(CLASSNAME, "parse", 9, id, "Problem in reading XML file. Encoding must be 'UTF-8' not " + doc.getInputEncoding()));
        }

        // check that the root (document) element contains the necessary
        // namespace declarations.
        e = doc.getDocumentElement();
        /* xsd/xsi namespaces are not used
        if ((av = e.getAttribute("xmlns:xsd")).equals("")) {
            addError(new VEOFailure(CLASSNAME, "parse", 10, id, "Root element does not contain attribute definition 'xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\""));
        } else if (!av.equals("http://www.w3.org/2001/XMLSchema")) {
            addError(new VEOFailure(CLASSNAME, "parse", 11, id, "Root element defines attribute xmlns:xsd as '"+av+"' not \"http://www.w3.org/2001/XMLSchema\""));
        }
        if ((av = e.getAttribute("xmlns:xsi")).equals("")) {
            addError(new VEOFailure(CLASSNAME, "parse", 12, id, "Root element does not contain attribute definition 'xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""));
        } else if (!av.equals("http://www.w3.org/2001/XMLSchema-instance")) {
            addError(new VEOFailure(CLASSNAME, "parse", 13, id, "Root element defines attribute xmlns:xsi as '"+av+"' not \"http://www.w3.org/2001/XMLSchema-instance\""));
        }
         */
        if ((av = e.getAttribute("xmlns:vers")).equals("")) {
            addError(new VEOFailure(CLASSNAME, "parse", 14, id, "Root element does not contain attribute definition 'xmlns:vers=\"http://www.prov.vic.gov.au/VERS\""));
        } else if (!av.equals("http://www.prov.vic.gov.au/VERS")) {
            addWarning(new VEOFailure(CLASSNAME, "parse", 15, id, "Root element defines attribute xmlns:vers as '" + av + "' not \"http://www.prov.vic.gov.au/VERS\""));
        }

        // validate the DOM tree against the schema
        try {
            validator.validate(new DOMSource(doc));
        } catch (SAXException se) {
            addError(new VEOFailure(CLASSNAME, "parse", 16, id, "Error when validating " + file.getFileName().toString() + " against schema '" + schemaFile.toString() + "'", se));
            return false;
        } catch (IOException ioe) {
            addError(new VEOFailure(CLASSNAME, "parse", 17, id, "System error when validating XML", ioe));
            return false;
        }

        // get list of elements
        elements = doc.getElementsByTagNameNS("*", "*");
        currentElement = 0;

        contentsAvailable = true;
        return true;
    }

    /**
     * callback for warnings generated by SAX parsing of XML
     *
     * @param e the Parse Error
     * @throws SAXException if a SAX parsing problem was detected
     */
    @Override
    public void warning(SAXParseException e) throws SAXException {
        addWarning(new VEOFailure(CLASSNAME, "warning", 1, id, "Warning when parsing file", e));
    }

    /**
     * callback for errors generated by SAX parsing of XML
     *
     * @param e the Parse Error
     * @throws SAXException if a SAX parsing problem was detected
     */
    @Override
    public void error(SAXParseException e) throws SAXException {
        addError(new VEOFailure(CLASSNAME, "error", 1, id, "Error when parsing file", e));
    }

    /**
     * callback for fatal errors generated by SAX parsing of XML
     *
     * @param e the Parse Error
     * @throws SAXException if a SAX parsing problem was detected
     */
    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        throw new SAXException(e.getMessage());
    }

    /**
     * Test if valid content is available.
     *
     * @return true if data can be examined
     */
    final public boolean contentsAvailable() {
        return contentsAvailable;
    }

    /**
     * Go to the root (first) element of the XML document.
     */
    final public void gotoRootElement() {
        assert (contentsAvailable);
        currentElement = 0;
    }

    /**
     * Step to the next element of the XML document in document order.
     */
    final public void gotoNextElement() {
        assert (contentsAvailable);
        currentElement++;
    }

    /**
     * Get the index of the current element. This may be beyond the list of
     * elements
     *
     * @return the index
     */
    final public int getCurrentElementIndex() {
        assert (contentsAvailable);
        return currentElement;
    }

    /**
     * Check if at end of list of elements
     *
     * @return true if at or beyond current element
     */
    final public boolean atEnd() {
        assert (contentsAvailable);
        if (elements == null) {
            return true;
        }
        return currentElement >= elements.getLength();
    }

    /**
     * Scan forward through the list of elements for the next sibling element of
     * the current element. Will return true if a sibling element was found at
     * this level, otherwise false
     *
     * @return true if the skip succeeded
     * @throws IndexOutOfBoundsException if we are already beyond the end of the
     * list of elements
     * @throws VEOError if a sibling was supposed to exist, but we didn't find
     * it in the list of elements
     */
    final public boolean gotoSibling() {
        Node next;

        // sanity check...
        assert (contentsAvailable);

        // get the next element at this level (or parent level if none at this level)
        next = getCurrentElement();
        do {
            next = next.getNextSibling();
        } while (next != null && next.getNodeType() != Node.ELEMENT_NODE);

        // return if no next sibling element at this level
        if (next == null) {
            return false;
        }

        // otherwise, find the sibling element in our list of elements
        do {
            currentElement++;
        } while (currentElement < elements.getLength() && elements.item(currentElement) != next);

        // if it was not found, complain
        return !atEnd();
    }

    /**
     * Scan forward through the list of elements for the next sibling element of
     * the parent element. Will return true if a sibling element was found at
     * this level, otherwise false
     *
     * @return true if the skip succeeded
     * @throws IndexOutOfBoundsException if we are already beyond the end of the
     * list of elements
     * @throws VEOError if a sibling was supposed to exist, but we didn't find
     * it in the list of elements
     */
    final public boolean gotoParentSibling() {
        Node next;

        // sanity check...
        assert (contentsAvailable);

        // get the parent node
        next = getCurrentElement().getParentNode();
        do {
            while (next.getNextSibling() == null) {
                next = next.getParentNode();
                if (next == null) {
                    currentElement = elements.getLength();
                    return false;
                    // throw new IndexOutOfBoundsException("Ran out of elements when consuming content");
                }
            }
            next = next.getNextSibling();
        } while (next.getNodeType() != Node.ELEMENT_NODE);

        // otherwise, find the sibling element in our list of elements
        do {
            currentElement++;
        } while (currentElement < elements.getLength() && elements.item(currentElement) != next);

        // if it was not found, complain
        return !atEnd();
    }

    /**
     * Get the current element.
     *
     * @return the current element
     */
    final public Element getCurrentElement() {
        assert (contentsAvailable);
        assert (elements != null);
        assert (currentElement <= elements.getLength());
        return (Element) elements.item(currentElement);
    }

    /**
     * Check if the current element has the specified tag name.
     *
     * @param tagName the name of the tag
     * @return true if the current element has the tag name.
     * @throws IndexOutOfBoundsException if there are no elements, or all have
     * been processed
     * @throws VEOError if the parse failed and no elements are available
     */
    final public boolean checkElement(String tagName) {
        Element e;

        assert (contentsAvailable);
        e = getCurrentElement();
        if (e == null) {
            return false;
        }
        return e.getTagName().equals(tagName);
    }

    /**
     * Get an attribute from the current element.
     *
     * @param attributeName the attribute to return
     * @return a string containing the attribute
     * @throws IndexOutOfBoundsException if there are no elements, or all have
     * been processed
     * @throws VEOError if the parse failed and no elements are available
     */
    final public String getAttribute(String attributeName) {
        Element e;

        assert (contentsAvailable);
        e = getCurrentElement();
        if (e == null) {
            return null;
        }
        return e.getAttribute(attributeName);
    }

    /**
     * Set an attribute in the current element. This is used to force
     * correctness so processing can continue
     *
     * @param attrName the attribute to set
     * @param attrVal the attribute value desired
     * @throws IndexOutOfBoundsException if there are no elements, or all have
     * been processed
     * @throws VEOError if the parse failed and no elements are available
     */
    final public void setAttribute(String attrName, String attrVal) {
        Element e;

        assert (contentsAvailable);
        e = getCurrentElement();
        if (e == null) {
            return;
        }
        e.setAttribute(attrName, attrVal);
    }

    /**
     * Get the value associated with the current element. This may be null if
     * the element has no text associated with it. The text returned is trimmed
     * of whitespace at the start and end.
     *
     * @return a string containing the complete value
     */
    final public String getTextValue() {
        Node n;

        assert (contentsAvailable);
        assert (elements != null);

        // get first child of this element
        n = elements.item(currentElement).getFirstChild();

        // get text, including text from any adjacent nodes
        // ASSUMES that the element only contains Text nodes
        if (n != null && n.getNodeType() == Node.TEXT_NODE) {
            return ((Text) n).getWholeText().trim();
        }

        // no text, so return null
        return null;
    }

    /**
     * Generate a representation of this object as a string
     *
     * @return the representation
     */
    @Override
    public String toString() {
        return "";
    }

    /**
     * Print the content of a DOM node (and its children).
     *
     * @param n the node
     * @param depth indent
     * @return String containing the node (and its children, if any)
     */
    final public static String prettyPrintNode(Node n, int depth) {
        StringBuffer sb;
        NodeList nl;
        int i;
        String v;

        if (n == null) {
            return "";
        }
        sb = new StringBuffer();
        switch (n.getNodeType()) {
            case 1:
                for (i = 0; i < depth; i++) {
                    sb.append(' ');
                }
                sb.append("Element: ");
                sb.append(n.getNodeName());
                sb.append("\n");
                if (n.hasChildNodes()) {
                    nl = n.getChildNodes();
                    for (i = 0; i < nl.getLength(); i++) {
                        sb.append(prettyPrintNode(nl.item(i), depth + 1));
                    }
                }
                break;
            case 3:
                v = n.getNodeValue();
                if (v == null || v.trim().equals("")) {
                    break;
                }
                for (i = 0; i < depth; i++) {
                    sb.append(' ');
                }
                sb.append("Text: '");
                sb.append(v.trim());
                sb.append("'\n");
                break;
            case Node.ATTRIBUTE_NODE:
                v = n.getNodeValue();
                if (v == null || v.trim().equals("")) {
                    break;
                }
                for (i = 0; i < depth; i++) {
                    sb.append(' ');
                }
                sb.append("Name: '");
                sb.append(n.getNodeName());
                sb.append("' Value: '");
                sb.append(v.trim());
                sb.append("'\n");
                break;
            default:
                sb.append(n.getNodeType());
                sb.append(" ");
                sb.append(n.getNodeValue());
                sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Diagnostic routine to dump the internal DOM representation of the
     * document.
     *
     * @return String containing the DOM representation
     */
    final public String dumpDOM() {

        assert (contentsAvailable);
        return dumpNode(elements.item(0), 0);
    }

    /**
     * Print the content of a DOM node (and its children).
     *
     * @param n the node
     * @param depth indent
     * @return String containing the node (and its children, if any)
     */
    final public static String dumpNode(Node n, int depth) {
        StringBuffer sb;
        NodeList nl;
        int i, j;
        String v;
        NamedNodeMap nnm;
        Node a;

        if (n == null) {
            return "";
        }
        sb = new StringBuffer();
        for (i = 0; i < depth; i++) {
            sb.append(' ');
        }
        sb.append("Node: ");
        sb.append(n.getNodeName());
        sb.append("(Type: ");
        sb.append(n.getNodeType());
        sb.append(") ");
        sb.append(n.getNamespaceURI());
        v = n.getNodeValue();
        if (v != null) {
            sb.append("'");
            sb.append(v.trim());
            sb.append("'");
        } else {
            sb.append("<null>");
        }
        sb.append("\n");
        nnm = n.getAttributes();
        if (nnm != null) {
            for (j = 0; j < nnm.getLength(); j++) {
                a = nnm.item(j);
                for (i = 0; i < depth + 1; i++) {
                    sb.append(' ');
                }
                sb.append("Attribute: ");
                sb.append(a.getNodeName());
                sb.append("(Type: ");
                sb.append(a.getNodeType());
                sb.append(") ");
                v = a.getNodeValue();
                if (v != null) {
                    sb.append("'");
                    sb.append(v.trim());
                    sb.append("'");
                } else {
                    sb.append("<null>");
                }
                sb.append("\n");
            }
        }
        if (n.hasChildNodes()) {
            nl = n.getChildNodes();
            for (i = 0; i < nl.getLength(); i++) {
                sb.append(dumpNode(nl.item(i), depth + 1));
            }
        }
        return sb.toString();
    }
}
