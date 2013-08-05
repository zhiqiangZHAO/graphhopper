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
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
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
public class EdgeDijkstraBidirectionRef extends AbstractEdgeBasedRoutingAlgorithm
{

    private int from, to;
    private int visitedFromCount;
    private PriorityQueue<EdgeEntry> openSetFrom;
    private TIntObjectMap<EdgeEntry> shortestWeightMapFrom;
    private int visitedToCount;
    private PriorityQueue<EdgeEntry> openSetTo;
    private TIntObjectMap<EdgeEntry> shortestWeightMapTo;
    private boolean alreadyRun;
    protected EdgeEntry currFrom;
    protected EdgeEntry currTo;
    protected TIntObjectMap<EdgeEntry> shortestWeightMapOther;
    public PathBidirRef shortest;

    public EdgeDijkstraBidirectionRef( Graph graph, FlagEncoder encoder )
    {
        super(graph, encoder);
        initCollections(Math.max(20, graph.getNodes()));
    }

    protected void initCollections( int nodes )
    {
        openSetFrom = new PriorityQueue<EdgeEntry>(nodes / 10);
        shortestWeightMapFrom = new TIntObjectHashMap<EdgeEntry>(nodes / 10);

        openSetTo = new PriorityQueue<EdgeEntry>(nodes / 10);
        shortestWeightMapTo = new TIntObjectHashMap<EdgeEntry>(nodes / 10);
    }

    public EdgeDijkstraBidirectionRef initFrom( int from )
    {
        this.from = from;
        currFrom = new EdgeEntry(EdgeIterator.NO_EDGE, from, 0);
        //        shortestWeightMapFrom.put(from, currFrom);
        return this;
    }

    public EdgeDijkstraBidirectionRef initTo( int to )
    {
        this.to = to;
        currTo = new EdgeEntry(EdgeIterator.NO_EDGE, to, 0);
        //        shortestWeightMapTo.put(to, currTo);
        return this;
    }

    @Override
    public Path calcPath( int from, int to )
    {
        if ( alreadyRun )
            throw new IllegalStateException("Create a new instance per call");
        alreadyRun = true;
        initPath();
        initFrom(from);
        initTo(to);

        Path p = checkIndenticalFromAndTo();
        if ( p != null )
            return p;

        int finish = 0;
        while ( finish < 2 )
        {
            finish = 0;
            if ( !fillEdgesFrom() )
                finish++;

            if ( !fillEdgesTo() )
                finish++;
        }

        return extractPath();
    }

    public Path extractPath()
    {
        return shortest.extract();
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the extractPath path!!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverseOrder 
    //    search, update extractPath = μ if df (v) + (v, w) + dr (w) < μ            
    public boolean checkFinishCondition()
    {
        if ( currFrom == null )
            return currTo.weight >= shortest.getWeight();
        else if ( currTo == null )
            return currFrom.weight >= shortest.getWeight();
        return currFrom.weight + currTo.weight >= shortest.getWeight();
    }

    void fillEdges( EdgeEntry curr, PriorityQueue<EdgeEntry> prioQueue,
            TIntObjectMap<EdgeEntry> shortestWeightMap, EdgeFilter filter )
    {

        boolean backwards = shortestWeightMapFrom == shortestWeightMapOther;

        int currNode = curr.endNode;
        EdgeIterator iter = graph.getEdges(currNode, filter);
        while ( iter.next() )
        {
            if ( !accept(iter, curr) )
                continue;

            //we need to distinguish between backward and forward direction when storing end weights
            int key = createIterKey(iter, backwards);

            int neighborNode = iter.getAdjNode();
            double tmpWeight = weightCalc.getWeight(iter.getDistance(), iter.getFlags())
                    + curr.weight;
            if ( !backwards )
            {
                tmpWeight += turnCostCalc.getTurnCosts(currNode, curr.edge, iter.getEdge());
            } else
            {
                tmpWeight += turnCostCalc.getTurnCosts(currNode, iter.getEdge(), curr.edge);
            }
            EdgeEntry de = shortestWeightMap.get(key);
            if ( de == null )
            {
                de = new EdgeEntry(iter.getEdge(), neighborNode, tmpWeight);
                de.parent = curr;
                shortestWeightMap.put(key, de);
                prioQueue.add(de);
            } else if ( de.weight > tmpWeight )
            {
                prioQueue.remove(de);
                de.edge = iter.getEdge();
                de.weight = tmpWeight;
                de.parent = curr;
                prioQueue.add(de);
            }

            updateShortest(de, key);
        }
    }

    @Override
    protected void updateShortest( EdgeEntry shortestEE, int edgeKey )
    {
        EdgeEntry entryOther = shortestWeightMapOther.get(edgeKey);
        if ( entryOther == null )
            return;

        //prevents the shortest path to contain the same edge twice, when turn restriction is around the meeting point

        boolean backwards = shortestWeightMapFrom == shortestWeightMapOther;
        if ( entryOther.edge == shortestEE.edge )
        {
            if ( backwards )
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
        if ( !backwards )
        {
            newShortest += turnCostCalc.getTurnCosts(shortestEE.endNode, shortestEE.edge,
                    entryOther.edge);
        } else
        {
            newShortest += turnCostCalc.getTurnCosts(entryOther.endNode, entryOther.edge,
                    shortestEE.edge);
        }

        if ( newShortest < shortest.getWeight() )
        {
            shortest.setSwitchToFrom(backwards);
            shortest.setEdgeEntry(shortestEE);
            shortest.edgeTo = entryOther;
            shortest.setWeight(newShortest);
        }
    }

    public boolean fillEdgesFrom()
    {
        if ( currFrom != null )
        {
            shortestWeightMapOther = shortestWeightMapTo;
            fillEdges(currFrom, openSetFrom, shortestWeightMapFrom, outEdgeFilter);
            visitedFromCount++;
            if ( openSetFrom.isEmpty() )
            {
                currFrom = null;
                return false;
            }

            currFrom = openSetFrom.poll();
            if ( checkFinishCondition() )
                return false;
        } else if ( currTo == null )
            return false;
        return true;
    }

    public boolean fillEdgesTo()
    {
        if ( currTo != null )
        {
            shortestWeightMapOther = shortestWeightMapFrom;
            fillEdges(currTo, openSetTo, shortestWeightMapTo, inEdgeFilter);
            visitedToCount++;
            if ( openSetTo.isEmpty() )
            {
                currTo = null;
                return false;
            }

            currTo = openSetTo.poll();
            if ( checkFinishCondition() )
                return false;
        } else if ( currFrom == null )
            return false;
        return true;
    }

    private Path checkIndenticalFromAndTo()
    {
        if ( from == to )
            return new Path(graph, flagEncoder);
        return null;
    }

    public EdgeEntry shortestWeightFrom( int nodeId )
    {
        return shortestWeightMapFrom.get(nodeId);
    }

    public EdgeEntry shortestWeightTo( int nodeId )
    {
        return shortestWeightMapTo.get(nodeId);
    }

    protected PathBidirRef createPath()
    {
        return new PathBidirRef(graph, flagEncoder);
    }

    public EdgeDijkstraBidirectionRef initPath()
    {
        shortest = createPath();
        return this;
    }

    /**
     * @return number of visited nodes.
     */
    @Override
    public int getVisitedNodes()
    {
        return visitedFromCount + visitedToCount;
    }

    @Override
    public String getName()
    {
        return "dijkstrabi";
    }
}
