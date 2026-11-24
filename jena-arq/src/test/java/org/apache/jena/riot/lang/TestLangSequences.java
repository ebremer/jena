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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.out.NodeFmtLib;
import org.apache.jena.riot.system.Checker;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.IsoMatcher;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/**
 * RDF sequences: {@code (e1 e2)} in Turtle, TriG and N-Triples is one sequence term,
 * not a ladder of {@code rdf:first}/{@code rdf:rest} triples.
 */
public class TestLangSequences {

    private static Graph parse(String str, Lang lang) {
        return RDFParser.fromString(str, lang).toGraph();
    }

    private static Node intLit(String lex) {
        return NodeFactory.createLiteralDT(lex, XSDDatatype.XSDinteger);
    }

    private static String write(Graph graph, Lang lang) {
        return RDFWriter.source(graph).lang(lang).asString();
    }

    @Test public void turtle_object_sequence_is_one_triple() {
        Graph g = parse("<http://x> <http://p> (1 2) .", Lang.TURTLE);
        assertEquals(1, g.size());
        Triple t = g.find().next();
        Node o = t.getObject();
        assertTrue(o.isNodeSeq());
        assertEquals(List.of(intLit("1"), intLit("2")), o.getSeq());
        assertFalse(g.contains(Node.ANY, RDF.Nodes.first, Node.ANY));
        assertFalse(g.contains(Node.ANY, RDF.Nodes.rest, Node.ANY));
    }

    @Test public void empty_sequence_is_not_nil() {
        Graph g = parse("<http://x> <http://p> () .", Lang.TURTLE);
        Node o = g.find().next().getObject();
        assertTrue(o.isNodeSeq());
        assertEquals(0, o.getSeq().size());
        assertNotEquals(RDF.Nodes.nil, o);
    }

    @Test public void sequence_elements_nested_and_blank_node_property_list() {
        Graph g = parse("<http://x> <http://p> ((1 2) [ <http://q> 3 ]) .", Lang.TURTLE);
        assertEquals(2, g.size());
        Node o = g.find(NodeFactory.createURI("http://x"), Node.ANY, Node.ANY).next().getObject();
        assertTrue(o.isNodeSeq());
        List<Node> elts = o.getSeq();
        assertEquals(2, elts.size());
        assertTrue(elts.get(0).isNodeSeq());
        assertEquals(List.of(intLit("1"), intLit("2")), elts.get(0).getSeq());
        assertTrue(elts.get(1).isBlank());
        assertTrue(g.contains(elts.get(1), NodeFactory.createURI("http://q"), intLit("3")));
    }

    @Test public void sequence_as_subject() {
        Graph g = parse("(1 2) <http://p> <http://o> .", Lang.TURTLE);
        assertEquals(1, g.size());
        assertTrue(g.find().next().getSubject().isNodeSeq());
    }

    @Test public void trig_sequence() {
        DatasetGraph dsg = RDFParser.fromString("<http://g> { <http://x> <http://p> (1 2) }", Lang.TRIG).toDatasetGraph();
        Graph g = dsg.getGraph(NodeFactory.createURI("http://g"));
        assertEquals(1, g.size());
        assertTrue(g.find().next().getObject().isNodeSeq());
    }

    @Test public void ntriples_sequence() {
        Graph g = parse("<http://x> <http://p> ( \"a\" <http://y> ( ) ) .", Lang.NTRIPLES);
        assertEquals(1, g.size());
        Node o = g.find().next().getObject();
        assertTrue(o.isNodeSeq());
        assertEquals(3, o.getSeq().size());
        assertTrue(o.getSeq().get(2).isNodeSeq());
    }

    @Test public void sequence_terms_pass_the_checker() {
        Graph g = parse("(1 2) <http://p> (<http://o> (3)) .", Lang.TURTLE);
        Triple t = g.find().next();
        assertTrue(Checker.check(t.getSubject()));
        assertTrue(Checker.check(t.getObject()));
        assertTrue(Checker.checkTriple(t));
    }

    @Test public void turtle_round_trip() {
        Graph g1 = parse("PREFIX : <http://example/>\n:x :p (1 (2 3) :y \"s\"@en) ; :q () .\n(:a) :r :z .", Lang.TURTLE);
        String ttl = write(g1, Lang.TURTLE);
        Graph g2 = parse(ttl, Lang.TURTLE);
        assertTrue(IsoMatcher.isomorphic(g1, g2), ttl);
    }

    @Test public void ntriples_round_trip() {
        Graph g1 = parse("PREFIX : <http://example/>\n:x :p (1 (2 3) :y) .", Lang.TURTLE);
        String nt = write(g1, Lang.NTRIPLES);
        assertTrue(nt.contains("( "), nt);
        Graph g2 = parse(nt, Lang.NTRIPLES);
        assertTrue(IsoMatcher.isomorphic(g1, g2));
    }

    @Test public void legacy_first_rest_ladders_are_not_abbreviated() {
        // Data in the old encoding stays in the old encoding: the writer must not turn it into "( )"
        // because that would be re-read as a sequence term.
        String nt = "<http://x> <http://p> _:b0 .\n"
                  + "_:b0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#first> \"a\" .\n"
                  + "_:b0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#rest> <http://www.w3.org/1999/02/22-rdf-syntax-ns#nil> .\n";
        Graph g1 = parse(nt, Lang.NTRIPLES);
        String ttl = write(g1, Lang.TURTLE);
        Graph g2 = parse(ttl, Lang.TURTLE);
        assertEquals(3, g2.size(), ttl);
        assertTrue(g2.contains(Node.ANY, RDF.Nodes.first, Node.ANY), ttl);
        assertTrue(IsoMatcher.isomorphic(g1, g2));
    }

    @Test public void node_formatting() {
        Node seq = NodeFactory.createSeqNode(intLit("1"), NodeFactory.createURI("http://x"), NodeFactory.createSeqNode());
        // Jena 6 writes N-Triples terms in full (5.0's strNT used the Turtle formatter).
        assertEquals("( \"1\"^^<http://www.w3.org/2001/XMLSchema#integer> <http://x> () )", NodeFmtLib.strNT(seq));
        assertEquals("( 1 <http://x> () )", NodeFmtLib.strTTL(seq));
        assertEquals("()", NodeFactory.createSeqNode().toString());
    }

    @Test public void equality_and_concreteness() {
        Node s1 = NodeFactory.createSeqNode(intLit("1"), intLit("2"));
        Node s2 = NodeFactory.createSeqNode(List.of(intLit("1"), intLit("2")));
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
        assertNotEquals(s1, NodeFactory.createSeqNode(intLit("2"), intLit("1")));
        assertTrue(s1.isConcrete());
        assertFalse(NodeFactory.createSeqNode(intLit("1"), NodeFactory.createVariable("x")).isConcrete());
    }
    // jena-core's GraphMatcher (Graph.isIsomorphicWith) maps the blank nodes inside sequences.
    @Test public void isomorphism_maps_blank_nodes_in_sequences() {
        String prefix = "PREFIX : <http://example/>\n";
        Graph g1 = parse(prefix+":a :p ( :s [ :q 1 ] ) .", Lang.TURTLE);
        Graph g2 = parse(prefix+":a :p ( :s _:x ) . _:x :q 1 .", Lang.TURTLE);
        Graph g3 = parse(prefix+":a :p ( :s _:x ) . _:y :q 1 .", Lang.TURTLE);
        assertTrue(g1.isIsomorphicWith(g2));
        assertFalse(g1.isIsomorphicWith(g3));
        Graph same = parse(prefix+":a :p ( _:x _:x ) .", Lang.TURTLE);
        Graph different = parse(prefix+":a :p ( _:x _:y ) .", Lang.TURTLE);
        assertFalse(same.isIsomorphicWith(different));
        assertTrue(same.isIsomorphicWith(parse(prefix+":a :p ( _:z _:z ) .", Lang.TURTLE)));
    }

    // The pretty Turtle writer must not write a blank node used only inside a sequence as a
    // free-standing [ ... ] while referring to it by label in the sequence.
    @Test public void turtle_round_trip_blank_node_in_sequence() {
        Graph g1 = parse("PREFIX : <http://example/>\n:a :q4 ( :s [ :p :o ] ) .", Lang.TURTLE);
        String ttl = write(g1, Lang.TURTLE);
        assertTrue(g1.isIsomorphicWith(parse(ttl, Lang.TURTLE)), ttl);
    }
}
