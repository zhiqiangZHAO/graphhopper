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
package com.graphhopper.routing.edgebased;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.PriorityQueue;

import com.graphhopper.routing.AStar.AStarEdge;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

/**
 * An edge-based version of AStar. End link costs will be stored for each edge instead of for each
 * node. This is necessary when considering turn costs, but will be slower than classic AStar.
 * 
 * @see http://www.easts.info/on-line/journal_06/1426.pdf
 * 
 *      TODO we better should reuse the code of AStar instead instead of copying it. should be done
 *      later
 * 
 * @author Karl Hübner
 */
public class EdgeAStar extends AbstractEdgeBasedRoutingAlgorithm
{

    private DistanceCalc dist = new DistancePlaneProjection();
    private boolean alreadyRun;
    private int visitedCount;

    public EdgeAStar( Graph g, FlagEncoder encoder, WeightCalculation type )
    {
        super(g, encoder, type);
    }

    /**
     * @param fast
     *            if true it enables an approximative distance calculation from lat,lon values
     */
    public EdgeAStar setApproximation( boolean approx )
    {
        if ( approx )
            dist = new DistancePlaneProjection();
        else
            dist = new DistanceCalc();
        return this;
    }

    @Override
    public Path calcPath( int from, int to )
    {
        if ( alreadyRun )
            throw new IllegalStateException("Create a new instance per call");
        alreadyRun = true;
        TIntObjectMap<AStarEdge> map = new TIntObjectHashMap<AStarEdge>();
        PriorityQueue<AStarEdge> prioQueueOpenSet = new PriorityQueue<AStarEdge>(1000);
        double toLat = graph.getLatitude(to);
        double toLon = graph.getLongitude(to);
        double currWeightToGoal, distEstimation, tmpLat, tmpLon;
        AStarEdge fromEntry = new AStarEdge(EdgeIterator.NO_EDGE, from, 0, 0);
        AStarEdge currEdge = fromEntry;
        EdgeExplorer explorer = outEdgeExplorer;
        while ( true )
        {
            int currVertex = currEdge.endNode;
            visitedCount++;
            if ( finished(currEdge, to) )
                break;

            
            explorer.setBaseNode(currVertex);
            while ( explorer.next() )
            {
                if ( !accept(explorer, currEdge) )
                    continue;

                //we need to distinguish between backward and forward direction when storing end weights
                int key = createIterKey(explorer, true);

                int neighborNode = explorer.getAdjNode();
                double alreadyVisitedWeight = weightCalc.getWeight(explorer.getDistance(),
                        explorer.getFlags())
                        + currEdge.weightToCompare;
                alreadyVisitedWeight += turnCostCalc.getTurnCosts(currVertex, currEdge.edge,
                        explorer.getEdge());
                AStarEdge nEdge = map.get(key);
                if ( nEdge == null || nEdge.weightToCompare > alreadyVisitedWeight )
                {
                    tmpLat = graph.getLatitude(neighborNode);
                    tmpLon = graph.getLongitude(neighborNode);
                    currWeightToGoal = dist.calcDist(toLat, toLon, tmpLat, tmpLon);
                    currWeightToGoal = weightCalc.getMinWeight(currWeightToGoal);
                    distEstimation = alreadyVisitedWeight + currWeightToGoal;
                    if ( nEdge == null )
                    {
                        nEdge = new AStarEdge(explorer.getEdge(), neighborNode, distEstimation,
                                alreadyVisitedWeight);
                        map.put(key, nEdge);
                    } else
                    {
                        prioQueueOpenSet.remove(nEdge);
                        nEdge.edge = explorer.getEdge();
                        nEdge.weight = distEstimation;
                        nEdge.weightToCompare = alreadyVisitedWeight;
                    }
                    nEdge.parent = currEdge;
                    prioQueueOpenSet.add(nEdge);
                    updateShortest(nEdge, neighborNode);
                }
            }

            if ( prioQueueOpenSet.isEmpty() )
                return new Path(graph, flagEncoder);

            currEdge = prioQueueOpenSet.poll();
            if ( currEdge == null )
                throw new AssertionError("cannot happen?");
        }

        return extractPath(currEdge);
    }

    boolean finished( EdgeEntry currEdge, int to )
    {
        return currEdge.endNode == to;
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedCount;
    }

    Path extractPath( EdgeEntry currEdge )
    {
        return new Path(graph, flagEncoder).setEdgeEntry(currEdge).extract();
    }

    @Override
    public String getName()
    {
        return "astar";
    }
}
