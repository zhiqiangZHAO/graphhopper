package com.graphhopper.reader;

import java.util.ArrayList;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.coll.LongIntMap;
import com.graphhopper.routing.util.TurnCostEncoder;
import com.graphhopper.routing.util.TurnCostsEntry;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.GraphTurnCosts;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;

/**
 * Helper object which gives node cost entries for a given OSM-relation of type "restriction"
 */
public class OSMRestrictionRelation
{

    private final Logger logger = LoggerFactory.getLogger(getClass());
    public static final int TYPE_UNSUPPORTED = 0;
    public static final int TYPE_NO_LEFT_TURN = 1;
    public static final int TYPE_NO_RIGHT_TURN = 2;
    public static final int TYPE_NO_STRAIGHT_ON = 3;
    public static final int TYPE_ONLY_RIGHT_TURN = 4;
    public static final int TYPE_ONLY_LEFT_TURN = 5;
    public static final int TYPE_ONLY_STRAIGHT_ON = 6;
    public static final int TYPE_NO_U_TURN = 7;
    protected long fromOsm;
    protected int via;
    protected long toOsm;
    protected int restriction;

    /**
     * @return <code>true</code>, if restriction type is supported and a via node has been found
     */
    public boolean isValid()
    {
        return restriction != TYPE_UNSUPPORTED && via >= 0 && fromOsm >= 0 && toOsm >= 0;
    }

    /**
     * transforms this relation into a collection of node cost entries
     * 
     * @param g the graph which provides node cost tables
     * @param edgeOutFilter an edge filter which only allows outgoing edges
     * @param edgeInFilter an edge filter which only allows incoming edges
     * @return a collection of node cost entries which can be added to the graph later
     */
    public Collection<TurnCostsEntry> getAsEntries( GraphTurnCosts g,
            EdgeExplorer edgeOutExplorer, EdgeExplorer edgeInExplorer, DataAccess osmidsOfEdges )
    {
        Collection<TurnCostsEntry> entries = new ArrayList<TurnCostsEntry>(3);
        if (via == EdgeIterator.NO_EDGE)
        {
            return entries;
        }
        try
        {
            int edgeIdFrom = EdgeIterator.NO_EDGE;

            // get all incoming edges and receive the edge which is defined by osmFrom
            EdgeIterator iter = edgeInExplorer.setBaseNode(via);

            while ( iter.next() )
            {
                if (osmid(iter.getEdge(), osmidsOfEdges) == fromOsm)
                {
                    edgeIdFrom = iter.getEdge();
                    break;
                }
            }

            //get all outgoing edges of the via node 
            iter = edgeOutExplorer.setBaseNode(via);
            if (edgeIdFrom != EdgeIterator.NO_EDGE)
            {
                if (restriction == TYPE_NO_U_TURN
                        || restriction == TYPE_NO_LEFT_TURN
                        || restriction == TYPE_NO_RIGHT_TURN
                        || restriction == TYPE_NO_STRAIGHT_ON)
                {
                    // if we have a restriction of TYPE_NO_* we add restriction only to
                    // the given turn (from, via, to)  
                    while ( iter.next() )
                    {
                        if (iter.getEdge() != edgeIdFrom
                                && osmid(iter.getEdge(), osmidsOfEdges) == toOsm)
                        {
                            entries.add(new TurnCostsEntry()
                                    .flags(TurnCostEncoder.restriction()).node(via)
                                    .edgeFrom(edgeIdFrom)
                                    .edgeTo(iter.getEdge()));
                        }
                    }

                } else if (restriction == TYPE_ONLY_RIGHT_TURN
                        || restriction == TYPE_ONLY_LEFT_TURN
                        || restriction == TYPE_ONLY_STRAIGHT_ON)
                {
                    // if we have a restriction of TYPE_ONLY_* we add restriction to
                    // any turn possibility (from, via, * ) except the given turn
                    while ( iter.next() )
                    {
                        if (iter.getEdge() != edgeIdFrom
                                && osmid(iter.getEdge(), osmidsOfEdges) != toOsm)
                        {
                            entries.add(new TurnCostsEntry()
                                    .flags(TurnCostEncoder.restriction()).node(via)
                                    .edgeFrom(edgeIdFrom)
                                    .edgeTo(iter.getEdge()));
                        }
                    }
                    ;
                }
            }
        } catch (Exception e)
        {
            logger.warn("Could not built node costs table for relation of node " + via + ".", e);
        }
        //TODO remove duplicate entries
        return entries;

    }

    private long osmid( int edgeId, DataAccess osmIds )
    {
        long ptr = (long) edgeId * 8;
        int left = osmIds.getInt(ptr);
        int right = osmIds.getInt(ptr + 4);
        return Helper.intToLong(left, right);
    }

    public final static OSMRestrictionRelation createRestriction( OSMRelation rel, LongIntMap osmNodeIDToIndexMap )
    {
        if ("restriction".equals(rel.getTag("type")))
        {
            OSMRestrictionRelation restriction = new OSMRestrictionRelation();
            restriction.restriction = getRestrictionType(rel.getTag("restriction"));
            for ( OSMRelation.Member member : rel.getMembers() )
            {
                if (OSMElement.WAY == member.type())
                {
                    if ("from".equals(member.role()))
                    {
                        restriction.fromOsm = member.ref();
                    } else if ("to".equals(member.role()))
                    {
                        restriction.toOsm = member.ref();
                    }
                } else if (OSMElement.NODE == member.type() && "via".equals(member.role()))
                {
                    int tmpNode = osmNodeIDToIndexMap.get(member.ref());
                    if (tmpNode < OSMReader.TOWER_NODE)
                    {
                        tmpNode = -tmpNode - 3;
                        restriction.via = tmpNode;
                    }
                }
            }
            return restriction;
        }
        return null;
    }

    public final static int getRestrictionType( String restrictionType )
    {
        if ("no_left_turn".equals(restrictionType))
        {
            return OSMRestrictionRelation.TYPE_NO_LEFT_TURN;
        } else if ("no_right_turn".equals(restrictionType))
        {
            return OSMRestrictionRelation.TYPE_NO_RIGHT_TURN;
        } else if ("no_straight_on".equals(restrictionType))
        {
            return OSMRestrictionRelation.TYPE_NO_STRAIGHT_ON;
        } else if ("no_u_turn".equals(restrictionType))
        {
            return OSMRestrictionRelation.TYPE_NO_U_TURN;
        } else if ("only_right_turn".equals(restrictionType))
        {
            return OSMRestrictionRelation.TYPE_ONLY_RIGHT_TURN;
        } else if ("only_left_turn".equals(restrictionType))
        {
            return OSMRestrictionRelation.TYPE_ONLY_LEFT_TURN;
        } else if ("only_straight_on".equals(restrictionType))
        {
            return OSMRestrictionRelation.TYPE_ONLY_STRAIGHT_ON;
        }
        return OSMRestrictionRelation.TYPE_UNSUPPORTED;
    }
}
