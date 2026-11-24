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
 * rdf-array: the SPARQL-array tests of rdf-tests-contrib
 * (https://github.com/rdf-array/rdf-tests-contrib), copied into testing/rdf-array by its
 * update.sh: sequence and gap patterns, UNNEST, the sequence functions, update, syntax and the
 * result formats.
 */
public class Scripts_RdfArrayContribSPARQL {
    @TestFactory
    @DisplayName("rdf-array rdf-tests-contrib - SPARQL")
    public Stream<DynamicNode> testFactory() {
        return Scripts.all(TestMakers.testMakerSPARQL,
                           "testing/rdf-array/rdf-tests-contrib/sparql/eval/manifest.ttl",
                           "testing/rdf-array/rdf-tests-contrib/sparql/update/manifest.ttl",
                           "testing/rdf-array/rdf-tests-contrib/sparql/syntax-query/manifest.ttl",
                           "testing/rdf-array/rdf-tests-contrib/sparql/results/manifest.ttl");
    }
}
