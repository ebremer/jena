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

package org.apache.jena.tdb1.store;

import org.apache.jena.tdb1.base.block.FileMode;
import org.apache.jena.tdb1.sys.LibTestOps;
import org.apache.jena.tdb1.sys.SystemTDB;
import org.junit.AfterClass ;
import org.junit.BeforeClass ;
import org.junit.runner.RunWith ;
import org.junit.runners.Suite ;

@RunWith(Suite.class)
@Suite.SuiteClasses( {
    TestNodeId.class
    , TestTripleTable.class
    , TestGraphTDB1.class
    , TestGraphNamedTDB1.class
    , TestDatasetTDB1Persist.class
    , TestDatasetTDB1.class
    , TestDatasetGraphTDB1Find.class
    , TestDatasetGraphTDB1FindPattern.class
    , TestLoader.class
    , Test_SPARQL_TDB1.class
    , TestConcurrentAccess.class
    , TestQueryExecTDB1.class
    , TestDynamicDatasetTDB1.class
    , TestStoreConnectionsDirect.class
    , TestStoreConnectionsMapped.class
    , TestLocationLockStoreConnection.class
    , TestGraphTDB1_Prefixes.class
    , TestQuadFilter.class
} )
public class TS_Store
{
    static FileMode mode ;

    @BeforeClass
    public static void beforeClass()
    {
        mode = SystemTDB.fileMode() ;
    }

    @AfterClass
    public static void afterClass()
    {
        if ( ! SystemTDB.fileMode().equals(mode) )
            LibTestOps.setFileMode(mode) ;
    }
}
