package com.graphhopper.reader.osm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.graphhopper.reader.ReaderWay;

public class WayRestriction {
    private final Long id;
    private List<Long> ways;
    private List<NodeRestriction> restrictions;
    private Long startNode;
    private boolean valid;

    public WayRestriction(Long id, List<Long> ways) {
        if (ways.size() < 3)
            throw new IllegalArgumentException("Relation " + id + ": Via Way Restriction needs 3 or more ways");
        this.id = id;
        this.ways = ways;
        this.restrictions = new ArrayList<NodeRestriction>(2);
        this.valid = false;
    }

    public void buildRestriction(HashMap<Long, ReaderWay> wayMap) {
        for (int i = 0; i < ways.size() - 1; i++) {
            Long fromId = ways.get(i);
            Long toId = ways.get(i + 1);
            ReaderWay from = wayMap.get(fromId);
            ReaderWay to = wayMap.get(toId);
            if (from == null || to == null) 
                return;
            Long via = getViaNode(from, to);
            restrictions.add(new NodeRestriction(fromId, via, toId));
        }
        valid = true;
    }

    public Long getViaNode(ReaderWay from, ReaderWay to) {
        Long[] fromEndNodes = from.getEndNodes();
        Long[] toEndNodes = to.getEndNodes();
        
        if (Arrays.asList(toEndNodes).contains(fromEndNodes[0]))
            return fromEndNodes[0];
        else if (Arrays.asList(toEndNodes).contains(fromEndNodes[1]))
            return fromEndNodes[1];
        else 
            throw new IllegalStateException("No Via Node found!");
    }

    public Long getId() {
        return id;
    }
    
    public List<Long> getWays() {
        return ways;
    }
    
	public boolean isValid() {
		return valid;
	}
	
    public Long getStartNode() {
        if (startNode < 0)
            throw new IllegalStateException("Restriction has not yet been build");
        return startNode;
    }
    
    public List<NodeRestriction> getRestrictions() {
        if (restrictions.size() == 0)
            throw new IllegalStateException("Restriction has not yet been build");
        return restrictions;
    }
}
