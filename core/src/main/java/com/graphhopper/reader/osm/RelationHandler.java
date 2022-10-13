package com.graphhopper.reader.osm;

import static com.graphhopper.reader.osm.OSMNodeData.isTowerNode;

import java.util.ArrayList;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.parsers.TurnCostParser;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.errors.TurnRestrictionException;

public class RelationHandler extends RelationHandlerBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelationHandler.class);
	private final BaseGraph baseGraph;
	private final OSMParsers osmParsers;
	private final TurnCostStorage turnCostStorage;
	private OSMNodeData nodeData;
    private OSMTurnRestrictionData restrictionData;
    private WayHandler wayHandler;

    public RelationHandler (BaseGraph baseGraph, OSMParsers osmParsers, TurnCostStorage turnCostStorage, OSMNodeData nodeData, OSMTurnRestrictionData restrictionData, WayHandler wayHandler) {
        this.baseGraph = baseGraph;
    	this.osmParsers = osmParsers;
    	this.turnCostStorage = turnCostStorage;
    	this.nodeData = nodeData;
    	this.restrictionData = restrictionData;
    	this.wayHandler = wayHandler;
    }

    @Override
    public void onStart() {
        LOGGER.info("pass2 - start reading OSM relations");
        LOGGER.info("building restrictions...");
        OSMTurnRestrictionBuilder builder = new OSMTurnRestrictionBuilder(nodeData, restrictionData, wayHandler);
        builder.buildRestrictions();
        LOGGER.info("finished building restrictions!");
    }
    
    @Override
    public void handleElement(ReaderElement elem) {
        counter++;
        logEvery(LOGGER, 10_000_000);
        handleRelation((ReaderRelation) elem);
    }
    
    @Override
    public void onFinish() {
        LOGGER.info("pass2 - finished reading OSM relations");
    }
    
    /**
     * This method is called for each relation during the second pass of {@link WaySegmentParser}
     * We use it to set turn restrictions.
     */
    protected void handleRelation(ReaderRelation relation) {    	
        if (turnCostStorage != null && relation.hasTag("type", "restriction")) {
            TurnCostParser.ExternalInternalMap map = new TurnCostParser.ExternalInternalMap() {
                @Override
                public int getInternalNodeIdOfOsmNode(long nodeOsmId) {
                    return getInternalNodeIdOfOSMNode(nodeOsmId);
                }

                @Override
                public long getOsmIdOfInternalEdge(int edgeId) {
                    return restrictionData.edgeIdToOsmWayIdMap.get(edgeId);
                }
            };
            for (OSMTurnRestriction turnRestriction : createTurnRestrictions(relation)) {
                if (turnRestriction.getViaType() == OSMTurnRestriction.ViaType.NODE)
                    try {
                        osmParsers.handleTurnRestrictionTags(turnRestriction, map, baseGraph);
                    } catch (TurnRestrictionException e) {
                        LOGGER.info("failed: " + turnRestriction.getId() + " - " + e);
                        e.printStackTrace();
                    }
                if (turnRestriction.getViaType() == OSMTurnRestriction.ViaType.WAY) {
                    if (!restrictionData.artificialNodeRestrictions.containsKey(turnRestriction.getId()))
                        return;
                    for (NodeRestriction nodeRestriction : restrictionData.artificialNodeRestrictions.get(turnRestriction.getId())){
                        turnRestriction.fromOsmWayId = nodeRestriction.getFrom();
                        turnRestriction.viaOSMIds = new ArrayList<>(Arrays.asList(nodeRestriction.getVia()));
                        turnRestriction.toOsmWayId = nodeRestriction.getTo();
                        try {
                            osmParsers.handleTurnRestrictionTags(turnRestriction, map, baseGraph);
                        } catch (TurnRestrictionException e) {
                            LOGGER.info("failed: " + turnRestriction.getId() + " - " + e);
                        }
                    }
                }
            }

        }
    }
    
    public int getInternalNodeIdOfOSMNode(long nodeOsmId) {
        int id = nodeData.getId(nodeOsmId);
        if (isTowerNode(id))
            return -id - 3;
        return -1;
    }
}
