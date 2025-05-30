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

package org.apache.jena.tdb1.store ;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream ;
import java.util.List ;

import org.apache.jena.atlas.io.IO ;
import org.apache.jena.atlas.iterator.Iter ;
import org.apache.jena.atlas.logging.LogCtl ;
import org.apache.jena.graph.Node ;
import org.apache.jena.graph.NodeFactory ;
import org.apache.jena.graph.Triple ;
import org.apache.jena.query.ARQ ;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.core.Quad ;
import org.apache.jena.tdb1.ConfigTest;
import org.apache.jena.tdb1.TDB1;
import org.apache.jena.tdb1.TDB1Loader;
import org.apache.jena.tdb1.base.file.Location;
import org.apache.jena.tdb1.setup.DatasetBuilderStd;
import org.apache.jena.tdb1.sys.TDBInternal;
import org.junit.AfterClass ;
import org.junit.BeforeClass ;
import org.junit.Test ;

@SuppressWarnings("removal")
public class TestLoader {
    private static String DIR = null ;
    private static final Node   g   = NodeFactory.createURI("g") ;
    private static final Node   s   = NodeFactory.createURI("s") ;
    private static final Node   p   = NodeFactory.createURI("p") ;
    private static final Node   o   = NodeFactory.createURI("o") ;

    @BeforeClass
    static public void beforeClass() {
        DIR = ConfigTest.getTestingDataRoot()+"/Loader/" ;
        LogCtl.disable(ARQ.logExecName) ;
        LogCtl.disable(TDB1.logLoaderName) ;
    }

    @AfterClass
    static public void afterClass() {
        LogCtl.enable(ARQ.logExecName) ;
        LogCtl.enable(TDB1.logLoaderName) ;
        TDBInternal.reset();
    }

    static DatasetGraphTDB fresh() {
        return DatasetBuilderStd.create(Location.mem()) ;
    }

    @Test
    public void load_dataset_01() {
        DatasetGraphTDB dsg = fresh() ;
        TDB1Loader.load(dsg, DIR + "data-1.nq", false) ;
        assertTrue(dsg.getDefaultGraph().isEmpty()) ;
        assertEquals(1, dsg.getGraph(g).size()) ;
    }

    @Test
    public void load_dataset_02() {
        DatasetGraphTDB dsg = fresh() ;
        InputStream in = IO.openFile(DIR + "data-1.nq") ;
        TDB1Loader.load(dsg, in, Lang.NQUADS, false, false) ;
        assertTrue(dsg.getDefaultGraph().isEmpty()) ;
        assertEquals(1, dsg.getGraph(g).size()) ;
    }

    @Test
    public void load_dataset_03() {
        DatasetGraphTDB dsg = fresh() ;
        TDB1Loader.load(dsg, DIR + "data-3.trig", false) ;
        String uri = dsg.getDefaultGraph().getPrefixMapping().getNsPrefixURI("") ;
        assertEquals("http://example/", uri) ;
    }

    @Test
    public void load_graph_01() {
        DatasetGraphTDB dsg = fresh() ;
        TDB1Loader.load(dsg, DIR + "data-2.nt", false) ;
        assertEquals(1, dsg.getDefaultGraph().size()) ;
    }

    @Test
    public void load_graph_02() {
        DatasetGraphTDB dsg = fresh() ;
        TDB1Loader.load(dsg.getDefaultGraphTDB(), DIR + "data-2.nt", false) ;
        assertEquals(1, dsg.getDefaultGraph().size()) ;
    }

    @Test
    public void load_graph_03() {
        DatasetGraphTDB dsg = fresh() ;
        TDB1Loader.load(dsg.getGraphTDB(g), DIR + "data-2.nt", false) ;
        assertEquals(0, dsg.getDefaultGraph().size()) ;
        assertEquals(1, dsg.getGraph(g).size()) ;

        // Check indexes.
        List<Triple> x = Iter.toList(dsg.getDefaultGraph().find(null, null, null)) ;
        assertEquals(0, x.size()) ;

        x = Iter.toList(dsg.getGraph(g).find(null, null, null)) ;
        assertEquals(1, x.size()) ;
        x = Iter.toList(dsg.getGraph(g).find(s, null, null)) ;
        assertEquals(1, x.size()) ;
        x = Iter.toList(dsg.getGraph(g).find(null, p, null)) ;
        assertEquals(1, x.size()) ;
        x = Iter.toList(dsg.getGraph(g).find(null, null, o)) ;
        assertEquals(1, x.size()) ;

        List<Quad> z = Iter.toList(dsg.find(null, null, null, null)) ;
        assertEquals(1, z.size()) ;
        z = Iter.toList(dsg.find(g, null, null, null)) ;
        assertEquals(1, z.size()) ;
        z = Iter.toList(dsg.find(null, s, null, null)) ;
        assertEquals(1, z.size()) ;
        z = Iter.toList(dsg.find(null, null, p, null)) ;
        assertEquals(1, z.size()) ;
        z = Iter.toList(dsg.find(null, null, null, o)) ;
        assertEquals(1, z.size()) ;
    }

    @Test
    public void load_graph_04() {
        DatasetGraphTDB dsg = fresh() ;
        TDB1Loader.load(dsg, DIR + "data-4.ttl", false) ;
        String uri = dsg.getDefaultGraph().getPrefixMapping().getNsPrefixURI("") ;
        assertEquals("http://example/", uri) ;
    }

    @Test
    public void load_graph_05() {
        DatasetGraphTDB dsg = fresh() ;
        GraphTDB graph = dsg.getDefaultGraphTDB() ;
        TDB1Loader.load(graph, DIR + "data-4.ttl", false) ;
        String uri = dsg.getDefaultGraph().getPrefixMapping().getNsPrefixURI("") ;
        assertEquals("http://example/", uri) ;
    }

    @Test
    public void load_graph_06() {
        DatasetGraphTDB dsg = fresh() ;
        GraphTDB graph = dsg.getGraphTDB(g) ;
        TDB1Loader.load(graph, DIR + "data-4.ttl", false) ;
        String uri1 = dsg.getGraph(g).getPrefixMapping().getNsPrefixURI("") ;
        assertEquals("http://example/", uri1) ;
        String uri2 = dsg.getDefaultGraph().getPrefixMapping().getNsPrefixURI("") ;
        // Shared prefixes.
        assertEquals("http://example/", uri2) ;
    }
}
