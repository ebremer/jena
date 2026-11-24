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

package org.apache.jena.sparql.algebra.op;

import java.util.Objects;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpVisitor;
import org.apache.jena.sparql.algebra.Transform;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.sse.Tags;
import org.apache.jena.sparql.util.NodeIsomorphismMap;

/**
 * {@code (unnest (?x ?i) expr sub)}: for each solution of {@code sub}, evaluate
 * {@code expr}; when it is a sequence, one solution per member, extended with
 * {@code ?x} bound to the member and, if present, {@code ?i} bound to its 1-based
 * position. Otherwise no solution. The syntax is {@code UNNEST(expr AS ?x AT ?i)}.
 */
public class OpUnnest extends Op1 {

    public static OpUnnest create(Op subOp, Expr expr, Var var, Var posVar) {
        return new OpUnnest(subOp, expr, var, posVar);
    }

    private final Expr expr;
    private final Var var;
    private final Var posVar;    // may be null

    private OpUnnest(Op subOp, Expr expr, Var var, Var posVar) {
        super(subOp);
        this.expr = Objects.requireNonNull(expr, "expr");
        this.var = Objects.requireNonNull(var, "var");
        this.posVar = posVar;
    }

    public Expr getExpr() { return expr; }

    /** The variable bound to each member. */
    public Var getVar() { return var; }

    /** The variable bound to the member's 1-based position, or null. */
    public Var getPosVar() { return posVar; }

    public boolean hasPosVar() { return posVar != null; }

    @Override
    public String getName() {
        return Tags.tagUnnest;
    }

    @Override
    public void visit(OpVisitor opVisitor) {
        opVisitor.visit(this);
    }

    @Override
    public Op1 copy(Op subOp) {
        return new OpUnnest(subOp, expr, var, posVar);
    }

    public OpUnnest copy(Op subOp, Expr expr2) {
        return new OpUnnest(subOp, expr2, var, posVar);
    }

    public OpUnnest copy(Op subOp, Expr expr2, Var var2, Var posVar2) {
        return new OpUnnest(subOp, expr2, var2, posVar2);
    }

    @Override
    public Op apply(Transform transform, Op subOp) {
        return transform.transform(this, subOp);
    }

    @Override
    public int hashCode() {
        return getName().hashCode() ^ Objects.hash(expr, var, posVar) ^ getSubOp().hashCode();
    }

    @Override
    public boolean equalTo(Op other, NodeIsomorphismMap labelMap) {
        if ( !(other instanceof OpUnnest o) )
            return false;
        if ( !expr.equals(o.expr) || !var.equals(o.var) || !Objects.equals(posVar, o.posVar) )
            return false;
        return getSubOp().equalTo(o.getSubOp(), labelMap);
    }
}
