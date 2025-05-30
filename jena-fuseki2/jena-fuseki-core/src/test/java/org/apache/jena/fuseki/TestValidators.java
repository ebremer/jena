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

package org.apache.jena.fuseki;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import org.apache.jena.fuseki.server.Validators;

public class TestValidators {
    @Test public void validator_service_1() {
        Validators.serviceName("");
    }

    @Test public void validator_service_2() {
        Validators.serviceName("abc");
    }

    @Test public void validator_service_3() {
        Validators.serviceName("/abc");
    }

    @Test public void validator_service_4() {
        Validators.serviceName("-");
    }

    @Test public void validator_service_20() {
        Validators.serviceName("abc-def");
    }

    @Test public void validator_service_21() {
        Validators.serviceName("$/op");
    }

    @Test public void validator_service_22() {
        Validators.serviceName("/abc.def_ghi");
    }

    @Test
    public void validator_service_bad_1() {
        assertThrows(FusekiConfigException.class, ()->Validators.serviceName(null));
    }

    @Test
    public void validator_service_bad_2() {
        assertThrows(FusekiConfigException.class, ()->Validators.serviceName(" "));
    }

    @Test
    public void validator_service_bad_3() {
        assertThrows(FusekiConfigException.class, ()->Validators.serviceName("\\"));
    }

    @Test
    public void validator_service_bad_4() {
        assertThrows(FusekiConfigException.class, ()->Validators.serviceName("<"));
    }

    @Test
    public void validator_service_bad_5() {
        assertThrows(FusekiConfigException.class, ()->Validators.serviceName(">"));
    }

    @Test
    public void validator_service_bad_6() {
        assertThrows(FusekiConfigException.class, ()->Validators.serviceName("?"));
    }

    @Test
    public void validator_service_bad_7() {
        assertThrows(FusekiConfigException.class, ()->Validators.serviceName("#"));
    }

    @Test
    public void validator_service_bad_8() {
        assertThrows(FusekiConfigException.class, ()->Validators.serviceName("\""));
    }

    @Test
    public void validator_service_bad_20() {
        assertThrows(FusekiConfigException.class, ()->Validators.serviceName("<http://example/>"));
    }

    @Test  public void validator_endpoint_null() {
        Validators.endpointName(null);
    }

    @Test public void validator_endpoint_1() {
        Validators.endpointName("");
    }

    @Test public void validator_endpoint_2() {
        Validators.endpointName("abc");
    }

    @Test public void validator_endpoint_3() {
        Validators.endpointName("/abc");
    }

    @Test public void validator_endpoint_4() {
        Validators.endpointName("-");
    }

    @Test public void validator_endpoint_20() {
        Validators.endpointName("abc-def");
    }

    @Test public void validator_endpoint_21() {
        Validators.endpointName("$/op");
    }

    @Test public void validator_endpoint_22() {
        Validators.endpointName("/abc.def_ghi");
    }

//    @Test
//    public void validator_endpoint_bad_1() {
//        Validators.endpointName(null);
//    }

    @Test
    public void validator_endpoint_bad_2() {
        assertThrows(FusekiConfigException.class, ()->Validators.endpointName(" "));
    }

    @Test
    public void validator_endpoint_bad_3() {
        assertThrows(FusekiConfigException.class, ()->Validators.endpointName("\\"));
    }

    @Test
    public void validator_endpoint_bad_4() {
        assertThrows(FusekiConfigException.class, ()->Validators.endpointName("<"));
    }

    @Test
    public void validator_endpoint_bad_5() {
        assertThrows(FusekiConfigException.class, ()->Validators.endpointName(">"));
    }

    @Test
    public void validator_endpoint_bad_6() {
        assertThrows(FusekiConfigException.class, ()->Validators.endpointName("?"));
    }

    @Test
    public void validator_endpoint_bad_7() {
        assertThrows(FusekiConfigException.class, ()->Validators.endpointName("#"));
    }

    @Test
    public void validator_endpoint_bad_8() {
        assertThrows(FusekiConfigException.class, ()->Validators.endpointName("\""));
    }

    @Test
    public void validator_endpoint_bad_20() {
        assertThrows(FusekiConfigException.class, ()->Validators.endpointName("<http://example/>)"));
    }

    @Test public void validator_graph_1() {
        Validators.graphName("http://example/abc");
    }

    @Test public void validator_graph_2() {
        Validators.graphName("http://example/abc#def");
    }

    @Test
    public void validator_graph_bad_1() {
        assertThrows(FusekiConfigException.class, ()->Validators.graphName("abc"));
    }

    @Test
    public void validator_graph_bad_2() {
        assertThrows(FusekiConfigException.class, ()->Validators.graphName("#abc"));
    }
}
