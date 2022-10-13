package com.graphhopper.reader.osm;

import java.util.ArrayList;
import java.util.HashMap;

import com.carrotsearch.hppc.IntLongMap;
import com.graphhopper.coll.GHIntLongHashMap;
import com.graphhopper.coll.GHLongHashSet;
import com.graphhopper.reader.ReaderWay;

public class OSMTurnRestrictionData {	
    // stores osm way ids used by relations to identify which edge ids needs to be mapped later
    public GHLongHashSet osmWayIdSet = new GHLongHashSet();
    // for via way restrictions we additionally need to store all ReaderWay objects which are part of the restriction
    public HashMap<Long, ReaderWay> osmWayMap = new HashMap<>();
    public ArrayList<NodeRestriction> nodeRestrictions = new ArrayList<>();
    public ArrayList<WayRestriction> wayRestrictions = new ArrayList<>();
    public HashMap<Long, Long> artificialViaNodes = new HashMap<>();
    // for every via way restriction we build artificial node restrictions
    public HashMap<Long, ArrayList<NodeRestriction>> artificialNodeRestrictions = new HashMap<>();
    public ArrayList<Long> osmWayIdsToIgnore = new ArrayList<>();
    public IntLongMap edgeIdToOsmWayIdMap = new GHIntLongHashMap(osmWayIdSet.size(), 0.5f);
}
