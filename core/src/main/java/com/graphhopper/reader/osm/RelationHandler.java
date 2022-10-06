package com.graphhopper.reader.osm;

import static com.graphhopper.reader.osm.OSMNodeData.isTowerNode;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.parsers.TurnCostParser;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.TurnCostStorage;

public class RelationHandler extends RelationHandlerBase {
	private final BaseGraph baseGraph;
	private final OSMParsers osmParsers;
	private final TurnCostStorage turnCostStorage;
	private OSMNodeData nodeData;
    private OSMTurnRestrictionData restrictionData;

    public RelationHandler (BaseGraph baseGraph, OSMParsers osmParsers, TurnCostStorage turnCostStorage, OSMNodeData nodeData, OSMTurnRestrictionData restrictionData) {
    	this.baseGraph = baseGraph;
    	this.osmParsers = osmParsers;
    	this.turnCostStorage = turnCostStorage;
    	this.nodeData = nodeData;
    	this.restrictionData = restrictionData;
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
            	    osmParsers.handleTurnRestrictionTags(turnRestriction, map, baseGraph);
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
