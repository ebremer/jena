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

package org.apache.jena.sparql.expr;

import org.apache.jena.sparql.function.library.seq.SeqFunctions;
import org.apache.jena.sparql.sse.Tags;

/**
 * {@code expr[m...n]} - the members m to n of a sequence, both included, counting
 * from 1 and clamped to the sequence. Evaluation error if the first argument is not
 * a sequence or the bounds are not integers (m &gt;= 1, n &gt;= 0).
 */
public class E_SeqSlice extends ExprFunction3 {
    private static final String functionName = Tags.tagSeqSlice;

    public E_SeqSlice(Expr seq, Expr from, Expr to) {
        super(seq, from, to, functionName);
    }

    @Override
    public NodeValue eval(NodeValue seq, NodeValue from, NodeValue to) {
        return SeqFunctions.seqSlice(seq, from, to);
    }

    @Override
    public Expr copy(Expr e1, Expr e2, Expr e3) { return new E_SeqSlice(e1, e2, e3); }
}
