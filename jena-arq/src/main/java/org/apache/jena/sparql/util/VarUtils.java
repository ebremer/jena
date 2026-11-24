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

package org.apache.jena.sparql.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.* ;
import org.apache.jena.sparql.pfunction.PropFuncArg;

public class VarUtils {
    public static Set<Var> getVars(Triple triple) {
        Set<Var> x = new HashSet<>();
        addVarsFromTriple(x, triple);
        return x;
    }

    public static void addVarsFromTriple(Collection<Var> acc, Triple triple) {
        addVar(acc, triple.getSubject());
        addVar(acc, triple.getPredicate());
        addVar(acc, triple.getObject());
    }

    /**
     * Add the variables a match of the triple always binds. That leaves out those
     * inside a sequence: a member may be an empty slot, which binds nothing.
     */
    public static void addFixedVarsFromTriple(Collection<Var> acc, Triple triple) {
        addFixedVar(acc, triple.getSubject());
        addFixedVar(acc, triple.getPredicate());
        addFixedVar(acc, triple.getObject());
    }

    private static void addFixedVar(Collection<Var> acc, Node n) {
        if ( n == null || n.isNodeSeq() )
            return;
        if ( n.isTripleTerm() )
            addFixedVarsFromTriple(acc, n.getTriple());
        else
            addVar(acc, n);
    }

    public static void addVarsFromQuad(Collection<Var> acc, Quad quad) {
        addVar(acc, quad.getGraph());
        addVar(acc, quad.getSubject());
        addVar(acc, quad.getPredicate());
        addVar(acc, quad.getObject());
    }

    public static void addVarsFromTriplePath(Collection<Var> acc, TriplePath tpath) {
        addVar(acc, tpath.getSubject());
        if ( tpath.isTriple() )
            addVar(acc, tpath.getPredicate());
        addVar(acc, tpath.getObject());
    }

    public static void addVar(Collection<Var> acc, Node n) {
        if ( n == null )
            return;
        if ( n.isVariable() )
            acc.add(Var.alloc(n));
        else if ( n.isTripleTerm() ) {
            Triple t = n.getTriple();
            addVarsFromTriple(acc, t);
        } else if ( n.isNodeSeq() ) {
            for ( Node elt : n.getSeq() )
                addVar(acc, elt);
        } else if ( n instanceof Node_SeqGap gap ) {
            if ( gap.isNamed() )
                acc.add(gap.getVar());
        }
    }

    // Name to avoid erasure clash
    public static void addVarNodes(Collection<Var> acc, Collection<Node> nodes) {
        if ( nodes == null )
            return ;
        for ( Node n : nodes )
            addVar(acc, n) ;
    }

    public static void addVarsTriples(Collection<Var> acc, Collection<Triple> triples) {
        for ( Triple triple : triples )
            addVarsFromTriple(acc, triple);
    }

    public static void addVars(Collection<Var> acc, BasicPattern pattern) {
        addVarsTriples(acc, pattern.getList());
    }

    public static void addVars(Collection<Var> acc, Node graphNode, BasicPattern triples) {
        addVar(acc, graphNode) ;
        addVars(acc, triples) ;
    }

    public static void addVars(Collection<Var> acc, QuadPattern quadPattern) {
        for ( Quad quad : quadPattern.getList() ) {
            addVarsFromQuad(acc, quad) ;
        }
    }

    public static void addVars(Collection<Var> acc, PropFuncArg arg) {
        if ( arg.isNode() )
            addVar(acc, arg.getArg());
        else
            addVarNodes(acc, arg.getArgList());
    }
}
