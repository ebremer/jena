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

package org.apache.jena.sparql.expr;

import org.apache.jena.sparql.function.library.seq.SeqFunctions;
import org.apache.jena.sparql.sse.Tags;

/** {@code ?x NOT IN ?seq} - the negation of {@link E_InSeq}. */
public class E_NotInSeq extends ExprFunction2 {
    private static final String functionName = Tags.tagNotInSeq;

    public E_NotInSeq(Expr needle, Expr seq) {
        super(needle, seq, functionName);
    }

    @Override
    public NodeValue eval(NodeValue needle, NodeValue seq) {
        return NodeValue.booleanReturn(!SeqFunctions.inSeq(needle, seq));
    }

    @Override
    public Expr copy(Expr e1, Expr e2) { return new E_NotInSeq(e1, e2); }
}
