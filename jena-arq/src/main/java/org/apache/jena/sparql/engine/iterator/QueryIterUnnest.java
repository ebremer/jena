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

package org.apache.jena.sparql.engine.iterator;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.atlas.lib.Lib;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingBuilder;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.serializer.SerializationContext;
import org.apache.jena.sparql.util.NodeFactoryExtra;

/**
 * {@code UNNEST(expr AS ?x AT ?i)}: for each input binding, evaluate the expression;
 * when it is a sequence, yield one binding per member with {@code ?x} bound to the
 * member and {@code ?i}, if given, to its 1-based position. An unbound or erroring
 * expression, or a value that is not a sequence, yields nothing. A variable already
 * bound in the input keeps only the members (positions) equal to its value. At an
 * empty slot {@code ?x} is left as it is, with {@code ?i} bound to the position.
 */
public class QueryIterUnnest extends QueryIterRepeatApply {
    private final Expr expr;
    private final Var var;
    private final Var posVar;

    public QueryIterUnnest(QueryIterator input, Expr expr, Var var, Var posVar, ExecutionContext execCxt) {
        super(input, execCxt);
        this.expr = expr;
        this.var = var;
        this.posVar = posVar;
    }

    @Override
    protected QueryIterator nextStage(Binding binding) {
        Node seq;
        try {
            NodeValue nv = expr.eval(binding, getExecContext());
            seq = nv.asNode();
        } catch (ExprEvalException ex) {
            return QueryIterNullIterator.create(getExecContext());
        }
        if ( seq == null || !seq.isNodeSeq() )
            return QueryIterNullIterator.create(getExecContext());
        List<Node> members = seq.getSeq();
        List<Binding> results = new ArrayList<>(members.size());
        for ( int i = 0 ; i < members.size() ; i++ ) {
            Binding b = extend(binding, members.get(i), i + 1);
            if ( b != null )
                results.add(b);
        }
        return QueryIterPlainWrapper.create(results.iterator(), getExecContext());
    }

    private Binding extend(Binding binding, Node member, int position) {
        BindingBuilder b = Binding.builder(binding);
        if ( !member.isNodeUndef() && !set(b, var, member) )
            return null;
        if ( posVar != null && !set(b, posVar, NodeFactoryExtra.intToNode(position)) )
            return null;
        return b.build();
    }

    /** Add var=value; if the variable is already bound, the values must be the same. */
    private static boolean set(BindingBuilder b, Var v, Node value) {
        if ( b.contains(v) )
            return b.get(v).sameValueAs(value);
        b.add(v, value);
        return true;
    }

    @Override
    protected void details(IndentedWriter out, SerializationContext cxt) {
        out.print(Lib.className(this));
        out.print(" ");
        out.print(expr.toString());
        out.print(" AS ");
        out.print(var.toString());
        if ( posVar != null ) {
            out.print(" AT ");
            out.print(posVar.toString());
        }
    }
}
