package com.graphhopper.routing.edgebased;

import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.TurnCostCalculation;
import com.graphhopper.util.EdgeIteratorState;

public interface EdgeBasedRoutingAlgorithm
{
    public RoutingAlgorithm turnCosts(TurnCostCalculation calc);
    
    int createIterKey(EdgeIteratorState iter, boolean backwards);
}
