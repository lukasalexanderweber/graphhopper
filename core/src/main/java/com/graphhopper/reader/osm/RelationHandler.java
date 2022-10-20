package com.graphhopper.reader.osm;

import static com.graphhopper.reader.osm.OSMNodeData.isTowerNode;
import static com.graphhopper.util.Helper.nf;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.osm.OSMTurnRestriction.RestrictionType;
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
        LOGGER.info("pass2 - finished reading OSM relations, processed node restrictions: {}, invalid node restrictions: {}, "
                        + "processed way restrictions: {}, invalid way restrictions: {}",
                        nf(restrictionData.node_restrictions), nf(restrictionData.invalid_node_restrictions), 
                        nf(restrictionData.way_restrictions), nf(restrictionData.invalid_way_restrictions));
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
                        restrictionData.invalid_node_restrictions++;
                    }
                if (turnRestriction.getViaType() == OSMTurnRestriction.ViaType.WAY) {
                    if (!restrictionData.artificialNodeRestrictions.containsKey(turnRestriction.getId())) {
                        restrictionData.invalid_way_restrictions++;
                        LOGGER.info("|" + turnRestriction.getId() + "|failed|no artificial Node Restrictions");
                        return;
                    }
                    
                    ArrayList<NodeRestriction> artificialNodeRestrictions = restrictionData.artificialNodeRestrictions.get(turnRestriction.getId());
                    
                    for (int i = 0; i < artificialNodeRestrictions.size(); i++){
                        NodeRestriction nodeRestriction = artificialNodeRestrictions.get(i);
                        OSMTurnRestriction artificialOSMTurnRestriction;
                        
                        // for via ways, a ONLY restriction is implemented by multiple NOT
                        // restrictions, only the last restriction is an ONLY restriction;
                        // NO via way restrictions are all NOT
                        if (i == artificialNodeRestrictions.size() - 1 && turnRestriction.getRestriction() == RestrictionType.ONLY) {
                            artificialOSMTurnRestriction = new OSMTurnRestriction(turnRestriction, nodeRestriction, RestrictionType.ONLY);
                        } else {
                            artificialOSMTurnRestriction = new OSMTurnRestriction(turnRestriction, nodeRestriction, RestrictionType.NOT);
                        }
                        
                        try {
                            osmParsers.handleTurnRestrictionTags(artificialOSMTurnRestriction, map, baseGraph);
                        } catch (TurnRestrictionException e) {
                            restrictionData.invalid_way_restrictions++;
                            LOGGER.info("|" + turnRestriction.getId() + "|failed|" + e.getMessage());
                            return;
                        }
                    }
                    LOGGER.info("|" + turnRestriction.getId() + "|success|");
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
