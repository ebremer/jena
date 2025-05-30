/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.riot.lang;

import static org.apache.jena.riot.lang.ReaderTriX.State.*;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.jena.atlas.web.ContentType;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.impl.XMLLiteralType;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Node_Marker;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.ReaderRIOT;
import org.apache.jena.riot.ReaderRIOTFactory;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandler;
import org.apache.jena.riot.system.ParserProfile;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.writer.StreamWriterTriX;
import org.apache.jena.riot.writer.WriterTriX;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.util.JenaXMLInput;
import org.apache.jena.vocabulary.RDF;

/** Read TriX.
 *  See {@link TriX} for details.
 * @see TriX
 * @see WriterTriX
 * @see StreamWriterTriX
 */
public class ReaderTriX implements ReaderRIOT {

    public static ReaderRIOTFactory factory = (Lang language, ParserProfile profile) -> {
        return new ReaderTriX(profile, profile.getErrorHandler());
    };

    // DTD for TrIX : The schema is a much longer.
/*
<!-- TriX: RDF Triples in XML -->
<!ELEMENT TriX (graph*)>
<!ATTLIST TriX xmlns CDATA #FIXED "http://www.w3.org/2004/03/trix/trix-1/">
<!ELEMENT graph (uri*, triple*)>
<!ELEMENT triple ((id|uri|plainLiteral|typedLiteral), uri, (id|uri|plainLiteral|typedLiteral))>
<!ELEMENT id (#PCDATA)>
<!ELEMENT uri (#PCDATA)>
<!ELEMENT plainLiteral (#PCDATA)>
<!ATTLIST plainLiteral xml:lang CDATA #IMPLIED>
<!ELEMENT typedLiteral (#PCDATA)>
<!ATTLIST typedLiteral datatype CDATA #REQUIRED>

 Extended for RDF-star.
<!ELEMENT triple ((id|uri|plainLiteral|typedLiteral|triple), uri, (id|uri|plainLiteral|typedLiteral|triple))>
     */

    private final ErrorHandler errorHandler;
    private final ParserProfile profile;

    public ReaderTriX(ParserProfile profile, ErrorHandler errorHandler) {
        this.profile = profile;
        this.errorHandler = errorHandler;
    }

    @Override
    public void read(InputStream in, String baseURI, ContentType ct, StreamRDF output, Context context) {
        XMLStreamReader xReader;
        try {
            xReader = JenaXMLInput.newXMLStreamReader(in);
        } catch (XMLStreamException e) { throw new RiotException("Can't initialize StAX parsing engine", e); }
        read(xReader,  baseURI, output);
    }

    @Override
    public void read(Reader reader, String baseURI, ContentType ct, StreamRDF output, Context context) {
        XMLStreamReader xReader;
        try {
            xReader = JenaXMLInput.newXMLStreamReader(reader);
        } catch (XMLStreamException e) { throw new RiotException("Can't initialize StAX parsing engine", e); }
        read(xReader,  baseURI, output);
    }

    private static String nsRDF = RDF.uri;
    private static String nsXSD = XSDDatatype.XSD; // No "#"
    private static String nsXML = "http://www.w3.org/XML/1998/namespace";
    private static String rdfXMLLiteral = XMLLiteralType.XMLLiteralTypeURI;

    // State TRIPLE is "asserted triple" - a triple that will go into the output.
    // State TRIPLE_TERM is a triple term (<<()>>) that will be term in another triple/quad.
    enum State { OUTER, TRIX, GRAPH, TRIPLE, TRIPLE_TERM }

    private static class NodeMarker extends Node_Marker {
        protected NodeMarker(String label) {
            super(label);
        }
    }

    private static Node nAssertedTriple = new NodeMarker("asserted triple");
    private static Node nTripleTerm = new NodeMarker("triple term");

    private void read(XMLStreamReader parser, String baseURI, StreamRDF output) {
        State state = OUTER;
        Node g = null;
        Deque<Node> terms = new ArrayDeque<>();
        // Depth of triple term nesting. 0 - not in a triple term
        int tripleTermDepth = 0;
        try {
            while(parser.hasNext()) {
                int event = parser.next();
                switch (event) {
                    case XMLStreamConstants.NAMESPACE:
                        break;
                    case XMLStreamConstants.START_DOCUMENT :
                        break;
                    case XMLStreamConstants.END_DOCUMENT :
                        if ( state != OUTER )
                            staxError(parser.getLocation(), "End of document while processing XML element");
                        return;
                    case XMLStreamConstants.START_ELEMENT : {
                        String tag = parser.getLocalName();

                        switch (tag) {
                            case TriX.tagTriX:
                            case TriX.tagTriXAlt:
                                if ( state != OUTER )
                                    staxErrorOutOfPlaceElement(parser);
                                state = TRIX;
                                break;
                            // structure
                            case TriX.tagGraph:
                                if ( state != TRIX )
                                    staxErrorOutOfPlaceElement(parser);
                                // URI?
                                state = GRAPH;
                                break;
                            case TriX.tagTriple: {
                                switch (state) {
                                    // Asserted triple
                                    case GRAPH :
                                        state = TRIPLE;
                                        push(terms, nAssertedTriple);
                                        break;
                                    // If TRIPLE_TERM,  nested triple term
                                    case TRIPLE:
                                        state = TRIPLE_TERM;
                                        // fall through.
                                    case TRIPLE_TERM:
                                        tripleTermDepth++;
                                        push(terms, nTripleTerm);
                                        break;
                                    default: staxErrorOutOfPlaceElement(parser);
                                }
                                break;
                            }
                            // Can occur in <graph> and <triple>
                            case TriX.tagId:
                            case TriX.tagQName:
                            case TriX.tagURI: {
                                if ( state != GRAPH && state != TRIPLE && state != TRIPLE_TERM )
                                    staxErrorOutOfPlaceElement(parser);
                                Node n = term(parser, profile);

                                if ( state == GRAPH ) {
                                    if ( g != null )
                                        staxError(parser.getLocation(), "Duplicate graph name");
                                    g = n;
                                    if ( g.isLiteral() )
                                        staxError(parser.getLocation(), "graph name is a literal");
                                }
                                else
                                    // state TRIPLE
                                    push(terms, n);
                                break;
                            }

                            case TriX.tagPlainLiteral:
                            case TriX.tagTypedLiteral: {
                                if ( state != TRIPLE )
                                    staxErrorOutOfPlaceElement(parser);
                                Node n = term(parser, profile);
                                push(terms, n);
                                break;
                            }
                            default:
                                staxError(parser.getLocation(), "Unrecognized XML element: "+qnameAsString(parser.getName()));
                                break;
                        }
                        break;
                    }
                    case XMLStreamConstants.END_ELEMENT : {
                        String tag = parser.getLocalName();
                        switch(tag) {
                            case TriX.tagTriple: {
                                int line = parser.getLocation().getLineNumber();
                                int col = parser.getLocation().getColumnNumber();
                                try {
                                    // Reverse order
                                    Node o = pop(terms);
                                    if ( o instanceof NodeMarker )
                                        staxError(parser.getLocation(), "Too few terms (zero) for a triple.");

                                    Node p = pop(terms);
                                    if ( p instanceof NodeMarker )
                                        staxError(parser.getLocation(), "Too few terms (one) for a triple.");

                                    Node s = pop(terms);
                                    if ( s instanceof NodeMarker )
                                        staxError(parser.getLocation(), "Too few terms (two) for a triple.");

                                    Node marker = pop(terms);
                                    if ( ! ( marker instanceof NodeMarker ) )
                                        staxError(parser.getLocation(), "Too many terms for a triple.");

                                    if ( p.isLiteral() )
                                        staxError(parser.getLocation(), "Predicate is a literal");
                                    if ( s.isLiteral() )
                                        staxError(parser.getLocation(), "Subject is a literal");

                                    switch(state) {
                                        case TRIPLE: {
                                            if ( ! nAssertedTriple.equals(marker) )
                                                // Internal error.
                                                staxError(parser.getLocation(), "Misaligned triple");
                                            //System.out.println("Asserted: Terms: "+terms.size());

                                            // Emit asserted quad or triple.
                                            if ( g == null ) {
                                                Triple t = profile.createTriple(s, p, o, line, col);
                                                output.triple(t);
                                            }
                                            else {
                                                if ( g.isLiteral() )
                                                    staxError(parser.getLocation(), "graph name is a literal");
                                                Quad q = profile.createQuad(g, s, p, o, line, col);
                                                output.quad(q);
                                            }
                                            // Next is either end of <graph> or another <triple>
                                            state = GRAPH;
                                            break;
                                        }

                                        case TRIPLE_TERM: {
                                            if ( ! nTripleTerm.equals(marker) )
                                                // Internal error.
                                                staxError(parser.getLocation(), "Misaligned triple term.");
                                            // Triple term.
                                            Node nt = profile.createTripleTerm(s, p, o, line, col);
                                            push(terms, nt);
                                            tripleTermDepth--;
                                            if ( tripleTermDepth == 0 )
                                                state = TRIPLE;
                                            break;
                                        }
                                        default:
                                            staxError(parser.getLocation(), "Internal error");
                                    }
                                    break;
                                } catch (NoSuchElementException ex) {
                                    staxError(parser.getLocation(), "Too few terms for a triple.");
                                }
                            }
                            case TriX.tagGraph:
                                state = TRIX;
                                g = null;
                                break;
                            case TriX.tagTriX:
                            case TriX.tagTriXAlt:
                                // We don't worry about mismatched tags.
                                state = OUTER;
                                break;
                        }
                        break;
                    }
                }
            }
            staxError("Premature end of file");
            return ;
        } catch (XMLStreamException ex) {
            staxError(parser.getLocation(), "XML error: "+ex.getMessage());
        }
    }

    private void push(Deque<Node> acc, Node term) {
        acc.push(term);
    }

    private Node pop(Deque<Node> acc) {
        return acc.pop();
    }

    private void staxErrorOutOfPlaceElement(XMLStreamReader parser) {
        staxError(parser.getLocation(), "Out of place XML element: "+tagName(parser));
    }

    private Node term(XMLStreamReader parser, ParserProfile profile) throws XMLStreamException {
        String tag = parser.getLocalName();
        int line = parser.getLocation().getLineNumber();
        int col = parser.getLocation().getColumnNumber();

        switch(tag) {
            case TriX.tagURI: {
                // Two uses!
                String x = parser.getElementText();
                Node n = profile.createURI(x, line, col);
                return n;
            }
            case TriX.tagQName: {
                String x = parser.getElementText();
                int idx = x.indexOf(':');
                if ( idx == -1 )
                    staxError(parser.getLocation(), "Expected ':' in prefixed name.  Found "+x);
                String[] y = x.split(":", 2);  // Allows additional ':'
                String prefUri = parser.getNamespaceURI(y[0]);
                String local = y[1];
                return profile.createURI(prefUri+local, line, col);
            }
            case TriX.tagId: {
                String x = parser.getElementText();
                return profile.createBlankNode(null, x, line, col);
            }
            case TriX.tagPlainLiteral: {
                // xml:lang
                int x = parser.getAttributeCount();
                if ( x > 1 )
                    // Namespaces?
                    staxError(parser.getLocation(), "Multiple attributes : only one allowed");
                String lang = null;
                if ( x == 1 )
                    lang = attribute(parser, nsXML, TriX.attrXmlLang);
                String lex = parser.getElementText();
                if ( lang == null )
                    return profile.createStringLiteral(lex, line, col);
                else
                    return profile.createLangLiteral(lex, lang, line, col);
            }
            case TriX.tagTypedLiteral: {
                int nAttr = parser.getAttributeCount();
                if ( nAttr != 1 )
                    staxError(parser.getLocation(), "Multiple attributes : only one allowed");
                String dt = attribute(parser, TriX.NS, TriX.attrDatatype);
                if ( dt == null )
                    staxError(parser.getLocation(), "No datatype attribute");
                RDFDatatype rdt = NodeFactory.getType(dt);

                String lex = (rdfXMLLiteral.equals(dt))
                    ? slurpRDFXMLLiteral(parser)
                    : parser.getElementText();
                return profile.createTypedLiteral(lex, rdt, line, col);
            }
            // Triple term done elswwhere
//            case TriX.tagTriple: {
//                Node s = term(parser, profile);
//                Node p = term(parser, profile);
//                Node o = term(parser, profile);
//                Node n = profile.createTripleTerm(s, p, o, line, col);
//                return n;
//            }
            default: {
                QName qname = parser.getName();
                staxError(parser.getLocation(), "Unrecognized tag -- "+qnameAsString(qname));
                return null;
            }
        }
    }

    private String slurpRDFXMLLiteral(XMLStreamReader parser) throws XMLStreamException {
        StringBuilder content = new StringBuilder();
        int depth = 0;

        while(parser.hasNext()) {
            int event = parser.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT: {
                    QName qname = parser.getName();
                    content.append("<");
                    content.append(qnameAsString(qname));
                    int N = parser.getNamespaceCount();
                    for ( int i = 0; i < N; i ++ ) {
                        String p = parser.getNamespacePrefix(i);
                        if ( p == null )
                            p = "xmlns";
                        else
                            p = "xmlns:"+p;
                        String v = parser.getNamespaceURI(i);
                        content.append(" ").append(p).append("=\"").append(v).append("\"");
                    }

                    N = parser.getAttributeCount();
                    for ( int i = 0; i < N; i ++ ) {
                        QName name = parser.getAttributeName(i);
                        String a = qnameAsString(name);
                        String v = parser.getAttributeValue(i);
                        content.append(" ").append(a).append("=\"").append(v).append("\"");
                    }
                    content.append(">");
                    depth++;
                    break;
                }
                case XMLStreamConstants.END_ELEMENT: {
                    depth--;
                    if ( depth == -1 ) {
                        // Close tag of typed Literal.
                        return content.toString();
                    }
                    QName qname = parser.getName();
                    String x = qnameAsString(qname);
                    content.append("</"+x+">");
                    // Final whitespace?
                    break;
                }
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                case XMLStreamConstants.SPACE:
                case XMLStreamConstants.ENTITY_REFERENCE:
                case XMLStreamConstants.PROCESSING_INSTRUCTION:
                case XMLStreamConstants.COMMENT:
                    content.append(parser.getText());
                    break;
                case XMLStreamConstants.END_DOCUMENT:
                    staxError(parser.getLocation(), "End of file");
            }
        }
        staxError(parser.getLocation(), "End of file");
        return null;
    }

    private String tagName(XMLStreamReader parser) {
        return qnameAsString(parser.getName());
    }

    private String qnameAsString(QName qname) {
        String x = qname.getPrefix();
        if ( x == null || x.isEmpty() )
            return qname.getLocalPart();
        return x+":"+qname.getLocalPart();
    }

    private String attribute(XMLStreamReader parser, String nsURI, String localname) {
        int x = parser.getAttributeCount();
        if ( x > 1 )
            // Namespaces?
            staxError(parser.getLocation(), "Multiple attributes : only one allowed : "+tagName(parser));
        if ( x == 0 )
            return null;

        String attrPX =  parser.getAttributePrefix(0);
        String attrNS =  parser.getAttributeNamespace(0);
        if ( attrNS == null )
            attrNS = parser.getName().getNamespaceURI();
        String attrLN = parser.getAttributeLocalName(0);
        if ( ! Objects.equals(nsURI, attrNS) || ! Objects.equals(attrLN, localname) ) {
            staxError(parser.getLocation(), "Unexpected attribute : "+attrPX+":"+attrLN+" at "+tagName(parser));
        }
        String attrVal = parser.getAttributeValue(0);
        return attrVal;
    }

    private void staxError(String msg) {
        staxError(-1, -1, msg);
    }

    private void staxError(Location loc, String msg) {
        staxError(loc.getLineNumber(), loc.getColumnNumber(), msg);
    }

    private void staxError(int line, int col, String msg) {
        errorHandler.error(msg, line, col);
    }
}

