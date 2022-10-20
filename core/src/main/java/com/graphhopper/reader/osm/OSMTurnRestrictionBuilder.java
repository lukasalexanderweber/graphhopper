package com.graphhopper.reader.osm;

import static java.util.Collections.emptyMap;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.PointList;

public class OSMTurnRestrictionBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(OSMTurnRestrictionBuilder.class);
    private long nextArtificialOSMWayId = -Long.MAX_VALUE;
    
    private OSMNodeData nodeData;
    private OSMTurnRestrictionData restrictionData;
    private WayHandler wayHandler;
    
    public OSMTurnRestrictionBuilder(OSMNodeData nodeData, OSMTurnRestrictionData restrictionData, WayHandler wayHandler) {
    	this.nodeData = nodeData;
    	this.restrictionData = restrictionData;
    	this.wayHandler = wayHandler;
    }
    
    public void buildRestrictions() {
        for (WayRestriction restriction : restrictionData.wayRestrictions) {
            if (!allWaysHaveTwoTowerNodes(restriction, nodeData)) {
                LOGGER.info("|" + restriction.getId() + "|failed|too much Tower Nodes");
                restrictionData.invalid_way_restrictions++;
                continue;
            }
            restriction.buildRestriction(restrictionData.osmWayMap);
            if (!restriction.isValid()) {
                LOGGER.info("|" + restriction.getId() + "|failed|invalid Restriction");
                restrictionData.invalid_way_restrictions++;
                continue;
            }
            try {
                int nRestrictions = restriction.getRestrictions().size();
                ArrayList<NodeRestriction> restrictions = new ArrayList<>();
                Long previousNewWay = null;
                
                for (int i = 0; i < nRestrictions - 1; i++) {
                    NodeRestriction r = restriction.getRestrictions().get(i);
                    NodeRestriction r2 = restriction.getRestrictions().get(i+1);

                    long from = r.getVia();
                    long to = r2.getVia();
                    long newWay = addArtificialWay(from, to, restrictionData.osmWayMap.get(r.getTo()));
                    
                    if (i == 0)
                        restrictions.add(new NodeRestriction(r.getFrom(), r.getVia(), r.getTo()));
                    if (i > 0 && i < nRestrictions-1) {
                        // prevent exiting the detour between two via ways (only if more than 1 via way)
                        restrictions.add(new NodeRestriction(r.getFrom(), r.getVia(), newWay));
                        restrictions.add(new NodeRestriction(previousNewWay, r.getVia(), r.getTo()));
                    }
                    if (i == nRestrictions-2)
                        restrictions.add(new NodeRestriction(newWay, r2.getVia(), r2.getTo()));
                    
                    previousNewWay = newWay;
                }
                
                restrictionData.artificialNodeRestrictions.put(restriction.getId(), restrictions);

            } catch (Exception e) {
                LOGGER.info("|" + restriction.getId() + "|failed|" + e);
                restrictionData.invalid_way_restrictions++;
                continue;
            }
            LOGGER.info("|" + restriction.getId() + "|success|");
        }
    }

    private boolean allWaysHaveTwoTowerNodes(WayRestriction restriction, OSMNodeData nodeData) {
    	boolean allWaysHaveTwoTowerNodes = true;
    	for (Long w : restriction.getWays()) {
    		ReaderWay way = restrictionData.osmWayMap.get(w);
    		if (way == null) 
    			break;
    		if (getTowerNodeCount(way, nodeData) != 2)
    			allWaysHaveTwoTowerNodes = false;
    			break;
    	}
    	return allWaysHaveTwoTowerNodes;
	}
    
    private int getTowerNodeCount(ReaderWay way, OSMNodeData nodeData) {
    	int towerCount = 0;
		for (LongCursor node : way.getNodes()) {
			int id = nodeData.getId(node.value);
			if (OSMNodeData.isTowerNode(id))
				towerCount++;
		}
		return towerCount;
    }
        
    private long addArtificialWay(long osmFrom, long osmTo, ReaderWay originalWay) {
        int from = nodeData.idToTowerNode(nodeData.getId(osmFrom));
        int to = nodeData.idToTowerNode(nodeData.getId(osmTo));
        long newOsmWayId = nextArtificialOSMWayId++;
        restrictionData.osmWayIdSet.add(newOsmWayId);      
        ReaderWay artificial_way = new ReaderWay(newOsmWayId, originalWay.getTags(), originalWay.getNodes());
        LongArrayList nodes = originalWay.getNodes();
        PointList pointList = new PointList(nodes.size(), nodeData.is3D());
        for (LongCursor point : nodes) {
            nodeData.addCoordinatesToPointList(nodeData.getId(point.value), pointList);
        }

        wayHandler.addEdge(from, to, pointList, artificial_way, emptyMap());
        return newOsmWayId;
    }        
}
