package com.graphhopper.routing.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphTurnCosts;

/**
 *
 * @author Karl Hübner
 */
public class DefaultTurnCostsCalcTest {

    private int edge_0_1;
    private int edge_1_2;
    private int edge_2_3;
    private int edge_3_0;
    private int edge_0_4;
    private int edge_3_4;
    private EncodingManager encodingManager = new EncodingManager("CAR,FOOT,BIKE");

    /*
     * 0---1
     * |\  |
     * | 4 |
     * |/  |
     * 3---2
     * 
     * from (0,1) via 1 to (1,2) -> restricted for cars, 300 costs for bikes
     * from (1,2) via 2 to (2,3) -> 100 costs for cars, restricted for bikes
     * from (2,3) via 3 to (3,0) -> restricted for cars, restricted for bikes
     * from (3,0) via 0 to (0,1) -> 100 costs for cars, 300 costs for bikes
     * from (3,4) via 4 to (4,0) -> no costs, no restriction
     * 
     */
    GraphTurnCosts createTestGraph() {
        GraphTurnCosts graph = new GraphBuilder(encodingManager).turnCostsGraphCreate();
        AbstractRoutingAlgorithmTester.initNodes(graph, 5);

        edge_0_1 = graph.edge(0, 1, 1000, true).getEdge();
        edge_1_2 = graph.edge(1, 2, 1000, true).getEdge();
        edge_2_3 = graph.edge(2, 3, 1000, true).getEdge();
        edge_3_0 = graph.edge(3, 0, 1000, true).getEdge();
        edge_0_4 = graph.edge(0, 4, 1000, true).getEdge();
        edge_3_4 = graph.edge(3, 4, 1000, true).getEdge();

        graph.turnCosts(1, edge_0_1, edge_1_2, TurnCostEncoder.costs(300, 0, TurnCostEncoder.RESTRICTION_CAR));
        graph.turnCosts(2, edge_1_2, edge_2_3, TurnCostEncoder.costs(0, 100, TurnCostEncoder.RESTRICTION_BIKE));
        graph.turnCosts(3, edge_2_3, edge_3_0, TurnCostEncoder.restriction());
        graph.turnCosts(0, edge_3_0, edge_0_1, TurnCostEncoder.costs(100, 300));
        graph.turnCosts(4, edge_3_4, edge_0_4, TurnCostEncoder.noCosts());

        return graph;
    }

    @Test
    public void turnCostCalc_fastestRoute_bikeEncoder() {
        TurnCostCalculation calc = new DefaultTurnCostsCalc(encodingManager.getEncoder(EncodingManager.BIKE), new FastestCalc(encodingManager.getEncoder(EncodingManager.BIKE)));
        calc.setGraph(createTestGraph());

        assertEquals(300, calc.getTurnCosts(1, edge_0_1, edge_1_2), 0);
        assertEquals(0, calc.getTurnCosts(1, edge_1_2, edge_0_1), 0);

        assertEquals(Double.MAX_VALUE, calc.getTurnCosts(2, edge_1_2, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(2, edge_2_3, edge_1_2), 0);

        assertEquals(Double.MAX_VALUE, calc.getTurnCosts(3, edge_2_3, edge_3_0), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_0, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_2_3, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_4, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_0, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_4, edge_3_0), 0);

        assertEquals(100, calc.getTurnCosts(0, edge_3_0, edge_0_1), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_1, edge_3_0), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_1, edge_0_4), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_4, edge_0_1), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_3_0, edge_0_4), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_4, edge_3_0), 0);

        assertEquals(0, calc.getTurnCosts(4, edge_0_4, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(4, edge_3_4, edge_0_4), 0);
    }

    @Test
    public void turnCostCalc_fastestRoute_carEncoder() {
        TurnCostCalculation calc = new DefaultTurnCostsCalc(encodingManager.getEncoder(EncodingManager.CAR), new FastestCalc(encodingManager.getEncoder(EncodingManager.CAR)));
        calc.setGraph(createTestGraph());

        assertEquals(Double.MAX_VALUE, calc.getTurnCosts(1, edge_0_1, edge_1_2), 0);
        assertEquals(0, calc.getTurnCosts(1, edge_1_2, edge_0_1), 0);

        assertEquals(100, calc.getTurnCosts(2, edge_1_2, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(2, edge_2_3, edge_1_2), 0);

        assertEquals(Double.MAX_VALUE, calc.getTurnCosts(3, edge_2_3, edge_3_0), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_0, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_2_3, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_4, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_0, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_4, edge_3_0), 0);

        assertEquals(300, calc.getTurnCosts(0, edge_3_0, edge_0_1), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_1, edge_3_0), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_1, edge_0_4), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_4, edge_0_1), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_3_0, edge_0_4), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_4, edge_3_0), 0);

        assertEquals(0, calc.getTurnCosts(4, edge_0_4, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(4, edge_3_4, edge_0_4), 0);
    }

    @Test
    public void turnCostCalc_fastestRoute_footEncoder() {
        TurnCostCalculation calc = new DefaultTurnCostsCalc(encodingManager.getEncoder(EncodingManager.FOOT), new FastestCalc(encodingManager.getEncoder(EncodingManager.FOOT)));
        calc.setGraph(createTestGraph());

        assertEquals(0, calc.getTurnCosts(1, edge_0_1, edge_1_2), 0);
        assertEquals(0, calc.getTurnCosts(1, edge_1_2, edge_0_1), 0);

        assertEquals(0, calc.getTurnCosts(2, edge_1_2, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(2, edge_2_3, edge_1_2), 0);

        assertEquals(0, calc.getTurnCosts(3, edge_2_3, edge_3_0), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_0, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_2_3, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_4, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_0, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_4, edge_3_0), 0);

        assertEquals(0, calc.getTurnCosts(0, edge_3_0, edge_0_1), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_1, edge_3_0), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_1, edge_0_4), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_4, edge_0_1), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_3_0, edge_0_4), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_4, edge_3_0), 0);

        assertEquals(0, calc.getTurnCosts(4, edge_0_4, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(4, edge_3_4, edge_0_4), 0);
    }

    @Test
    public void turnCostCalc_shortestRoute_carEncoder() {
        TurnCostCalculation calc = new DefaultTurnCostsCalc(encodingManager.getEncoder(EncodingManager.CAR), new ShortestCalc());
        calc.setGraph(createTestGraph());

        assertEquals(Double.MAX_VALUE, calc.getTurnCosts(1, edge_0_1, edge_1_2), 0);
        assertEquals(0, calc.getTurnCosts(1, edge_1_2, edge_0_1), 0);

        assertEquals(0, calc.getTurnCosts(2, edge_1_2, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(2, edge_2_3, edge_1_2), 0);

        assertEquals(Double.MAX_VALUE, calc.getTurnCosts(3, edge_2_3, edge_3_0), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_0, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_2_3, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_4, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_0, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_4, edge_3_0), 0);

        assertEquals(0, calc.getTurnCosts(0, edge_3_0, edge_0_1), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_1, edge_3_0), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_1, edge_0_4), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_4, edge_0_1), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_3_0, edge_0_4), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_4, edge_3_0), 0);

        assertEquals(0, calc.getTurnCosts(4, edge_0_4, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(4, edge_3_4, edge_0_4), 0);
    }

    @Test
    public void turnCostCalc_shortestRoute_bikeEncoder() {
        TurnCostCalculation calc = new DefaultTurnCostsCalc(encodingManager.getEncoder(EncodingManager.BIKE), new ShortestCalc());
        calc.setGraph(createTestGraph());

        assertEquals(0, calc.getTurnCosts(1, edge_0_1, edge_1_2), 0);
        assertEquals(0, calc.getTurnCosts(1, edge_1_2, edge_0_1), 0);

        assertEquals(Double.MAX_VALUE, calc.getTurnCosts(2, edge_1_2, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(2, edge_2_3, edge_1_2), 0);

        assertEquals(Double.MAX_VALUE, calc.getTurnCosts(3, edge_2_3, edge_3_0), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_0, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_2_3, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_4, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_0, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_4, edge_3_0), 0);

        assertEquals(0, calc.getTurnCosts(0, edge_3_0, edge_0_1), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_1, edge_3_0), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_1, edge_0_4), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_4, edge_0_1), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_3_0, edge_0_4), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_4, edge_3_0), 0);

        assertEquals(0, calc.getTurnCosts(4, edge_0_4, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(4, edge_3_4, edge_0_4), 0);
    }

    @Test
    public void turnCostCalc_shortestRoute_footEncoder() {
        TurnCostCalculation calc = new DefaultTurnCostsCalc(encodingManager.getEncoder(EncodingManager.FOOT), new ShortestCalc());
        calc.setGraph(createTestGraph());

        assertEquals(0, calc.getTurnCosts(1, edge_0_1, edge_1_2), 0);
        assertEquals(0, calc.getTurnCosts(1, edge_1_2, edge_0_1), 0);

        assertEquals(0, calc.getTurnCosts(2, edge_1_2, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(2, edge_2_3, edge_1_2), 0);

        assertEquals(0, calc.getTurnCosts(3, edge_2_3, edge_3_0), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_0, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_2_3, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_4, edge_2_3), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_0, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(3, edge_3_4, edge_3_0), 0);

        assertEquals(0, calc.getTurnCosts(0, edge_3_0, edge_0_1), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_1, edge_3_0), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_1, edge_0_4), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_4, edge_0_1), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_3_0, edge_0_4), 0);
        assertEquals(0, calc.getTurnCosts(0, edge_0_4, edge_3_0), 0);

        assertEquals(0, calc.getTurnCosts(4, edge_0_4, edge_3_4), 0);
        assertEquals(0, calc.getTurnCosts(4, edge_3_4, edge_0_4), 0);
    }
}
