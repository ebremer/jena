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
 * {@code expr[m...]} - the members from position m (1-based) to the end of a
 * sequence, clamped: {@code ()[2...]} is {@code ()}.
 */
public class E_SeqSliceFrom extends ExprFunction2 {
    private static final String functionName = Tags.tagSeqSliceFrom;

    public E_SeqSliceFrom(Expr seq, Expr from) {
        super(seq, from, functionName);
    }

    @Override
    public NodeValue eval(NodeValue seq, NodeValue from) {
        return SeqFunctions.seqSlice(seq, from, null);
    }

    @Override
    public Expr copy(Expr e1, Expr e2) { return new E_SeqSliceFrom(e1, e2); }
}
