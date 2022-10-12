package com.graphhopper.reader.osm;

import static com.graphhopper.reader.osm.OSMNodeData.EMPTY_NODE;
import static com.graphhopper.reader.osm.OSMNodeData.JUNCTION_NODE;
import static com.graphhopper.util.Helper.nf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.util.Helper;

public class NodeHandler extends ElementHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeHandler.class);

    private long acceptedNodes = 0;
    private long ignoredSplitNodes = 0;
    
    private final ElevationProvider eleProvider;
    private OSMNodeData nodeData;
    
    public NodeHandler(OSMNodeData nodeData, ElevationProvider eleProvider) {
        super(ReaderElement.Type.NODE);
    	this.nodeData = nodeData;
    	this.eleProvider = eleProvider;
    }

    @Override
    public void onStart() {
        LOGGER.info("pass2 - start reading OSM nodes");
    }
    
    @Override
    public void handleElement(ReaderElement elem) {
        counter++;
        logEvery(LOGGER, 10_000_000);
        handleNode((ReaderNode) elem);
    }
    
    @Override
    public void onFinish() {
        LOGGER.info("pass2 - finished reading OSM nodes, way nodes: {}, with tags: {}, ignored barriers at junctions: {}",
                        nf(acceptedNodes), nf(nodeData.getTaggedNodeCount()), nf(ignoredSplitNodes));
    }
    
    public void handleNode(ReaderNode node) {
        int nodeType = nodeData.addCoordinatesIfMapped(node.getId(), node.getLat(), node.getLon(), () -> eleProvider.getEle(node));
        if (nodeType == EMPTY_NODE)
            return;

        acceptedNodes++;

        // we keep node tags for barrier nodes
        if (isBarrierNode(node)) {
            if (nodeType == JUNCTION_NODE) {
                LOGGER.debug("OSM node {} at {},{} is a barrier node at a junction. The barrier will be ignored",
                        node.getId(), Helper.round(node.getLat(), 7), Helper.round(node.getLon(), 7));
                ignoredSplitNodes++;
            } else
                nodeData.setTags(node);
        }
    }

    /**
     * @return true if the given node should be duplicated to create an artificial edge. If the node turns out to be a
     * junction between different ways this will be ignored and no artificial edge will be created.
     */
    protected boolean isBarrierNode(ReaderNode node) {
    	return node.hasTag("barrier") || node.hasTag("ford");    
    }
}
