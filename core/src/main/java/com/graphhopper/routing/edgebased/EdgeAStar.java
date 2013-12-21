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
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
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

    private DistanceCalc dist;
    private int visitedCount;
    private TIntObjectMap<AStarEdge> fromMap;
    private PriorityQueue<AStarEdge> prioQueueOpenSet;
    private AStarEdge currEdge;
    private int to1 = -1;
    private double toLat;
    private double toLon;

    public EdgeAStar( Graph g, FlagEncoder encoder, Weighting weighting )
    {
        super(g, encoder, weighting);
        initCollections(1000);
        setApproximation(true);
    }

    protected void initCollections( int size )
    {
        fromMap = new TIntObjectHashMap<AStarEdge>();
        prioQueueOpenSet = new PriorityQueue<AStarEdge>(size);
    }

    /**
     * @param fast if true it enables an approximative distance calculation from lat,lon values
     */
    public EdgeAStar setApproximation( boolean approx )
    {
        if (approx)
            dist = new DistancePlaneProjection();
        else
            dist = new DistanceCalcEarth();
        return this;
    }

    @Override
    public Path calcPath( int from, int to )
    {
        checkAlreadyRun();
        toLat = graph.getLatitude(to);
        toLon = graph.getLongitude(to);
        to1 = to;
        currEdge = createEdgeEntry(from, 0);
        return runAlgo();
    }

    private Path runAlgo()
    {
        if(!fromMap.isEmpty()){
            throw new AssertionError("the fromMap for edgebased algorithms must be empty");
        }
        
        double currWeightToGoal, distEstimation, tmpLat, tmpLon;
        EdgeExplorer explorer = outEdgeExplorer;
        while ( true )
        {
            int currVertex = currEdge.endNode;
            visitedCount++;
            if (finished())
                break;

            EdgeIterator iter = explorer.setBaseNode(currVertex);
            while ( iter.next() )
            {
                if (!accept(iter, currEdge))
                    continue;
                if (currEdge.edge == iter.getEdge())
                    continue;

                //we need to distinguish between backward and forward direction when storing end weights
                int key = createIterKey(iter, true);

                int neighborNode = iter.getAdjNode();
                double alreadyVisitedWeight = weighting.calcWeight(iter) + currEdge.weightToCompare;
                alreadyVisitedWeight += turnCostCalc.getTurnCosts(currVertex, currEdge.edge, iter.getEdge());
                AStarEdge nEdge = fromMap.get(key);
                if (nEdge == null || nEdge.weightToCompare > alreadyVisitedWeight)
                {
                    tmpLat = graph.getLatitude(neighborNode);
                    tmpLon = graph.getLongitude(neighborNode);
                    currWeightToGoal = dist.calcDist(toLat, toLon, tmpLat, tmpLon);
                    currWeightToGoal = weighting.getMinWeight(currWeightToGoal);
                    distEstimation = alreadyVisitedWeight + currWeightToGoal;
                    if (nEdge == null)
                    {
                        nEdge = new AStarEdge(iter.getEdge(), neighborNode, distEstimation,
                                alreadyVisitedWeight);
                        fromMap.put(key, nEdge);
                    } else
                    {
                        prioQueueOpenSet.remove(nEdge);
                        nEdge.edge = iter.getEdge();
                        nEdge.weight = distEstimation;
                        nEdge.weightToCompare = alreadyVisitedWeight;
                    }
                    nEdge.parent = currEdge;
                    prioQueueOpenSet.add(nEdge);
                    updateShortest(nEdge, neighborNode);
                }
            }

            if (prioQueueOpenSet.isEmpty())
                return createEmptyPath();

            currEdge = prioQueueOpenSet.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");
        }

        return extractPath();
    }

    @Override
    protected boolean finished()
    {
        return currEdge.endNode == to1;
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedCount;
    }

    @Override
    protected Path extractPath()
    {
        return new Path(graph, flagEncoder).setEdgeEntry(currEdge).extract();
    }

    @Override
    protected AStarEdge createEdgeEntry( int node, double dist )
    {
        return new AStarEdge(EdgeIterator.NO_EDGE, node, dist, dist);
    }

    @Override
    public String getName()
    {
        return "edgeAstar";
    }
}
