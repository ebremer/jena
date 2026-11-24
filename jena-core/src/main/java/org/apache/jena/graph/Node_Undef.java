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

import org.apache.jena.shared.PrefixMapping;

/**
 * The empty slot of a sequence: {@code ( 1 undef 3 )} has three positions and
 * nothing at the second.
 * <p>
 * It is not an RDF term. It stands only as an element of a {@link Node_Seq}, never as
 * the subject, predicate or object of a triple, and a variable is never bound to it.
 * It is not an unknown value either: {@code ( 1 _:b 3 )} says that. A slot has no
 * value and later data cannot fill it.
 * There is one instance, {@link Node#UNDEF}.
 */
public class Node_Undef extends Node {

    /*package*/ static final Node_Undef nodeUndef = new Node_Undef();

    private Node_Undef() {}

    @Override
    public boolean isConcrete() { return true; }

    @Override
    public boolean isNodeUndef() { return true; }

    @Override
    public int hashCode() {
        return Node.hashNodeUndef;
    }

    @Override
    public boolean equals(Object other) {
        // There is only one such object.
        return this == other;
    }

    @Override
    public Object visitWith(NodeVisitor v) {
        return v.visitUndef(this);
    }

    @Override
    public String toString(PrefixMapping pmap) { return toString(); }

    @Override
    public String toString() {
        return "undef";
    }
}
