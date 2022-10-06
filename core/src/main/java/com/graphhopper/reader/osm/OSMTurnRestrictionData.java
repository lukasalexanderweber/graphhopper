package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.IntLongMap;
import com.graphhopper.coll.GHIntLongHashMap;
import com.graphhopper.coll.GHLongHashSet;

public class OSMTurnRestrictionData {	
    // stores osm way ids used by relations to identify which edge ids needs to be mapped later
    public GHLongHashSet osmWayIdSet = new GHLongHashSet();
    // we use negative ids to create artificial OSM way ids
    public IntLongMap edgeIdToOsmWayIdMap = new GHIntLongHashMap(osmWayIdSet.size(), 0.5f);
    
    public OSMTurnRestrictionData() {
    	
    }
}
