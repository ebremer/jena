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

package org.apache.jena.fuseki.mgt;

import static java.lang.String.format;
import static org.apache.jena.atlas.lib.Lib.lowercase;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.atlas.RuntimeIOException;
import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.json.JsonBuilder;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.atlas.lib.InternalErrorException;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.atlas.web.ContentType;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.fuseki.build.DatasetDescriptionMap;
import org.apache.jena.fuseki.build.FusekiConfig;
import org.apache.jena.fuseki.ctl.ActionContainerItem;
import org.apache.jena.fuseki.ctl.JsonDescription;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.FusekiVocab;
import org.apache.jena.fuseki.server.ServerConst;
import org.apache.jena.fuseki.servlets.ActionLib;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.fuseki.system.DataUploader;
import org.apache.jena.fuseki.system.FusekiNetLib;
import org.apache.jena.fuseki.webapp.FusekiWebapp;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFLib;
import org.apache.jena.sparql.core.assembler.AssemblerUtils;
import org.apache.jena.sparql.util.FmtUtils;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.web.HttpSC;

public class ActionDatasets extends ActionContainerItem {

    static private Property pServiceName = FusekiVocab.pServiceName;
    //static private Property pStatus = FusekiVocab.pStatus;

    private static final String paramDatasetName    = "dbName";
    private static final String paramDatasetType    = "dbType";
    private static final String tDatabaseTDB        = "tdb";
    private static final String tDatabaseTDB1       = "tdb1";
    private static final String tDatabaseTDB2       = "tdb2";
    private static final String tDatabaseMem        = "mem";

    // Sync lock.
    private static final Object lock = new Object();

    public ActionDatasets() { super(); }

    @Override
    public void validate(HttpAction action) {}

    // ---- GET : return details of dataset or datasets.
    @Override
    protected JsonValue execGetContainer(HttpAction action) {
        action.log.info(format("[%d] GET datasets", action.id));
        JsonBuilder builder = new JsonBuilder();
        builder.startObject("D");
        builder.key(ServerConst.datasets);
        JsonDescription.arrayDatasets(builder, action.getDataAccessPointRegistry());
        builder.finishObject("D");
        return builder.build();
    }

    @Override
    protected JsonValue execGetItem(HttpAction action) {
        String item = getItemDatasetName(action);
        action.log.info(format("[%d] GET dataset %s", action.id, item));
        JsonBuilder builder = new JsonBuilder();
        DataAccessPoint dsDesc = getItemDataAccessPoint(action, item);
        if ( dsDesc == null )
            ServletOps.errorNotFound("Not found: dataset "+item);
        JsonDescription.describe(builder, dsDesc);
        return builder.build();
    }

    // ---- POST

    @Override
    protected JsonValue execPostContainer(HttpAction action) {
        UUID uuid = UUID.randomUUID();
        DatasetDescriptionMap registry = new DatasetDescriptionMap();

        ContentType ct = ActionLib.getContentType(action);

        boolean hasParams = action.getRequestParameterNames().hasMoreElements();

        if ( ct == null && ! hasParams )
            ServletOps.errorBadRequest("Bad request - Content-Type or both parameters dbName and dbType required");

        boolean committed = false;
        // Also acts as a concurrency lock
        synchronized(lock) {
            String systemFileCopy = null;
            String configFile = null;

            try {
                // Where to build the templated service/database.
                Model modelData = ModelFactory.createDefaultModel();
                StreamRDF dest = StreamRDFLib.graph(modelData.getGraph());

                if ( hasParams || WebContent.isHtmlForm(ct) )
                    assemblerFromForm(action, dest);
                else if ( WebContent.isMultiPartForm(ct) )
                    assemblerFromUpload(action, dest);
                else
                    assemblerFromBody(action, dest);

                // ----
                // Keep a persistent copy immediately.  This is not used for
                // anything other than being "for the record".
                systemFileCopy = FusekiWebapp.dirSystemFileArea.resolve(uuid.toString()).toString();
                try ( OutputStream outCopy = IO.openOutputFile(systemFileCopy) ) {
                    RDFDataMgr.write(outCopy, modelData, Lang.TURTLE);
                }

                Model model = ModelFactory.createDefaultModel();
                model.add(modelData);
                // Add dataset and model declarations.
                model = AssemblerUtils.prepareForAssembler(model);

                // ----
                // Process configuration.

                // Returns the "service fu:name NAME" statement
                Statement stmt = findService(model);

                Resource subject = stmt.getSubject();
                Literal object = stmt.getObject().asLiteral();

                if ( object.getDatatype() != null && ! object.getDatatype().equals(XSDDatatype.XSDstring) )
                    action.log.warn(format("[%d] Service name '%s' is not a string", action.id, FmtUtils.stringForRDFNode(object)));

                String datasetPath;
                {   // Check the name provided.
                    String datasetName = object.getLexicalForm();
                    // This duplicates the code FusekiBuilder.buildDataAccessPoint to give better error messages and HTTP status code."

                    // ---- Check and canonicalize name.
                    if ( datasetName.isEmpty() )
                        ServletOps.error(HttpSC.BAD_REQUEST_400, "Empty dataset name");
                    if ( StringUtils.isBlank(datasetName) )
                        ServletOps.error(HttpSC.BAD_REQUEST_400, format("Whitespace dataset name: '%s'", datasetName));
                    if ( datasetName.contains(" ") )
                        ServletOps.error(HttpSC.BAD_REQUEST_400, format("Bad dataset name (contains spaces) '%s'",datasetName));
                    if ( datasetName.equals("/") )
                        ServletOps.error(HttpSC.BAD_REQUEST_400, format("Bad dataset name '%s'",datasetName));
                    datasetPath = DataAccessPoint.canonical(datasetName);
                    // ---- Check whether it already exists
                    if ( action.getDataAccessPointRegistry().isRegistered(datasetPath) )
                        // And abort.
                        ServletOps.error(HttpSC.CONFLICT_409, "Name already registered "+datasetPath);
                }

                action.log.info(format("[%d] Create database : name = %s", action.id, datasetPath));

                configFile = FusekiWebapp.generateConfigurationFilename(datasetPath);
                List<String> existing = FusekiWebapp.existingConfigurationFile(datasetPath);
                if ( ! existing.isEmpty() )
                    ServletOps.error(HttpSC.CONFLICT_409, "Configuration file for '"+datasetPath+"' already exists");

                // Write to configuration directory.
                try ( OutputStream outCopy = IO.openOutputFile(configFile) ) {
                    RDFDataMgr.write(outCopy, modelData, Lang.TURTLE);
                }

                DataAccessPoint dataAccessPoint = FusekiConfig.buildDataAccessPoint(subject.getModel().getGraph(), subject.asNode(), registry);
                if ( dataAccessPoint == null ) {
                    FmtLog.error(action.log, "Failed to build DataAccessPoint: datasetPath = %s; DataAccessPoint name = %s", datasetPath, dataAccessPoint);
                    ServletOps.errorBadRequest("Failed to build DataAccessPoint");
                    return null;
                }
                dataAccessPoint.getDataService().setEndpointProcessors(action.getOperationRegistry());
                dataAccessPoint.getDataService().goActive();
                if ( ! datasetPath.equals(dataAccessPoint.getName()) )
                    FmtLog.warn(action.log, "Inconsistent names: datasetPath = %s; DataAccessPoint name = %s", datasetPath, dataAccessPoint);

                action.getDataAccessPointRegistry().register(dataAccessPoint);
                action.setResponseContentType(WebContent.contentTypeTextPlain);
                ServletOps.success(action);

                committed = true;

            } catch (IOException ex) { IO.exception(ex); }
            finally {
                if ( ! committed ) {
                    if ( systemFileCopy != null ) FileOps.deleteSilent(systemFileCopy);
                    if ( configFile != null ) FileOps.deleteSilent(configFile);

                }

            }
        }
        return null;
    }

    /** Find the service resource. There must be only one in the configuration. */
    private Statement findService(Model model) {
        // Try to find by unique pServiceName (max backwards compatibility)
        // then try to find by rdf:type fuseki:Service.

        // JENA-1794
        Statement stmt = getOne(model, null, pServiceName, null);
        // null means 0 or many, not one.

        if ( stmt == null ) {
            // This calculates { ?x rdf:type fu:Service ; ?x fu:name ?name }
            // One and only one service.
            Statement stmt2 = getOne(model, null, RDF.type, FusekiVocab.fusekiService);
            if ( stmt2 == null ) {
                int count = model.listStatements(null, RDF.type, FusekiVocab.fusekiService).toList().size();
                if ( count == 0 )
                    ServletOps.errorBadRequest("No triple rdf:type fuseki:Service found");
                else
                    ServletOps.errorBadRequest("Multiple Fuseki service descriptions");
            }
            Statement stmt3 = getOne(model, stmt2.getSubject(), pServiceName, null);
            if ( stmt3 == null ) {
                StmtIterator sIter = model.listStatements(stmt2.getSubject(), pServiceName, (RDFNode)null );
                if ( ! sIter.hasNext() )
                    ServletOps.errorBadRequest("No name given in description of Fuseki service");
                sIter.next();
                if ( sIter.hasNext() )
                    ServletOps.errorBadRequest("Multiple names given in description of Fuseki service");
                throw new InternalErrorException("Inconsistent: getOne didn't fail the second time");
            }
            stmt = stmt3;
        }

        if ( ! stmt.getObject().isLiteral() )
            ServletOps.errorBadRequest("Found "+FmtUtils.stringForRDFNode(stmt.getObject())+" : Service names are strings, then used to build the external URI");

        return stmt;
    }

    @Override
    protected JsonValue execPostItem(HttpAction action) {
        // This used to be the state change function -- active/inactive.
        // Leave the core of the operation - tests used it to ping for a database.
        String name = getItemDatasetName(action);
        if ( name == null )
            name = "''";
        action.log.info(format("[%d] POST dataset %s", action.id, name));

        // Not in the action - this not an ActionService.
        DataAccessPoint dap = getItemDataAccessPoint(action, name);

        if ( dap == null )
            ServletOps.errorNotFound("Not found: dataset "+name);
        return null;
    }

    // ---- DELETE

    @Override
    protected void execDeleteItem(HttpAction action) {
        // Does not exist?
        String name = getItemDatasetName(action);
        if ( name == null )
            name = "";
        action.log.info(format("[%d] DELETE dataset=%s", action.id, name));

        if ( ! action.getDataAccessPointRegistry().isRegistered(name) )
            ServletOps.errorNotFound("No such dataset registered: "+name);

        boolean committed = false;
        synchronized(lock) {
            // Redo check inside transaction.
            DataAccessPoint ref = action.getDataAccessPointRegistry().get(name);
            if ( ref == null )
                ServletOps.errorNotFound("No such dataset registered: "+name);

            // Get a reference before removing.
            DataService dataService = ref.getDataService();
            // ---- Make it invisible in this running server.
            action.getDataAccessPointRegistry().remove(name);

            // Find the configuration.
            String filename = name.startsWith("/") ? name.substring(1) : name;
            List<String> configurationFiles = FusekiWebapp.existingConfigurationFile(filename);

            if ( configurationFiles.isEmpty() ) {
                // ---- Unmanaged
                action.log.warn(format("[%d] Can't delete database configuration - not a managed database; dataset=%s", action.id, name));
//                ServletOps.errorOccurred(format("Can't delete database - not a managed configuration", name));
                committed = true;
                ServletOps.success(action);
                return;
            }

            if  ( configurationFiles.size() > 1 ) {
                // -- This should not happen.
                action.log.warn(format("[%d] There are %d configuration files, not one.", action.id, configurationFiles.size()));
                ServletOps.errorOccurred(format("There are %d configuration files, not one. Delete not performed; manual clean up of the filesystem needed.",
                                                configurationFiles.size()));
                return;
            }

            // ---- Remove managed database.
            String cfgPathname = configurationFiles.get(0);

            // Delete configuration file.
            // Once deleted, server restart will not have the database.
            FileOps.deleteSilent(cfgPathname);

            // Delete the database for real only when it is in the server "run/databases"
            // area. Don't delete databases that reside elsewhere. We do delete the
            // configuration file, so the databases will not be associated with the server
            // anymore.

            @SuppressWarnings("removal")
            boolean isTDB1 = org.apache.jena.tdb1.sys.TDBInternal.isTDB1(dataService.getDataset());
            boolean isTDB2 = org.apache.jena.tdb2.sys.TDBInternal.isTDB2(dataService.getDataset());

            // This occasionally fails in tests due to outstanding transactions.
            // Unclear what's holding the transaction (maybe another test clearing up slowly).
            try {
                dataService.shutdown();
            } catch (/*DBOE*/ Exception ex) { }
            // JENA-1481: Really delete files.
            if ( ( isTDB1 || isTDB2 ) ) {
                // Delete databases created by the UI, or the admin operation, which are
                // in predictable, unshared location on disk.
                // There may not be any database files, the in-memory case.
                Path pDatabase = FusekiWebapp.dirDatabases.resolve(filename);
                if ( Files.exists(pDatabase)) {
                    try {
                        if ( Files.isSymbolicLink(pDatabase)) {
                            action.log.info(format("[%d] Database is a symbolic link, not removing files %s", action.id, pDatabase));
                        } else {
                            IO.deleteAll(pDatabase);
                            action.log.info(format("[%d] Deleted database files %s", action.id, pDatabase));
                        }
                    } catch (RuntimeIOException ex) {
                        action.log.error(format("[%d] Error while deleting database files %s: %s", action.id, pDatabase, ex.getMessage()), ex);
                        // But we have managed to remove it from the running server, and removed its configuration, so declare victory.
                    }
                }
            }
            ServletOps.success(action);
        }
    }

    private static void assemblerFromBody(HttpAction action, StreamRDF dest) {
        bodyAsGraph(action, dest);
    }

    private static Map<String, String> dbTypeToTemplate = Map.of(
            // Default TDB
            tDatabaseTDB,  Template.templateTDB2_FN,
            // Specific TDB
            tDatabaseTDB1,  Template.templateTDB1_FN,
            tDatabaseTDB2, Template.templateTDB2_FN,
            // Transactional, in-memory
            tDatabaseMem,  Template.templateTIM_MemFN);

    private static void assemblerFromForm(HttpAction action, StreamRDF dest) {
        String x = action.getRequestQueryString();
        String dbType = action.getRequestParameter(paramDatasetType);
        String dbName = action.getRequestParameter(paramDatasetName);
        if ( StringUtils.isBlank(dbType) || StringUtils.isBlank(dbName) )
            ServletOps.errorBadRequest("Received HTML form.  Both parameters 'dbName' and 'dbType' required");

        Map<String, String> params = new HashMap<>();

        if ( dbName.startsWith("/") )
            params.put(Template.NAME, dbName.substring(1));
        else
            params.put(Template.NAME, dbName);
        FusekiWebapp.addGlobals(params);

        //action.log.info(format("[%d] Create database : name = %s, type = %s", action.id, dbName, dbType ));

        String template = dbTypeToTemplate.get(lowercase(dbType));
        if ( template == null )
            ServletOps.errorBadRequest(format("dbType can be only '%s' ('%s','%s') or '%s'", tDatabaseTDB, tDatabaseTDB1, tDatabaseTDB2, tDatabaseMem));

        String syntax = TemplateFunctions.templateFile(template, params, Lang.TTL);
        RDFParser.create().source(new StringReader(syntax)).base("http://base/").lang(Lang.TTL).parse(dest);
    }

    private static void assemblerFromUpload(HttpAction action, StreamRDF dest) {
        DataUploader.incomingData(action, dest);
    }

    private static Statement getOne(Model m, Resource s, Property p, RDFNode o) {
        StmtIterator iter = m.listStatements(s, p, o);
        if ( ! iter.hasNext() )
            return null;
        Statement stmt = iter.next();
        if ( iter.hasNext() )
            return null;
        return stmt;
    }

    private static void bodyAsGraph(HttpAction action, StreamRDF dest) {
        HttpServletRequest request = action.getRequest();
        String base = ActionLib.wholeRequestURL(request);
        ContentType ct = FusekiNetLib.getContentType(request);
        Lang lang = RDFLanguages.contentTypeToLang(ct.getContentTypeStr());
        if ( lang == null ) {
            ServletOps.errorBadRequest("Unknown content type for triples: " + ct);
            return;
        }
        dest.prefix("root", base+"#");
        ActionLib.parseOrError(action, dest, lang, base);
    }
}
