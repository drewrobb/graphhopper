/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

/**
 * Iterates through all edges of one node. Avoids object creation in-between via
 * direct access methods.
 *
 * Usage:
 * <pre>
 * // calls to iter.node(), distance() without next() will cause undefined behaviour
 * EdgeIterator iter = graph.getOutgoing(nodeId);
 * // or similar
 * EdgeIterator iter = graph.getIncoming(nodeId);
 * while(iter.next()) {
 *   int baseNodeId = iter.baseNode(); // equal to nodeId
 *   int adjacentNodeId = iter.node();
 *   ...
 * }
 *
 * @author Peter Karich
 */
public interface EdgeIterator {

    /**
     * To be called to go to the next edge
     */
    boolean next();

    /**
     * @return the edge id of the current edge. Although the current
     * implementation uses an index starting from 1, do not make any assumptions
     * about it.
     */
    int edge();

    /**
     * If you retrieve edges via "edgeIterator = graph.getEdges(nodeId)" then
     * the returned node is identical to nodeId. Often only used instead of
     * nodeId for convenience reasons. Do not confuse this with <i>source</i>
     * node of a directed edge.
     *
     * @return the requested node itself
     * @see EdgeIterator
     */
    int baseNode();

    /**
     * @return the node id of the adjacent node (to baseNode) for the current
     * edge.
     * @see EdgeIterator
     */
    int node();

    /**
     * For OSM a way is often a curve not just a straight line and so nodes
     * between tower nodes are necessary to have a more exact geometry (for
     * drawing). Those nodes are called pillar nodes
     *
     * @return pillar nodes
     */
    PointList wayGeometry();

    /**
     * @param list is a sorted collection of nodes between the baseNode and the
     * current adjacent node
     */
    void wayGeometry(PointList list);

    /**
     * @return the distance of the current edge edge
     */
    double distance();

    void distance(double dist);

    int flags();

    void flags(int flags);

    /**
     * @return true if no data is available where we could iterate over
     */
    boolean isEmpty();
    /**
     * integer value to indicate if an edge is valid or not which then would be
     * initialized with this value
     */
    public static final int NO_EDGE = -1;

    static class Edge {

        public static boolean isValid(int edgeId) {
            return edgeId > NO_EDGE;
        }
    }
}
