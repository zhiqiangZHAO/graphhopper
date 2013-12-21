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

import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathBidirRef;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

/**
 * An edge-based version of bidirectional Dijkstra. End link costs will be stored for each edge
 * instead of for each node. This is necessary when considering turn costs, but will be slower than
 * the classic version.
 * 
 * @see http://www.easts.info/on-line/journal_06/1426.pdf
 * 
 *      TODO we better should reuse the code of DijkstraBidirectionRef instead instead of copying
 *      it. should be done later
 * 
 * @author Karl Hübner
 */
public class EdgeDijkstraBidirectionRef extends AbstractEdgeBasedBidirAlgo
{

    //    private int from, to;
    private PriorityQueue<EdgeEntry> openSetFrom;
    private TIntObjectMap<EdgeEntry> bestWeightMapFrom;
    private PriorityQueue<EdgeEntry> openSetTo;
    private TIntObjectMap<EdgeEntry> bestWeightMapTo;
    protected TIntObjectMap<EdgeEntry> bestWeightMapOther;
    protected EdgeEntry currFrom;
    protected EdgeEntry currTo;

    public PathBidirRef bestPath;

    public EdgeDijkstraBidirectionRef( Graph g, FlagEncoder encoder, Weighting weighting )
    {
        super(g, encoder, weighting);
        initCollections(Math.max(20, graph.getNodes()));
    }

    protected void initCollections( int nodes )
    {
        openSetFrom = new PriorityQueue<EdgeEntry>(nodes / 10);
        bestWeightMapFrom = new TIntObjectHashMap<EdgeEntry>(nodes / 10);

        openSetTo = new PriorityQueue<EdgeEntry>(nodes / 10);
        bestWeightMapTo = new TIntObjectHashMap<EdgeEntry>(nodes / 10);
    }

    public void initFrom( int from, double dist )
    {
        currFrom = createEdgeEntry(from, dist);
        openSetFrom.add(currFrom);
        if (currTo != null)
        {
            bestWeightMapOther = bestWeightMapTo;
            updateShortest(currTo, from);
        }
    }

    @Override
    public void initTo( int to, double dist )
    {
        currTo = createEdgeEntry(to, dist);
        openSetTo.add(currTo);
        if (currFrom != null)
        {
            bestWeightMapOther = bestWeightMapFrom;
            updateShortest(currFrom, to);
        }
    }

    @Override
    protected void initPath()
    {
        bestPath = new PathBidirRef(graph, flagEncoder);
    }

    @Override
    protected Path extractPath()
    {
        return bestPath.extract();
    }

    @Override
    protected void checkState( int fromBase, int fromAdj, int toBase, int toAdj )
    {
        if (bestWeightMapFrom.isEmpty() || bestWeightMapTo.isEmpty())
            throw new IllegalStateException("Either 'from'-edge or 'to'-edge is inaccessible. From:" + bestWeightMapFrom
                    + ", to:" + bestWeightMapTo);
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the extractPath path!!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverseOrder 
    //    search, update extractPath = μ if df (v) + (v, w) + dr (w) < μ            
    @Override
    protected boolean finished()
    {
        if (finishedFrom || finishedTo)
            return true;

        return currFrom.weight + currTo.weight >= bestPath.getWeight();
    }

    void fillEdges( EdgeEntry currEdge, PriorityQueue<EdgeEntry> prioQueue,
            TIntObjectMap<EdgeEntry> shortestWeightMap, EdgeExplorer explorer )
    {

        boolean backwards = bestWeightMapFrom == bestWeightMapOther;

        int currNode = currEdge.endNode;
        EdgeIterator iter = explorer.setBaseNode(currNode);
        while ( iter.next() )
        {
            if (!accept(iter, currEdge))
                continue;
            // minor speed up
            if (currEdge.edge == iter.getEdge())
                continue;

            //we need to distinguish between backward and forward direction when storing end weights
            int key = createIterKey(iter, backwards);

            int neighborNode = iter.getAdjNode();
            double tmpWeight = weighting.calcWeight(iter) + currEdge.weight;
            if (!backwards)
            {
                tmpWeight += turnCostCalc.getTurnCosts(currNode, currEdge.edge, iter.getEdge());
            } else
            {
                tmpWeight += turnCostCalc.getTurnCosts(currNode, iter.getEdge(), currEdge.edge);
            }
            EdgeEntry de = shortestWeightMap.get(key);
            if (de == null)
            {
                de = new EdgeEntry(iter.getEdge(), neighborNode, tmpWeight);
                de.parent = currEdge;
                shortestWeightMap.put(key, de);
                prioQueue.add(de);
            } else if (de.weight > tmpWeight)
            {
                prioQueue.remove(de);
                de.edge = iter.getEdge();
                de.weight = tmpWeight;
                de.parent = currEdge;
                prioQueue.add(de);
            }

            updateShortest(de, key);
        }
    }

    @Override
    protected void updateShortest( EdgeEntry shortestEE, int edgeKey )
    {
        EdgeEntry entryOther = bestWeightMapOther.get(edgeKey);
        if (entryOther == null)
            return;

        //prevents the shortest path to contain the same edge twice, when turn restriction is around the meeting point

        boolean backwards = bestWeightMapFrom == bestWeightMapOther;
        if (entryOther.edge == shortestEE.edge)
        {
            if (backwards)
            {
                entryOther = entryOther.parent;
            } else
            {
                shortestEE = shortestEE.parent;
            }
        }

        // update μ
        double newShortest = shortestEE.weight + entryOther.weight;

        //costs for the turn where forward and backward routing meet each other
        if (!backwards)
        {
            newShortest += turnCostCalc.getTurnCosts(shortestEE.endNode, shortestEE.edge,
                    entryOther.edge);
        } else
        {
            newShortest += turnCostCalc.getTurnCosts(entryOther.endNode, entryOther.edge,
                    shortestEE.edge);
        }

        if (newShortest < bestPath.getWeight())
        {
            bestPath.setSwitchToFrom(backwards);
            bestPath.setEdgeEntry(shortestEE);
            bestPath.edgeTo = entryOther;
            bestPath.setWeight(newShortest);
        }
    }

    @Override
    protected boolean fillEdgesFrom()
    {
        if (openSetFrom.isEmpty())
            return false;
        currFrom = openSetFrom.poll();

        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, openSetFrom, bestWeightMapFrom, outEdgeExplorer);
        visitedFromCount++;
        return true;
    }

    @Override
    protected boolean fillEdgesTo()
    {
        if (openSetTo.isEmpty())
            return false;
        currTo = openSetTo.poll();

        bestWeightMapOther = bestWeightMapFrom;
        fillEdges(currTo, openSetTo, bestWeightMapTo, inEdgeExplorer);
        visitedToCount++;
        return true;
    }

    public EdgeEntry shortestWeightFrom( int nodeId )
    {
        return bestWeightMapFrom.get(nodeId);
    }

    public EdgeEntry shortestWeightTo( int nodeId )
    {
        return bestWeightMapTo.get(nodeId);
    }

    protected PathBidirRef createPath()
    {
        return new PathBidirRef(graph, flagEncoder);
    }

    @Override
    public String getName()
    {
        return "edge-based dijkstrabi";
    }
}
