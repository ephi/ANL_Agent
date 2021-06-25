package geniusweb.exampleparties.learningagent; // TODO: change name

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

/**
 * The class hold the negotiation data that is obtain during a negotiation
 * session. It will be saved to disk after the negotiation has finished. During
 * the learning phase, this negotiation data can be used to update the
 * persistent state of the agent. NOTE that Jackson can serialize many default
 * java classes, but not custom classes out-of-the-box.
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public class NegotiationData {
	private static final int tSplit = 40; // TODO: import from learning agent

    private Double maxReceivedUtil = 0.0;
    private Double agreementUtil = 0.0;
    private String opponentName;
    
    // Yair added
    private Double opponentUtil = 0.0;
    private Double[] opponentUtilByTime = new Double[tSplit];
//    private List<Double> opponentOfferTimes = new ArrayList<Double>();

    public void addAgreementUtil(Double agreementUtil) {
        this.agreementUtil = agreementUtil;
        if (agreementUtil > maxReceivedUtil)
            this.maxReceivedUtil = agreementUtil;
    }

    public void addBidUtil(Double bidUtil) {
        if (bidUtil > maxReceivedUtil)
            this.maxReceivedUtil = bidUtil;
    }
    
    public void updateOpponentOffers(double[] opSum, int[] opCounts) {
    	for (int i=0; i < tSplit; i++) {
			this.opponentUtilByTime[i] = (opCounts[i] > 0) ? opSum[i]/opCounts[i] : 0.0;
    	}
    }

    public void setOpponentName(String opponentName) {
        this.opponentName = opponentName;
    }

    public void setOpponentUtil(Double oppUtil) {
    	this.opponentUtil = oppUtil;
    }
    
    public String getOpponentName() {
        return this.opponentName;
    }

    public Double getMaxReceivedUtil() {
        return this.maxReceivedUtil;
    }

    public Double getAgreementUtil() {
        return this.agreementUtil;
    }
    
    public Double getOpponentUtil() {
    	return this.opponentUtil;
    }
    
    public Double[] getOpponentUtilByTime() {
    	return this.opponentUtilByTime;
    }
//    
//    public List<Double> getOpponentOfferTimes() {
//    	return this.opponentOfferTimes;
//    }
    
}
