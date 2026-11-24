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

package org.apache.jena.sparql.function.library.seq;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;

/** Functions on RDF sequence terms. */
public class SeqFunctions {

    /** Test whether a {@link NodeValue} is a sequence term. */
    public static NodeValue isSeq(NodeValue nv) {
        return NodeValue.booleanReturn(nv.asNode().isNodeSeq());
    }

    /**
     * The number of elements of a sequence.
     * Throws {@link ExprEvalException} if the argument is not a sequence.
     */
    public static NodeValue seqLen(NodeValue nv) {
        List<Node> elts = elements("seqLen", nv);
        return NodeValue.makeInteger(elts.size());
    }

    /**
     * The element at a 1-based position of a sequence.
     * Throws {@link ExprEvalException} if the first argument is not a sequence, the
     * second is not an integer, or the position is out of range.
     */
    public static NodeValue seqAt(NodeValue nvSeq, NodeValue nvIndex) {
        List<Node> elts = elements("seqAt", nvSeq);
        if ( ! nvIndex.isInteger() )
            throw new ExprEvalException("seqAt: Index is not an integer: "+nvIndex);
        int idx;
        try {
            idx = nvIndex.getInteger().intValueExact();
        } catch (ArithmeticException ex) {
            throw new ExprEvalException("seqAt: Index out of range: "+nvIndex);
        }
        if ( idx < 1 || idx > elts.size() )
            throw new ExprEvalException("seqAt: Index out of range (1.."+elts.size()+"): "+nvIndex);
        Node elt = elts.get(idx-1);
        if ( elt.isNodeUndef() )
            throw new ExprEvalException("seqAt: Empty slot: "+nvIndex);
        return NodeValue.makeNode(elt);
    }

    /**
     * {@code seq[from...to]}: the members from position {@code from} to position
     * {@code to}, both included and 1-based, clamped to the sequence. A null
     * {@code from} is 1 ({@code seq[...to]}); a null {@code to} is the end
     * ({@code seq[from...]}). Throws {@link ExprEvalException} if the first argument
     * is not a sequence, or a bound is not an integer, or {@code from < 1}, or
     * {@code to < 0}.
     */
    public static NodeValue seqSlice(NodeValue nvSeq, NodeValue nvFrom, NodeValue nvTo) {
        List<Node> elts = elements("seqSlice", nvSeq);
        int from = ( nvFrom == null ) ? 1 : bound("seqSlice", nvFrom, 1);
        int to = ( nvTo == null ) ? elts.size() : bound("seqSlice", nvTo, 0);
        int start = Math.min(from - 1, elts.size());
        int end = Math.min(to, elts.size());
        List<Node> slice = ( start >= end ) ? List.of() : elts.subList(start, end);
        return NodeValue.makeNode(NodeFactory.createSeqNode(slice));
    }

    private static int bound(String name, NodeValue nv, int least) {
        if ( ! nv.isInteger() )
            throw new ExprEvalException(name+": Bound is not an integer: "+nv);
        int x;
        try {
            x = nv.getInteger().intValueExact();
        } catch (ArithmeticException ex) {
            throw new ExprEvalException(name+": Bound out of range: "+nv);
        }
        if ( x < least )
            throw new ExprEvalException(name+": Bound must be at least "+least+": "+nv);
        return x;
    }

    /**
     * {@code needle IN seq}: is some member of the sequence equal ({@code =}) to
     * the needle? A member that is not comparable with the needle is not equal to
     * it. Throws {@link ExprEvalException} if the second argument is not a sequence.
     */
    public static boolean inSeq(NodeValue needle, NodeValue nvSeq) {
        List<Node> elts = elements("inSeq", nvSeq);
        for ( Node elt : elts ) {
            // An empty slot holds nothing.
            if ( elt.isNodeUndef() )
                continue;
            try {
                if ( NodeValue.sameValueAs(needle, NodeValue.makeNode(elt)) )
                    return true;
            } catch (ExprEvalException ex) { /* not comparable: not equal */ }
        }
        return false;
    }

    /** Build a sequence from its elements. */
    public static NodeValue mkSeq(List<NodeValue> args) {
        List<Node> elts = new ArrayList<>(args.size());
        for ( NodeValue nv : args )
            elts.add(nv.asNode());
        return NodeValue.makeNode(NodeFactory.createSeqNode(elts));
    }

    private static List<Node> elements(String name, NodeValue nv) {
        Node n = nv.asNode();
        if ( ! n.isNodeSeq() )
            throw new ExprEvalException(name+": Not a sequence: "+nv);
        return n.getSeq();
    }
}
