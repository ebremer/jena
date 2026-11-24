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

package org.apache.jena.external;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import org.apache.jena.arq.junit.Scripts;
import org.apache.jena.arq.junit.manifest.TestMakers;

/**
 * rdf-array: the Turtle-, TriG-, N-Triples- and N-Quads-array tests of rdf-tests-contrib
 * (https://github.com/rdf-array/rdf-tests-contrib), copied into testing/rdf-array by its
 * update.sh. The SPARQL-array tests are {@link Scripts_RdfArrayContribSPARQL}.
 */
public class Scripts_RdfArrayContrib {
    @TestFactory
    @DisplayName("rdf-array rdf-tests-contrib - RDF syntaxes")
    public Stream<DynamicNode> testFactory() {
        return Scripts.all(TestMakers.testMakerRIOT,
                           "testing/rdf-array/rdf-tests-contrib/rdf/rdf-turtle/manifest.ttl",
                           "testing/rdf-array/rdf-tests-contrib/rdf/rdf-trig/manifest.ttl",
                           "testing/rdf-array/rdf-tests-contrib/rdf/rdf-n-triples/manifest.ttl",
                           "testing/rdf-array/rdf-tests-contrib/rdf/rdf-n-quads/manifest.ttl");
    }
}
