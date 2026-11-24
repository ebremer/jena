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

package org.apache.jena.sparql.core;

import java.util.Optional;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Node_Ext;
import org.apache.jena.shared.PrefixMapping;

/**
 * SPARQL pattern term for a gap in a sequence pattern: {@code ...} or {@code ...?rest}.
 * <p>
 * A gap stands for any run of zero or more members of the sequence being matched,
 * so {@code ( ?first ... )}, {@code ( ... ?last )} and {@code ( ... ?x ... )} find
 * the first member, the last member and every member. A named gap
 * {@code ...?rest} also binds {@code ?rest} to the run it matched, as a sequence;
 * the same variable twice must match equal runs.
 * <p>
 * This is a pattern-only term: it only occurs as a member of a sequence pattern in a
 * WHERE clause, it is never concrete and it never appears in data.
 */
public class Node_SeqGap extends Node_Ext<Optional<Var>> {

    private static final Node_SeqGap ANON = new Node_SeqGap(Optional.empty());

    /** The anonymous gap {@code ...}. */
    public static Node_SeqGap create() {
        return ANON;
    }

    /** A gap: anonymous when {@code var} is null, else the named gap {@code ...?var}. */
    public static Node_SeqGap create(Var var) {
        return var == null ? ANON : new Node_SeqGap(Optional.of(var));
    }

    private Node_SeqGap(Optional<Var> var) {
        super(var);
    }

    /** The variable of a named gap, or null for {@code ...}. */
    public Var getVar() {
        return get().orElse(null);
    }

    public boolean isNamed() {
        return get().isPresent();
    }

    @Override
    public String toString(PrefixMapping pm) {
        return toString();
    }

    @Override
    public String toString() {
        return isNamed() ? "..." + getVar().toString() : "...";
    }
}
