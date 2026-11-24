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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.iterator.Abortable;
import org.apache.jena.sparql.engine.iterator.IterAbortable;
import org.apache.jena.sparql.expr.nodevalue.NodeFunctions;

public class SolverLib {

    /**
     * Create an abortable iterator, storing it in the killList.
     * Just return the input iterator if killList is null.
     */
    public static <T> Iterator<T> makeAbortable(Iterator<T> iter, List<Abortable> killList, AtomicBoolean cancel) {
        if ( killList == null )
            return iter;
        IterAbortable<T> k = IterAbortable.wrap(iter, cancel);
        killList.add(k);
        return k;
    }

    /**
     * Test whether a triple has an triple term as one of its components.
     */
    public static boolean tripleHasNodeTriple(Triple triple) {
        return triple.getSubject().isTripleTerm()
               /*|| triple.getPredicate().isTripleTerm()*/
               || triple.getObject().isTripleTerm();
    }

    /**
     * Test whether a quad has an triple term as one of its components.
     */
    public static boolean quadHasNodeTriple(Quad quad) {
        return quad.getSubject().isTripleTerm()
               // || triple.getPredicate().isTripleTerm()
               // || quad.getGraph().isTripleTerm()
               || quad.getObject().isTripleTerm();
    }

    /**
     * Test whether a triple has a compound term with variables as one of its
     * components: a triple term (RDF-star) or a sequence with variables or gaps inside.
     */
    public static boolean tripleHasEmbTripleWithVars(Triple triple) {
        return isCompoundWithVars(triple.getSubject())
               // || isCompoundWithVars(triple.getPredicate())
               || isCompoundWithVars(triple.getObject());
    }

    /**
     * Test whether a quad has a compound term with variables as one of its
     * components: a triple term (RDF-star) or a sequence with variables or gaps inside.
     */
    public static boolean quadHasEmbTripleWithVars(Quad quad) {
        return isCompoundWithVars(quad.getSubject())
               // || isCompoundWithVars(triple.getPredicate())
               // || isCompoundWithVars(triple.getGraph())
               || isCompoundWithVars(quad.getObject());
    }

    private static boolean isCompoundWithVars(Node node) {
        if ( node.isTripleTerm() || node.isNodeSeq() )
            return ! node.isConcrete();
        return false;
    }

    /** Test equality of two concrete teams. */
    public static boolean sameTermAs(Node node1, Node node2) {
        return NodeFunctions.sameTerm(node1, node2);
    }

    /**
     * The term to find in the data for a pattern node {@code xNode}, given it after
     * substitution, {@code tNode}. A sequence pattern is not looked up by its
     * substituted form: {@code ( ?x )} with {@code ?x} bound also matches
     * {@code ( undef )}.
     */
    public static Node nodeTopLevel(Node xNode, Node tNode) {
        return hasSeqWithVars(xNode) ? Node.ANY : nodeTopLevel(tNode);
    }

    private static boolean hasSeqWithVars(Node node) {
        if ( node.isNodeSeq() )
            return ! node.isConcrete();
        if ( node.isTripleTerm() ) {
            Triple t = node.getTriple();
            return hasSeqWithVars(t.getSubject()) || hasSeqWithVars(t.getPredicate()) || hasSeqWithVars(t.getObject());
        }
        return false;
    }

    /**
     * Convert a pattern node into ANY, or leave as a constant term. Any compound
     * term with a variable or a gap, that is {@code {@literal <<?var>>}},
     * {@code (?var)} or {@code ( ... )}, becomes an ANY.
     */
    public static Node nodeTopLevel(Node node) {
        // Public so TDB code can use it.
        if ( Var.isVar(node) )
            return Node.ANY;
        if ( node.isTripleTerm() || node.isNodeSeq() ) { //|| node.isNodeGraph() )
            if ( ! node.isConcrete() )
                // Nested variables.
                return Node.ANY;
        }
        return node;
    }
}
