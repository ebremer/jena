/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 *   SPDX-License-Identifier: Apache-2.0
 */

package org.apache.jena.sparql.engine.main.solver;

import static org.apache.jena.sparql.engine.main.solver.SolverLib.nodeTopLevel;
import static org.apache.jena.sparql.engine.main.solver.SolverLib.sameTermAs;
import static org.apache.jena.sparql.engine.main.solver.SolverLib.tripleHasEmbTripleWithVars;

import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.Node_SeqGap;
import org.apache.jena.sparql.core.Substitute;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingFactory;
import org.apache.jena.util.iterator.ExtendedIterator;

/**
 * Compound terms - triple form. Solve patterns when there is a triple term
 * (RDF-star) or a sequence with variables or gaps ({@code ...}, {@code ...?rest})
 * inside.
 */
public class SolverRX3 {

    /**
     * This constant is not public API. It is exposed only so integration testing can
     * check the value for a release build.
     */
    public static final boolean DATAPATH = true;

    public static Iterator<Binding> rdfStarTriple(Iterator<Binding> chain, Triple tPattern, ExecutionContext execCxt) {
        // Should all work without this trap for plain RDF.
        if ( DATAPATH ) {
            // No variables or gaps inside <<>> or (..)
            if ( ! tripleHasEmbTripleWithVars(tPattern) )
                // Direct to data.
                return matchData(chain, tPattern, execCxt);
        }
        return rdfStarTripleSub(chain, tPattern, execCxt);
    }

    private static Iterator<Binding> rdfStarTripleSub(Iterator<Binding> input, Triple tPattern, ExecutionContext execCxt) {
        Iterator<Binding> matches = Iter.flatMap(input, binding->rdfStarTripleSub(binding, tPattern, execCxt));
        return matches;
    }

    private static Iterator<Binding> rdfStarTripleSub(Binding input, Triple xPattern, ExecutionContext execCxt) {
        Triple tPattern = Substitute.substitute(xPattern, input);
        Node s = nodeTopLevel(xPattern.getSubject(), tPattern.getSubject());
        Node p = nodeTopLevel(xPattern.getPredicate(), tPattern.getPredicate());
        Node o = nodeTopLevel(xPattern.getObject(), tPattern.getObject());
        Graph graph = execCxt.getActiveGraph();
        ExtendedIterator<Triple> graphIter = graph.find(s, p, o);
        // Match on the pattern before substitution: a variable bound in the input is
        // still compatible with an empty slot, where a constant does not match it.
        return Iter.flatMap(graphIter, tData->matchTriple(input, tData, xPattern));
    }

    /**
     * Direct.
     * Match the graph with a triple pattern.
     * This is the accessor to the graph.
     * It assumes any compound terms have been dealt with.
     */
    private static Iterator<Binding> matchData(Iterator<Binding> chain, Triple pattern, ExecutionContext execCxt) {
        Graph g = execCxt.getActiveGraph();
        Iterator<Binding> iter = StageMatchTriple.accessTriple(chain, g, pattern, null, execCxt);
        return iter;
    }

    /**
     * Match a data triple against a triple pattern, extending the input binding.
     * There may be several results: a sequence pattern with gaps, such as
     * {@code ( ... ?x ... )}, yields one binding per way of placing the gaps.
     */
    public static Iterator<Binding> matchTriple(Binding input, Triple tData, Triple tPattern) {
        Iterator<Binding> iter = Iter.singletonIterator(input);
        iter = Iter.flatMap(iter, b->match(b, tData.getSubject(), tPattern.getSubject()));
        iter = Iter.flatMap(iter, b->match(b, tData.getPredicate(), tPattern.getPredicate()));
        iter = Iter.flatMap(iter, b->match(b, tData.getObject(), tPattern.getObject()));
        return iter;
    }

    /**
     * RDF term matcher - recurses into triple terms and sequences.
     * Returns the bindings (zero or more) that extend the input binding so that the
     * pattern matches the data.
     */
    /*package*/ static Iterator<Binding> match(Binding input, Node nData, Node nPattern) {
        if ( nPattern == null )
            return Iter.singletonIterator(input);
        if ( nData == Node.ANY )
            return Iter.singletonIterator(input);

        // An empty slot has no value to bind: a variable leaves the binding as it is, as
        // UNDEF does in VALUES. Of the constants, only undef itself matches it.
        if ( nData.isNodeUndef() )
            return ( Var.isVar(nPattern) || nPattern.isNodeUndef() ) ? Iter.singletonIterator(input) : Iter.nullIterator();

        // Shallow substitute - nested variables are seen as the structure is walked.
        nPattern = Var.lookup(input::get, nPattern);

        // nPattern.isConcrete() : either nPattern is an RDF term or is <<>> / (..) with no variables.
        if ( nPattern.isConcrete() ) {
            // No nested variables. Is data equal to pattern?
            // Term comparison.
            return sameTermAs(nData, nPattern) ? Iter.singletonIterator(input) : Iter.nullIterator();
        }

        // Easy case - nPattern is a variable.
        if ( Var.isVar(nPattern) ) {
            Var var = Var.alloc(nPattern);
            return Iter.singletonIterator(BindingFactory.binding(input, var, nData));
        }

        // nPattern is <<>> with variables. Is the data a <<>>?
        if ( nPattern.isTripleTerm() ) {
            if ( !nData.isTripleTerm() )
                return Iter.nullIterator();
            // Unpack, match components.
            return matchTriple(input, nData.getTriple(), nPattern.getTriple());
        }

        // nPattern is (..) with variables, and maybe gaps. Is the data a (..)?
        if ( nPattern.isNodeSeq() ) {
            if ( !nData.isNodeSeq() )
                return Iter.nullIterator();
            List<Node> dElts = nData.getSeq();
            List<Node> pElts = nPattern.getSeq();
            if ( hasGap(pElts) )
                return matchMembers(input, pElts, 0, dElts, 0);
            // No gaps: member by member, same length.
            if ( dElts.size() != pElts.size() )
                return Iter.nullIterator();
            Iterator<Binding> iter = Iter.singletonIterator(input);
            for ( int i = 0 ; i < pElts.size() ; i++ ) {
                final int idx = i;
                iter = Iter.flatMap(iter, b->match(b, dElts.get(idx), pElts.get(idx)));
            }
            return iter;
        }

        return Iter.nullIterator();
    }

    private static boolean hasGap(List<Node> pElts) {
        for ( Node p : pElts ) {
            if ( p instanceof Node_SeqGap )
                return true;
        }
        return false;
    }

    /**
     * Match the pattern members from {@code i} against the data members from
     * {@code j}. A gap {@code ...} takes any run of members (every run length is
     * tried); a named gap {@code ...?rest} also matches {@code ?rest} against that
     * run as a sequence, so a variable bound earlier, or repeated, must be equal to
     * it.
     */
    private static Iterator<Binding> matchMembers(Binding input, List<Node> pElts, int i, List<Node> dElts, int j) {
        if ( i == pElts.size() )
            return ( j == dElts.size() ) ? Iter.singletonIterator(input) : Iter.nullIterator();
        Node p = pElts.get(i);
        if ( p instanceof Node_SeqGap gap ) {
            // Every run from j to k, k in j..size.
            return Iter.flatMap(IntStream.rangeClosed(j, dElts.size()).iterator(), k->{
                if ( !gap.isNamed() )
                    return matchMembers(input, pElts, i+1, dElts, k);
                Node run = NodeFactory.createSeqNode(dElts.subList(j, k));
                Iterator<Binding> bound = match(input, run, gap.getVar());
                return Iter.flatMap(bound, b->matchMembers(b, pElts, i+1, dElts, k));
            });
        }
        if ( j >= dElts.size() )
            return Iter.nullIterator();
        Iterator<Binding> iter = match(input, dElts.get(j), p);
        return Iter.flatMap(iter, b->matchMembers(b, pElts, i+1, dElts, j+1));
    }
}
