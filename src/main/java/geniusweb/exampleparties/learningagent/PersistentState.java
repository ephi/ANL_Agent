package geniusweb.exampleparties.learningagent; // TODO: change name

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

/**
 * This class can hold the persistent state of your agent. You can off course
 * also write something else to the file path that is provided to your agent,
 * but this provides an easy usable method. This object is serialized using
 * Jackson. NOTE that Jackson can serialize many default java classes, but not
 * custom classes out-of-the-box.
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public class PersistentState {

//	their data structures
    private Double avgUtility = 0.0;
    private Integer negotiations = 0;
    private Map<String, Double> avgMaxUtilityOpponent = new HashMap<String, Double>();
    private Map<String, Integer> opponentEncounters = new HashMap<String, Integer>();

//  our new data structures
    private Double stdUtility = 0.0;
    private List<Double> negoResults = new ArrayList<Double>();
    private Map<String, Double> avgOpponentUtility = new HashMap<String, Double>();
//    private Map<String, List<Integer>> opponentNegos = new HashMap<String, List<Integer>>(); // NOT IN USE FOR NOW
    
    /**
     * Update the persistent state with a negotiation data of a previous negotiation
     * session
     * 
     * @param negotiationData NegotiationData class holding the negotiation data
     *                        that is obtain during a negotiation session.
     */
    public void update(NegotiationData negotiationData) {
        // Keep track of the average utility that we obtained
    	this.avgUtility = (this.avgUtility * negotiations + negotiationData.getAgreementUtil())
                / (negotiations + 1);
        
        // Keep track of the number of negotiations that we performed
        negotiations++;
        
        // add utility to UtiList calculate std deviation of results
        this.negoResults.add(negotiationData.getAgreementUtil());
        this.stdUtility = 0.0;
        for (double util : this.negoResults)
        	this.stdUtility += Math.pow(util - this.avgUtility, 2);
        this.stdUtility = Math.sqrt(this.stdUtility / this.negotiations);

        // Get the name of the opponent that we negotiated against
        String opponent = negotiationData.getOpponentName();

        // Check for safety
        if (opponent != null) {
            // Update the number of encounters with an opponent
            Integer encounters = opponentEncounters.containsKey(opponent) ? opponentEncounters.get(opponent) : 0;
            opponentEncounters.put(opponent, encounters + 1);
            // Track the average value of the maximum that an opponent has offered us across
            // multiple negotiation sessions
            Double avgUtil = avgMaxUtilityOpponent.containsKey(opponent) ? avgMaxUtilityOpponent.get(opponent) : 0.0;
            avgMaxUtilityOpponent.put(opponent,
                    (avgUtil * encounters + negotiationData.getMaxReceivedUtil()) / (encounters + 1));
            
            Double avgOpUtil = avgOpponentUtility.containsKey(opponent) ? avgOpponentUtility.get(opponent) : 0.0;
            avgOpponentUtility.put(opponent,
            		(avgOpUtil * encounters + negotiationData.getOpponentUtil()) / (encounters + 1));
        }
    }

    public double getAvgUtility() {
    	return this.avgUtility;
    }
    
    public double getStdUtility() {
    	return this.stdUtility;
    }
    
    public double getOpUtility(String opponent) {
    	return this.knownOpponent(opponent) ? this.avgOpponentUtility.get(opponent) : 0.0;
    }
    
    public Double getAvgMaxUtility(String opponent) {
        if (avgMaxUtilityOpponent.containsKey(opponent)) {
            return avgMaxUtilityOpponent.get(opponent);
        }
        return null;
    }

    public Integer getOpponentEncounters(String opponent) {
        if (opponentEncounters.containsKey(opponent)) {
            return opponentEncounters.get(opponent);
        }
        return null;
    }

    public Boolean knownOpponent(String opponent) {
        return opponentEncounters.containsKey(opponent);
    }
}
