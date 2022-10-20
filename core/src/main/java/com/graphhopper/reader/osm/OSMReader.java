/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader.osm;

import static com.graphhopper.util.Helper.nf;

import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.OSMReaderConfig;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.util.CustomArea;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.countryrules.CountryRuleFactory;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Parses an OSM file (xml, zipped xml or pbf) and creates a graph from it. The OSM file is actually read twice.
 * During the first scan we determine the 'type' of each node, i.e. we check whether a node only appears in a single way
 * or represents an intersection of multiple ways, or connects two ways. We also scan the relations and store them for
 * each way ID in memory.
 * During the second scan we store the coordinates of the nodes that belong to ways in memory and then split each way
 * into several segments that are divided by intersections or barrier nodes. Each segment is added as an edge of the
 * resulting graph. Afterwards we scan the relations again to determine turn restrictions.
 **/
public class OSMReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(OSMReader.class);
    private final int workerThreads = 2;    
    
    // from constructor
    private final BaseGraph baseGraph;
    private final EncodingManager encodingManager;
    private final OSMParsers osmParsers;
    private final OSMReaderConfig config;

    // from setters
    private File osmFile;
	private AreaIndex<CustomArea> areaIndex;
    private ElevationProvider eleProvider = ElevationProvider.NOOP;
	private CountryRuleFactory countryRuleFactory;

	// during initialization
	private NodeAccess nodeAccess;
    private final TurnCostStorage turnCostStorage;
    private final RamerDouglasPeucker simplifyAlgo = new RamerDouglasPeucker();
    private OSMNodeData nodeData;
    private OSMTurnRestrictionData restrictionData;
    private RelationFlagsData relationFlagsData;
        
    // during read
    private Date osmDataDate;
    
    public OSMReader(BaseGraph baseGraph, EncodingManager encodingManager, OSMParsers osmParsers, OSMReaderConfig config) {
    	this.baseGraph = baseGraph;
        this.encodingManager = encodingManager;
        this.config = config;
        this.nodeAccess = baseGraph.getNodeAccess();
        this.osmParsers = osmParsers;
        
        this.restrictionData = new OSMTurnRestrictionData();
        this.nodeData = new OSMNodeData(nodeAccess, baseGraph.getDirectory());

        simplifyAlgo.setMaxDistance(config.getMaxWayPointDistance());
        simplifyAlgo.setElevationMaxDistance(config.getElevationMaxWayPointDistance());
        turnCostStorage = baseGraph.getTurnCostStorage();

        IntsRef tempRelFlags = osmParsers.createRelationFlags();
        if (tempRelFlags.length != 2)
            // we use a long to store relation flags currently, so the relation flags ints ref must have length 2
            throw new IllegalArgumentException("OSMReader cannot use relation flags with != 2 integers");
        relationFlagsData = new RelationFlagsData(tempRelFlags);        
    }


    /**
     * Sets the OSM file to be read. Supported formats include .osm.xml, .osm.gz and .xml.pbf
     */
    public OSMReader setFile(File osmFile) {
        this.osmFile = osmFile;
        return this;
    }

    /**
     * The area index is queried for each OSM way and the associated areas are added to the way's tags
     */
    public OSMReader setAreaIndex(AreaIndex<CustomArea> areaIndex) {
        this.areaIndex = areaIndex;
        return this;
    }

    public OSMReader setElevationProvider(ElevationProvider eleProvider) {
        if (eleProvider == null)
            throw new IllegalStateException("Use the NOOP elevation provider instead of null or don't call setElevationProvider");

        if (!nodeAccess.is3D() && ElevationProvider.NOOP != eleProvider)
            throw new IllegalStateException("Make sure you graph accepts 3D data");

        this.eleProvider = eleProvider;
        return this;
    }

    public OSMReader setCountryRuleFactory(CountryRuleFactory countryRuleFactory) {
        this.countryRuleFactory = countryRuleFactory;
        return this;
    }
    
    public WayPreprocessor getWayPreprocessor() {
    	return new WayPreprocessor(osmParsers, nodeData);
    }
    
    public RelationPreprocessor getRelationPreprocessor() {
    	return new RelationPreprocessor(osmParsers, restrictionData, relationFlagsData);
    }
    
    public NodeHandler getNodeHandler() {
    	return new NodeHandler(nodeData, eleProvider);
    }
    
    public WayHandler getWayHandler() {
    	return new WayHandler(baseGraph, osmParsers, nodeData, restrictionData, 
    			relationFlagsData, config, nodeAccess, simplifyAlgo, eleProvider, 
    			countryRuleFactory, areaIndex, encodingManager);
    }        

    public RelationHandler getRelationHandler() {
    	return new RelationHandler(baseGraph, osmParsers, turnCostStorage, nodeData, restrictionData, getWayHandler());
    }
    
    public void readGraph() throws IOException {
        if (osmParsers == null)
            throw new IllegalStateException("Tag parsers were not set.");

        if (osmFile == null)
            throw new IllegalStateException("No OSM file specified");

        if (!osmFile.exists())
            throw new IllegalStateException("Your specified OSM file does not exist:" + osmFile.getAbsolutePath());

        if (!baseGraph.isInitialized())
            throw new IllegalStateException("BaseGraph must be initialize before we can read OSM");

        if (nodeData.getNodeCount() > 0)
            throw new IllegalStateException("You can only read the graph once");

        LOGGER.info("Start reading OSM file: '" + osmFile + "'");
        LOGGER.info("preprocessing ways and relations - start");
        StopWatch sw1 = StopWatch.started();
        OSMParser parse1 = new OSMParser()
                        .setFileheaderHandler(new FileHeaderHandler())
                        .setWayHandler(getWayPreprocessor())
                        .setRelationHandler(getRelationPreprocessor());
        parse1.readOSM(osmFile, workerThreads);
        LOGGER.info("preprocessing ways and relations - finished, took: {}", sw1.stop().getTimeString());

        long nodes = nodeData.getNodeCount();

        LOGGER.info("Creating graph. Node count (pillar+tower): " + nodes + ", " + Helper.getMemInfo());

        LOGGER.info("graph creation - start");
        StopWatch sw2 = new StopWatch().start();
        OSMParser parse2 = new OSMParser()
                        .setNodeHandler(getNodeHandler())
                        .setWayHandler(getWayHandler())
                        .setRelationHandler(getRelationHandler());
        parse2.readOSM(osmFile, workerThreads);
        LOGGER.info("graph creation - finished, took: {}", sw2.stop().getTimeString());

        nodeData.release();

        LOGGER.info("Finished reading OSM file." +
                " pass1: " + (int) sw1.getSeconds() + "s, " +
                " pass2: " + (int) sw2.getSeconds() + "s, " +
                " total: " + (int) (sw1.getSeconds() + sw2.getSeconds()) + "s");

        osmDataDate = parse1.getTimeStamp();
        if (baseGraph.getNodes() == 0)
            throw new RuntimeException("Graph after reading OSM must not be empty");
        LOGGER.info("Finished reading OSM file: {}, nodes: {}, edges: {}",
                osmFile.getAbsolutePath(), nf(baseGraph.getNodes()), nf(baseGraph.getEdges()));
        finishedReading();
    }
    
    /**
     * @return the timestamp given in the OSM file header or null if not found
     */
    public Date getDataDate() {
        return osmDataDate;
    }   

    private void finishedReading() {
        eleProvider.release();
        relationFlagsData = null;
        restrictionData = null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
