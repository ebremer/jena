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

package org.apache.jena.graph;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import org.apache.jena.shared.PrefixMapping;

/**
 * RDF sequence term.
 * <p>
 * A sequence is an ordered collection of RDF terms that is itself an RDF term: it
 * can be the subject or object of a triple and an element of another sequence. It
 * replaces the {@code rdf:first}/{@code rdf:rest} encoding of collections:
 * {@code <s> <p> (1 2)} is one triple whose object is the sequence of the literals
 * 1 and 2, not a ladder of blank nodes.
 * <p>
 * A sequence is concrete when all its elements are concrete; a sequence with
 * variables in it is a pattern (SPARQL). An element may be {@link Node#UNDEF}, an
 * empty slot: it counts towards the size and has no value.
 */
public class Node_Seq extends Node {

    private final List<Node> elements;

    /*package*/ Node_Seq(List<Node> elements) {
        Objects.requireNonNull(elements, "elements");
        // Rejects null elements, copies, and is unmodifiable.
        this.elements = List.copyOf(elements);
    }

    /** The elements, in order. The list is unmodifiable. */
    @Override
    public List<Node> getSeq() {
        return elements;
    }

    /** Number of elements. */
    public int size() {
        return elements.size();
    }

    /** Element at a 0-based position. */
    public Node get(int i) {
        return elements.get(i);
    }

    @Override
    public boolean isNodeSeq() {
        return true;
    }

    @Override
    public boolean isConcrete() {
        for ( Node n : elements ) {
            if ( !n.isConcrete() )
                return false;
        }
        return true;
    }

    @Override
    public Object visitWith(NodeVisitor v) {
        return v.visitSeq(this, elements);
    }

    @Override
    public int hashCode() {
        return Node.hashNodeSeq + elements.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        Node_Seq other = (Node_Seq)obj;
        return elements.equals(other.elements);
    }

    @Override
    public String toString(PrefixMapping pm) {
        if ( elements.isEmpty() )
            return "()";
        StringJoiner sj = new StringJoiner(" ", "( ", " )");
        for ( Node n : elements )
            sj.add(n.toString(pm));
        return sj.toString();
    }

    @Override
    public String toString() {
        if ( elements.isEmpty() )
            return "()";
        StringJoiner sj = new StringJoiner(" ", "( ", " )");
        for ( Node n : elements )
            sj.add(n.toString());
        return sj.toString();
    }
}
