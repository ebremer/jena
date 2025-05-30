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

package org.apache.jena.fuseki.access;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;

import org.apache.jena.graph.Node;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.exec.QueryExec;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.sparql.util.NodeUtils;
import org.apache.jena.tdb1.TDB1Factory;
import org.apache.jena.tdb2.DatabaseMgr;

/** A {@link SecurityContextView} is the things actor (user, role) is allowed to do.
 * Currently version: the set of graphs, by graph name, they can access.
 * It can be inverted into a "deny" policy with {@link Predicate#negate()}.
 */
public class SecurityContextView implements SecurityContext {

    public static SecurityContextView NONE = new SecurityContextView();
    public static SecurityContextView DFT_GRAPH = new SecurityContextView(true);

    private final Collection<Node> graphNames;
    private final boolean matchDefaultGraph;

    private SecurityContextView() {
        this(false);
    }

    private SecurityContextView(boolean matchDefaultGraph) {
        this.matchDefaultGraph = matchDefaultGraph;
        this.graphNames = Collections.emptyList();
    }

    public SecurityContextView(String...graphNames) {
        this(NodeUtils.convertToSetNodes(graphNames));
    }

    public SecurityContextView(Node...graphNames) {
        this(Arrays.asList(graphNames));
    }

    public SecurityContextView(Collection<Node> visibleGraphs) {
        Collection<Node> x = new ArrayList<>();
        x.addAll(visibleGraphs);
        this.matchDefaultGraph = visibleGraphs.stream().anyMatch(Quad::isDefaultGraph);
        if ( matchDefaultGraph ) {
            x.remove(Quad.defaultGraphIRI);
            x.remove(Quad.defaultGraphNodeGenerated);
        }
        this.graphNames = Collections.unmodifiableCollection(x);
    }

    @Override
    public Collection<Node> visibleGraphs() {
        return graphNames;
    }

    @Override
    public boolean visableDefaultGraph() {
        return matchDefaultGraph;
    }

    @Override
    public QueryExec createQueryExec(Query query, DatasetGraph dsg) {
        if ( isAccessControlledTDB(dsg) ) {
            QueryExec qExec = QueryExec.dataset(dsg).query(query).build();
            filterTDB(dsg, qExec);
            return qExec;
        }
        // Not TDB - do by selecting graphs.
        DatasetGraph dsgA = DataAccessCtl.filteredDataset(dsg, this);
        return QueryExec.dataset(dsgA).query(query).build();
    }

    /**
     * Apply a filter suitable for the TDB-backed {@link DatasetGraph}, to the {@link Context} of the
     * {@link QueryExec}. This does not modify the {@link DatasetGraph}.
     */
    @Override
    public void filterTDB(DatasetGraph dsg, QueryExec qExec) {
        GraphFilter<?> predicate = predicate(dsg);
        qExec.getContext().set(predicate.getContextKey(), predicate);
    }

    /**
     * Quad filter to reflect the security policy of this {@link SecurityContext}. It is
     * better to call {@link #createQueryExecution(Query, DatasetGraph)} which may be more
     * efficient.
     */
    @Override
    public Predicate<Quad> predicateQuad() {
        return quad -> {
            if ( quad.isDefaultGraph() )
                return matchDefaultGraph;
            if ( quad.isUnionGraph() )
                // Union graph is automatically there but its visible contents are different.
                return true;
            return graphNames.contains(quad.getGraph());
        };
    }

    /**
     * Create a GraphFilter for a TDB backed dataset.
     *
     * @return GraphFilter
     * @throws IllegalArgumentException
     *             if not a TDB database, or a {@link DatasetGraphAccessControl} wrapped
     *             TDB database.
     */
    @SuppressWarnings("removal")
    protected GraphFilter<?> predicate(DatasetGraph dsg) {
        dsg = DatasetGraphAccessControl.removeWrapper(dsg);
        // dsg has to be the database dataset, not wrapped.
        //  DatasetGraphSwitchable is wrapped but should not be unwrapped.
        if ( TDB1Factory.isTDB1(dsg) )
            return GraphFilterTDB1.graphFilter(dsg, graphNames, matchDefaultGraph);
        if ( DatabaseMgr.isTDB2(dsg) )
            return GraphFilterTDB2.graphFilter(dsg, graphNames, matchDefaultGraph);
        throw new IllegalArgumentException("Not a TDB1 or TDB2 database: "+dsg.getClass().getSimpleName());
    }

    @SuppressWarnings("removal")
    protected static boolean isAccessControlledTDB(DatasetGraph dsg) {
        DatasetGraph dsgBase = DatasetGraphAccessControl.unwrapOrNull(dsg);
        if ( dsgBase == null )
            return false;
        if ( TDB1Factory.isTDB1(dsgBase) )
            return true;
        if ( DatabaseMgr.isTDB2(dsgBase) )
            return true;
        return false;
    }

    @Override
    public String toString() {
        return "dft:"+matchDefaultGraph+" / "+graphNames.toString();
    }
}
