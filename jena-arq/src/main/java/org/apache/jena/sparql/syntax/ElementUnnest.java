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

package org.apache.jena.sparql.syntax;

import java.util.Objects;

import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.util.NodeIsomorphismMap;

/**
 * {@code UNNEST(expr AS ?x AT ?i)}: one solution per member of the sequence that
 * {@code expr} evaluates to, binding {@code ?x} to the member and, when given,
 * {@code ?i} to its 1-based position. No solution when {@code expr} is unbound,
 * errors or is not a sequence.
 */
public class ElementUnnest extends Element {
    private final Expr expr;
    private final Var var;
    private final Var posVar;        // may be null

    public ElementUnnest(Expr expr, Var var, Var posVar) {
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
    public boolean equalTo(Element el2, NodeIsomorphismMap isoMap) {
        if ( !(el2 instanceof ElementUnnest other) )
            return false;
        return expr.equals(other.expr) && var.equals(other.var) && Objects.equals(posVar, other.posVar);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expr, var, posVar);
    }

    @Override
    public void visit(ElementVisitor v) {
        v.visit(this);
    }
}
