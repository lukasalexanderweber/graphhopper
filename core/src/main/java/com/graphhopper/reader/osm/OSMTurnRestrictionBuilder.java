package com.graphhopper.reader.osm;

import static java.util.Collections.emptyMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.PointList;

public class OSMTurnRestrictionBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(OSMTurnRestrictionBuilder.class);
    private long nextArtificialOSMWayId = -Long.MAX_VALUE;
    private HashMap<String, Long> artificialViaNodes = new HashMap<>();
    private long restrictionKey = 0;
    
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
            restrictionKey++;
            // for now we only work with single via-way restrictions 
            if (restriction.getWays().size() == 3) {
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
                    NodeRestriction r = restriction.getRestrictions().get(0);
                    NodeRestriction r2 = restriction.getRestrictions().get(1);
    
                    long entry_node = restriction.getStartNode();
                    long via_node = r.getVia();
                    long exit_node = r2.getVia();
                    SegmentNode entry_node_copy = nodeData.addCopyOfNodeAsTowerNode(new SegmentNode(entry_node, nodeData.getId(entry_node)));
                    SegmentNode via_node_copy = nodeData.addCopyOfNodeAsTowerNode(new SegmentNode(via_node, nodeData.getId(via_node)));
                    SegmentNode exit_node_copy = nodeData.addCopyOfNodeAsTowerNode(new SegmentNode(exit_node, nodeData.getId(exit_node)));
                    storeArtificialNode(entry_node, entry_node_copy);
                    storeArtificialNode(via_node, via_node_copy);
                    storeArtificialNode(exit_node, exit_node_copy);
                    
                    addArtificialWay(entry_node, via_node, restrictionData.osmWayMap.get(r.getFrom()));
                    addArtificialWay(via_node, exit_node, restrictionData.osmWayMap.get(r2.getFrom()));
                    
                    enterRestrictionGraph(entry_node);
                    long leaveId = leaveRestrictionGraph(exit_node);
                    
                    ArrayList<NodeRestriction> restrictions = new ArrayList<>();
                    restrictions.add(new NodeRestriction(r.getFrom(), r.getVia(), r.getTo()));
                    restrictions.add(new NodeRestriction(leaveId, r2.getVia(), r2.getTo()));
                    restrictionData.artificialNodeRestrictions.put(restriction.getId(), restrictions);

                } catch (Exception e) {
                    LOGGER.info("|" + restriction.getId() + "|failed|" + e);
                    restrictionData.invalid_way_restrictions++;
                    continue;
                }
                LOGGER.info("|" + restriction.getId() + "|success|");
            }
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
    
    private void storeArtificialNode(long node, SegmentNode node_copy) {
        String node_id = restrictionKey + ":" + node;
        artificialViaNodes.put(node_id, node_copy.osmNodeId);
    }
    
    private long getArtificialNode(long node) {
        String node_id = restrictionKey + ":" + node;
        return artificialViaNodes.get(node_id);
    }
    
    private void addArtificialWay(long osmFrom, long osmTo, ReaderWay originalWay) {
        int from = nodeData.idToTowerNode(nodeData.getId(getArtificialNode(osmFrom)));
        int to = nodeData.idToTowerNode(nodeData.getId(getArtificialNode(osmTo)));
        long newOsmWayId = nextArtificialOSMWayId++;
        restrictionData.osmWayIdSet.add(newOsmWayId);      
        ReaderWay artificial_way = new ReaderWay(newOsmWayId, originalWay.getTags(), originalWay.getNodes());
        LongArrayList nodes = originalWay.getNodes();
        PointList pointList = new PointList(nodes.size(), nodeData.is3D());
        for (LongCursor point : nodes) {
            nodeData.addCoordinatesToPointList(nodeData.getId(point.value), pointList);
        }

        wayHandler.addEdge(from, to, pointList, artificial_way, emptyMap());
    }
    
    private long enterRestrictionGraph(long node) {
    	long osmTo = getArtificialNode(node);
        int from = nodeData.idToTowerNode(nodeData.getId(node));
        int to = nodeData.idToTowerNode(nodeData.getId(osmTo));
        return create0DistanceArificialOneWay(from, to, node, osmTo);
    }
    
    private long leaveRestrictionGraph(long node) {
    	long osmFrom = getArtificialNode(node);
        int from = nodeData.idToTowerNode(nodeData.getId(osmFrom));
        int to = nodeData.idToTowerNode(nodeData.getId(node));
        return create0DistanceArificialOneWay(from, to, osmFrom, node);
    }
    
    private long create0DistanceArificialOneWay(int from, int to, long osmFrom, long osmTo) {
        long newOsmId = nextArtificialOSMWayId++;
        restrictionData.osmWayIdSet.add(newOsmId);
        
        LongArrayList longalist = new LongArrayList();
        longalist.add(osmFrom);
        longalist.add(osmTo);

        Map<String, Object> properties = new HashMap<>();
        properties.put("oneway", "yes");
        properties.put("highway", "motorway"); // TODO same as Input?
        
        ReaderWay artificial_way = new ReaderWay(newOsmId, properties, longalist);
        
        PointList pointList = new PointList(2, nodeData.is3D());
        nodeData.addCoordinatesToPointList(nodeData.towerNodeToId(from), pointList);
        nodeData.addCoordinatesToPointList(nodeData.towerNodeToId(to), pointList);
        
        wayHandler.addEdge(from, to, pointList, artificial_way, emptyMap());
        return newOsmId;
    }
}
