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

package com.graphhopper.routing.util.parsers;

import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.IntIntMap;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.reader.osm.GraphRestriction;
import com.graphhopper.reader.osm.Pair;
import com.graphhopper.reader.osm.RestrictionType;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;

import java.util.List;

import static com.graphhopper.reader.osm.RestrictionType.NO;
import static com.graphhopper.reader.osm.RestrictionType.ONLY;

public class RestrictionSetter {
    private final BaseGraph baseGraph;
    private final EdgeExplorer edgeExplorer;
    private final IntIntMap artificialEdgesByEdges = new IntIntHashMap();

    public RestrictionSetter(BaseGraph baseGraph) {
        this.baseGraph = baseGraph;
        this.edgeExplorer = baseGraph.createEdgeExplorer();
    }

    /**
     * Adds all the turn restriction entries to the graph that are needed to enforce the given restrictions, for
     * a single turn cost encoded value.
     * Implementing via-way turn restrictions requires adding artificial edges to the graph, which is also handled here.
     * Since we keep track of the added artificial edges here it is important to only use one RestrictionSetter instance
     * for **all** turn restrictions and vehicle types.
     */
    public void setRestrictions(List<Pair<GraphRestriction, RestrictionType>> restrictions, DecimalEncodedValue turnCostEnc) {
        // we first need to add all the artificial edges, because we might need to restrict turns between artificial
        // edges created for different restrictions (when restrictions are overlapping)
        addArtificialEdges(restrictions);
        // now we can add all the via-way restrictions
        addViaWayRestrictions(restrictions, turnCostEnc);
        // ... and finally all the via-node restrictions
        addViaNodeRestrictions(restrictions, turnCostEnc);
    }

    private void addArtificialEdges(List<Pair<GraphRestriction, RestrictionType>> restrictions) {
        for (Pair<GraphRestriction, RestrictionType> p : restrictions) {
            if (p.first.isViaWayRestriction()) {
                if (ignoreViaWayRestriction(p)) continue;
                for (IntCursor viaEdgeCursor : p.first.getViaEdges()) {
                    int viaEdge = viaEdgeCursor.value;
                    int artificialEdge = artificialEdgesByEdges.getOrDefault(viaEdge, -1);
                    if (artificialEdge < 0) {
                        EdgeIteratorState viaEdgeState = baseGraph.getEdgeIteratorState(viaEdge, Integer.MIN_VALUE);
                        EdgeIteratorState artificialEdgeState = baseGraph.edge(viaEdgeState.getBaseNode(), viaEdgeState.getAdjNode())
                                .setFlags(viaEdgeState.getFlags())
                                .setWayGeometry(viaEdgeState.fetchWayGeometry(FetchMode.PILLAR_ONLY))
                                .setDistance(viaEdgeState.getDistance())
                                .setKeyValues(viaEdgeState.getKeyValues());
                        artificialEdge = artificialEdgeState.getEdge();
                        artificialEdgesByEdges.put(viaEdge, artificialEdge);
                    }
                }
            }
        }
    }

    private void addViaWayRestrictions(List<Pair<GraphRestriction, RestrictionType>> restrictions, DecimalEncodedValue turnCostEnc) {
        for (Pair<GraphRestriction, RestrictionType> p : restrictions) {
            if (!p.first.isViaWayRestriction()) continue;
            if (ignoreViaWayRestriction(p)) continue;
            final int fromEdge = p.first.getFromEdges().get(0);
            final int toEdge = p.first.getToEdges().get(0);
            final int artificialFrom = artificialEdgesByEdges.getOrDefault(fromEdge, fromEdge);
            final int artificialTo = artificialEdgesByEdges.getOrDefault(toEdge, toEdge);
            
            final int nViaEdges = p.first.getViaEdges().size();
                        
            for (int i = 0; i < nViaEdges; i++) {
                int viaNode = p.first.getViaNodes().get(i);
                int viaNode2 = p.first.getViaNodes().get(i+1);
                int viaEdge = p.first.getViaEdges().get(i);
                int artificialVia = artificialEdgesByEdges.getOrDefault(viaEdge, viaEdge);
                if (artificialVia == viaEdge)
                    throw new IllegalArgumentException("There should be an artificial edge for every via edge of a way restriction");
                
                // never turn between an artificial edge and its corresponding real edge
                restrictTurn(turnCostEnc, artificialVia, viaNode, viaEdge);
                restrictTurn(turnCostEnc, viaEdge, viaNode, artificialVia);
                restrictTurn(turnCostEnc, artificialVia, viaNode2, viaEdge);
                restrictTurn(turnCostEnc, viaEdge, viaNode2, artificialVia);
                
                if (i == 0) {
                    if (p.second == NO) {
                        restrictTurn(turnCostEnc, fromEdge, viaNode, viaEdge);
                        // if there is an artificial from edge of an overlapping restriction 
                        // we restrict it the same way as the from edge
                        if (artificialFrom != fromEdge)
                            restrictTurn(turnCostEnc, artificialFrom, viaNode, artificialVia);
                    } else if (p.second == ONLY) {
                        EdgeIterator iter = edgeExplorer.setBaseNode(viaNode);
                        while (iter.next())
                            if (artificialFrom != fromEdge) {
                                if (iter.getEdge() != artificialFrom && iter.getEdge() != fromEdge && iter.getEdge() != artificialVia)
                                    restrictTurn(turnCostEnc, fromEdge, viaNode, iter.getEdge());
                            } else {
                                if (iter.getEdge() != fromEdge && iter.getEdge() != artificialVia)
                                    restrictTurn(turnCostEnc, fromEdge, viaNode, iter.getEdge());
                            }
                    }
                }
                
                if (i > 0 && i < nViaEdges) {
                    int oldArtificialViaEdge = artificialEdgesByEdges.get(p.first.getViaEdges().get(i-1));
                    if (p.second == NO) {
                        restrictTurn(turnCostEnc, oldArtificialViaEdge, viaNode, viaEdge);
                    } else if (p.second == ONLY) {
                        EdgeIterator iter = edgeExplorer.setBaseNode(viaNode);
                        while (iter.next())
                            if (iter.getEdge() != oldArtificialViaEdge && iter.getEdge() != artificialVia)
                                restrictTurn(turnCostEnc, oldArtificialViaEdge, viaNode, iter.getEdge());
                    }
                }

                
                if (i == nViaEdges - 1) {
                    if (p.second == NO) {
                        restrictTurn(turnCostEnc, artificialVia, viaNode2, toEdge);
                        // if there is an artificial to edge of an overlapping restriction 
                        // we restrict it the same way as the from edge
                        if (artificialTo != toEdge)
                            restrictTurn(turnCostEnc, artificialVia, viaNode2, artificialTo);
                    } else if (p.second == ONLY) {
                        EdgeIterator iter = edgeExplorer.setBaseNode(viaNode2);
                        while (iter.next())
                            if (iter.getEdge() != artificialVia && iter.getEdge() != toEdge)
                                restrictTurn(turnCostEnc, artificialVia, viaNode2, iter.getEdge());
                    }
                }
            }
        }
    }
        
    private void addViaNodeRestrictions(List<Pair<GraphRestriction, RestrictionType>> restrictions, DecimalEncodedValue turnCostEnc) {
        for (Pair<GraphRestriction, RestrictionType> p : restrictions) {
            if (p.first.isViaWayRestriction()) continue;
            final int viaNode = p.first.getViaNodes().get(0);
            for (IntCursor fromEdgeCursor : p.first.getFromEdges()) {
                for (IntCursor toEdgeCursor : p.first.getToEdges()) {
                    final int fromEdge = fromEdgeCursor.value;
                    final int toEdge = toEdgeCursor.value;
                    final int artificialFrom = artificialEdgesByEdges.getOrDefault(fromEdge, fromEdge);
                    final int artificialTo = artificialEdgesByEdges.getOrDefault(toEdge, toEdge);
                    if (p.second == NO) {
                        restrictTurn(turnCostEnc, fromEdge, viaNode, toEdge);
                        // we also need to restrict this term in case there are artificial edges for the from- and/or to-edge
                        if (artificialFrom != fromEdge)
                            restrictTurn(turnCostEnc, artificialFrom, viaNode, toEdge);
                        if (artificialTo != toEdge)
                            restrictTurn(turnCostEnc, fromEdge, viaNode, artificialTo);
                        if (artificialFrom != fromEdge && artificialTo != toEdge)
                            restrictTurn(turnCostEnc, artificialFrom, viaNode, artificialTo);
                    } else if (p.second == ONLY) {
                        // we need to restrict all turns except the one, but that also means not restricting the
                        // artificial counterparts of these turns, if they exist.
                        // we do not explicitly restrict the U-turn from the from-edge back to the from-edge though.
                        EdgeIterator iter = edgeExplorer.setBaseNode(viaNode);
                        while (iter.next()) {
                            if (iter.getEdge() != fromEdge && iter.getEdge() != toEdge && iter.getEdge() != artificialTo)
                                restrictTurn(turnCostEnc, fromEdge, viaNode, iter.getEdge());
                            // and the same for the artificial edge belonging to the from-edge if it exists
                            if (fromEdge != artificialFrom && iter.getEdge() != artificialFrom && iter.getEdge() != toEdge && iter.getEdge() != artificialTo)
                                restrictTurn(turnCostEnc, artificialFrom, viaNode, iter.getEdge());
                        }
                    } else {
                        throw new IllegalArgumentException("Unexpected restriction type: " + p.second);
                    }
                }
            }
        }
    }

    private void restrictTurn(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge) {
        if (fromEdge < 0 || toEdge < 0 || viaNode < 0)
            throw new IllegalArgumentException("from/toEdge and viaNode must be >= 0");
        baseGraph.getTurnCostStorage().set(turnCostEnc, fromEdge, viaNode, toEdge, Double.POSITIVE_INFINITY);
//        if (type == RestrictionType.NO)
//            prohibitoryRestriction(turnCostEnc, fromEdge, viaNode, toEdge);
//        if (type == RestrictionType.ONLY)
//            mandatoryRestriction(turnCostEnc, fromEdge, viaNode, toEdge);
    }
    
//    private void prohibitoryRestriction(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge) {
//        baseGraph.getTurnCostStorage().set(turnCostEnc, fromEdge, viaNode, toEdge, Double.POSITIVE_INFINITY);
//    }
//    
//    private void mandatoryRestriction(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge) {
//        EdgeIterator iter = edgeExplorer.setBaseNode(viaNode);
//        while (iter.next())
//            if (iter.getEdge() != fromEdge && iter.getEdge() != toEdge)
//                prohibitoryRestriction(turnCostEnc, fromEdge, viaNode, iter.getEdge());
//    }
    
    private static boolean ignoreViaWayRestriction(Pair<GraphRestriction, RestrictionType> p) {
        if (p.first.getFromEdges().size() > 1 || p.first.getToEdges().size() > 1)
            // no multi-from or -to yet
            return true;
        return false;
    }

}