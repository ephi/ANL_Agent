package geniusweb.exampleparties.learningagent; // TODO: change name

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
//import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

//import org.apache.commons.math3.fitting.PolynomialCurveFitter;
//import org.apache.commons.math3.fitting.WeightedObservedPoints;


/**
 * This class can hold the persistent state of your agent. You can off course
 * also write something else to the file path that is provided to your agent,
 * but this provides an easy usable method. This object is serialized using
 * Jackson. NOTE that Jackson can serialize many default java classes, but not
 * custom classes out-of-the-box.
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public class PersistentState {
	private static final int tSplit = 40;
	private static final double tPhase = 0.2;
	private static final Double newWeight = 0.2; 
//	private static final int polynomRank = 4;
	private static final int smoothWidth = 3; // from each side of the element
	private static final double opponentDecrease = 0.65;
	private static final double defualtAlpha = 10.7;

//	their data structures
    private Double avgUtility = 0.0;
    private Integer negotiations = 0;
    private Map<String, Double> avgMaxUtilityOpponent = new HashMap<String, Double>();
    private Map<String, Integer> opponentEncounters = new HashMap<String, Integer>();

//  our new data structures
    private Double stdUtility = 0.0;
    private List<Double> negoResults = new ArrayList<Double>();
    private Map<String, Double> avgOpponentUtility = new HashMap<String, Double>();
    private Map<String, Double> opponentAlpha = new HashMap<String, Double>();
    
    private Map<String, Double[]> opponentUtilByTime = new HashMap<String, Double[]>();
//    private Map<String, double[]> thresholdFunction = new HashMap<String, double[]>();
    
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
            
            // update opponent utility over time
            Double[] opponentTimeUtil;
            if (opponentUtilByTime.containsKey(opponent))
            	opponentTimeUtil = opponentUtilByTime.get(opponent);
        	else {
        		opponentTimeUtil = new Double[tSplit];
        		for (int i=0; i<tSplit; i++) opponentTimeUtil[i] = 0.0;
        	}
            
            // update values in the array
            Double[] newUtilData = negotiationData.getOpponentUtilByTime();
        	System.out.println(opponent + ":");
            for (int i=0; i < tSplit; i++) {
        		opponentTimeUtil[i] = ((1-newWeight)*opponentTimeUtil[i] + newWeight*newUtilData[i]);            		
            }
            opponentUtilByTime.put(opponent, opponentTimeUtil);
            opponentAlpha.put(opponent, calcAlpha(opponent));
            
            // NOT IN USE because doesn't work
//             thresholdFuncrtion.put(opponent, updateOpponentThreshold(opponentTimeUtil));

        }
    }

    public Double calcAlpha(String opponent) {
    	// smoothing with smooth width of smoothWidth
    	Double[] alphaArray = getSmoothThresholdOverTime(opponent);
    	if (alphaArray == null) {
    		return defualtAlpha;
    	}
    	
    	// find the last index with data in alphaArray
    	int maxIndex, t;
    	for (maxIndex=0; maxIndex<tSplit && alphaArray[maxIndex]>0.2; maxIndex++);
    	
    	// find t, time that threshold decrease by 50%
    	double maxValue = alphaArray[0];
    	double minValue = alphaArray[Math.max(maxIndex - smoothWidth - 1, 0)];
    	
    	// if there is no clear trend-line, return default value
    	if (maxValue - minValue < 0.1)
    		return defualtAlpha;
    	
    	for (t=0; t<maxIndex && alphaArray[t]>(maxValue - opponentDecrease*(maxValue-minValue)); t++);
    	
    	double[] calibratedPolynom = {572.83,-1186.7, 899.29, -284.68, 32.911};
    	Double alpha = calibratedPolynom[0];
    	
    	double tTime = tPhase + (1-tPhase)*(maxIndex*((double)t/tSplit) + (tSplit-maxIndex)*0.85)/tSplit;
    	for (int i=1; i<calibratedPolynom.length; i++)
    		alpha = alpha*tTime + calibratedPolynom[i];
    	
    	System.out.print("alpha: " + alpha);
    	return alpha;	
    }
    
    public Double[] getSmoothThresholdOverTime(String opponent) {
    	// case of known opponent
    	if (!this.knownOpponent(opponent))
    		return null;
    	
    	// smoothing with smooth width of smoothWidth
    	Double[] opponentTimeUtil = opponentUtilByTime.get(opponent);
    	Double[] smoothedTimeUtil = new Double[tSplit];
    	for (int i=0; i<tSplit; i++) smoothedTimeUtil[i] = 0.0;
    	
    	for (int i=0; i<tSplit; i++) {
    		for (int j=Math.max(i-smoothWidth, 0); j<Math.min(i+smoothWidth+1, tSplit); j++)
    			smoothedTimeUtil[i] += opponentTimeUtil[j];
    		smoothedTimeUtil[i] /= (Math.min(i+smoothWidth+1, tSplit) - Math.max(i-smoothWidth, 0));
//    		System.out.println(i*(1-tPhase)/(tSplit-1)+tPhase + ", " + alphaArray[i]);
    	}
    	return smoothedTimeUtil;
    }
    
    public double getAvgUtility() {
    	return this.avgUtility;
    }
    
    public double getStdUtility() {
    	return this.stdUtility;
    }
    
    public Double getOpponentAlpha(String opponent) {
    	return this.knownOpponent(opponent) ? opponentAlpha.get(opponent) : 0.0;
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
   
//    public double[] updateOpponentThreshold(Double[] opponentTimeUtil) {
//    	final WeightedObservedPoints utilList = new WeightedObservedPoints();
//    	for (int i=0; i < tSplit; i++) {
//    		if ((opponentTimeUtil[i] != null) && (opponentTimeUtil[i] > 0)) {
//    			System.out.println(i*(1-tPhase)/(tSplit-1)+tPhase + ", " + opponentTimeUtil[i]);
//    			utilList.add(i*(1-tPhase)/(tSplit-1)+tPhase, opponentTimeUtil[i]);
//    		}
//    	}
//    	
//    	if (utilList.toList().size() > 5) {
//	    	final PolynomialCurveFitter fitter = PolynomialCurveFitter.create(polynomRank);
//	    	final double[] coeff = fitter.fit(utilList.toList()); 
//	    	System.out.println(Arrays.toString(coeff));
//	    	return coeff;
//    	}
//    	return null;
//    }

}

