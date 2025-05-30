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

package org.apache.jena.tdb1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.apache.jena.atlas.lib.FileOps ;
import org.apache.jena.sparql.core.DatasetGraph ;
import org.apache.jena.sparql.core.Quad ;
import org.apache.jena.sparql.sse.SSE ;
import org.apache.jena.tdb1.base.file.Location;
import org.apache.jena.tdb1.sys.TDBInternal;
import org.apache.jena.tdb1.sys.TDBMaker;
import org.apache.jena.tdb1.transaction.DatasetGraphTransaction;
import org.junit.After ;
import org.junit.Before ;
import org.junit.Test ;

@SuppressWarnings("removal")
public class TestTDB1Factory
{
    String DIR = ConfigTest.getCleanDir() ;

    static Quad quad1 = SSE.parseQuad("(_ <s> <p> 1)") ;
    static Quad quad2 = SSE.parseQuad("(_ <s> <p> 1)") ;

    @Before
    public void before() {
        TDBInternal.reset();
        FileOps.clearDirectory(DIR);
    }

    @After
    public void after() {
        TDBInternal.reset();
        FileOps.clearDirectory(DIR);
    }

    @Test
    public void testTDBFactory1() {
        DatasetGraph dg1 = TDB1Factory.createDatasetGraph(Location.mem("FOO"));
        DatasetGraph dg2 = TDB1Factory.createDatasetGraph(Location.mem("FOO"));
        dg1.add(quad1);
        assertTrue(dg2.contains(quad1));
    }

    @Test
    public void testTDBFactory2() {
        // The unnamed location is unique each time.
        DatasetGraph dg1 = TDB1Factory.createDatasetGraph(Location.mem());
        DatasetGraph dg2 = TDB1Factory.createDatasetGraph(Location.mem());
        dg1.add(quad1);
        assertFalse(dg2.contains(quad1));
    }

    @Test
    public void testTDBMakerTxn1() {
        DatasetGraph dg1 = TDBMaker.createDatasetGraphTransaction(Location.create(DIR));
        DatasetGraph dg2 = TDBMaker.createDatasetGraphTransaction(Location.create(DIR));

        DatasetGraph dgBase1 = ((DatasetGraphTransaction)dg1).getBaseDatasetGraph();
        DatasetGraph dgBase2 = ((DatasetGraphTransaction)dg2).getBaseDatasetGraph();

        assertSame(dgBase1, dgBase2);
    }

    @Test
    public void testTDBMakerTxn2() {
        // Named memory locations
        DatasetGraph dg1 = TDBMaker.createDatasetGraphTransaction(Location.mem("FOO"));
        DatasetGraph dg2 = TDBMaker.createDatasetGraphTransaction(Location.mem("FOO"));

        DatasetGraph dgBase1 = ((DatasetGraphTransaction)dg1).getBaseDatasetGraph();
        DatasetGraph dgBase2 = ((DatasetGraphTransaction)dg2).getBaseDatasetGraph();

        assertSame(dgBase1, dgBase2);
    }

    @Test
    public void testTDBMakerTxn3() {
        // Un-named memory locations
        DatasetGraph dg1 = TDBMaker.createDatasetGraphTransaction(Location.mem());
        DatasetGraph dg2 = TDBMaker.createDatasetGraphTransaction(Location.mem());

        DatasetGraph dgBase1 = ((DatasetGraphTransaction)dg1).getBaseDatasetGraph();
        DatasetGraph dgBase2 = ((DatasetGraphTransaction)dg2).getBaseDatasetGraph();

        assertNotSame(dgBase1, dgBase2);
    }

    @Test public void testTDBFresh01() {
        boolean b = TDB1Factory.inUseLocation(DIR) ;
        assertFalse("Expect false before any creation attempted", b) ;
    }

    @Test public void testTDBFresh02() {
        boolean b = TDB1Factory.inUseLocation(DIR) ;
        assertFalse("Expect false before any creation attempted", b) ;
        TDB1Factory.createDataset(DIR) ;
        b = TDB1Factory.inUseLocation(DIR) ;
        assertTrue("Expected true after creation attempted", b) ;
        TDBInternal.expel(Location.create(DIR), true);
    }

    @Test public void testTDBFresh03() {
        boolean b = TDB1Factory.inUseLocation(DIR) ;
        assertFalse("Expect true before any creation attempted", b) ;
        TDB1Factory.createDataset(DIR) ;
        b = TDB1Factory.inUseLocation(DIR) ;
        assertTrue("Expected true after creation attempted", b) ;
        TDBInternal.expel(Location.create(DIR), true);
        b = TDB1Factory.inUseLocation(DIR) ;
        assertTrue("Expected true even after StoreConenction reset", b) ;
    }

    @Test public void testTDBFresh11() {
        Location loc = Location.mem() ;
        boolean b = TDB1Factory.inUseLocation(loc) ;
        assertFalse("Expect false before any creation attempted", b) ;
    }

    @Test public void testTDBFresh22() {
        Location loc = Location.mem() ;
        boolean b = TDB1Factory.inUseLocation(loc) ;
        TDB1Factory.createDataset(loc) ;
        b = TDB1Factory.inUseLocation(loc) ;
        assertFalse("Expected false for a unique memory location", b) ;
    }

    @Test public void testTDBFresh23() {
        Location loc = Location.mem("FOO") ;
        boolean b = TDB1Factory.inUseLocation(loc) ;
        TDB1Factory.createDataset(loc) ;
        b = TDB1Factory.inUseLocation(loc) ;
        assertTrue("Expected true for a named memory location", b) ;
    }
}
