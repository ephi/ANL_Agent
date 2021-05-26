package geniusweb.exampleparties.learningagent; // TODO: change name

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

import geniusweb.actions.FileLocation;

import java.util.UUID;
import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.LearningDone;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllBidsList;
import geniusweb.inform.ActionDone;
import geniusweb.inform.Agreements;
import geniusweb.inform.Finished;
import geniusweb.inform.Inform;
import geniusweb.inform.Settings;
import geniusweb.inform.YourTurn;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.DiscreteValue;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.NumberValue;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.UtilitySpace;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import geniusweb.references.Parameters;
import tudelft.utilities.logging.Reporter;

import com.fasterxml.jackson.databind.ObjectMapper;

public class LearningAgent extends DefaultParty { // TODO: change name

    private Bid lastReceivedBid = null;
    private PartyId me;
    private final Random random = new Random();
    protected ProfileInterface profileint = null;
    private Progress progress;
    private String protocol;
    private Parameters parameters;
    private UtilitySpace utilitySpace;
    private Domain domain;
    private PersistentState persistentState;
    private NegotiationData negotiationData;
    private List<File> dataPaths;
    private File persistentPath;
    private String opponentName;
    // Expecting Lower Limit of Concession Function behavior
    // The idea here that we will keep for a negotiation scenario the most frequent 
    // Issues-Values, afterwards, as a counter offer bid for each issue we will select the most frequent value.
    private HashMap<IssueValKey,Integer> issueValFreqs;
    
    //Best bid for agent, exists if bid space is small enough to search in
    private final BigInteger MAX_SEARCHABLE_BIDSPACE = BigInteger.valueOf(50000);
    private Bid optimalBid = null;
    private AllBidsList allBidList;

    public LearningAgent() { // TODO: change name
    }

    public LearningAgent(Reporter reporter) { // TODO: change name
        super(reporter); // for debugging
    }

    /**
     * This method mostly contains utility functionallity for the agent to function
     * properly. The code that is of most interest for the ANL competition is
     * further below and in the other java files in this directory. It does,
     * however, not hurt to read through this code to have a better understanding of
     * what is going on.
     * 
     * @param info information object for agent
     */
    @Override
    public void notifyChange(Inform info) {
        try {
            if (info instanceof Settings) {
                // info is a Settings object that is passed at the start of a negotiation
                Settings settings = (Settings) info;

                // ID of my agent
                this.me = settings.getID();

                // The progress object keeps track of the deadline
                this.progress = settings.getProgress();

                // Protocol that is initiate for the agent
                this.protocol = settings.getProtocol().getURI().getPath();

                // Parameters for the agent (can be passed through the GeniusWeb GUI, or a
                // JSON-file)
                this.parameters = settings.getParameters();
                

                // The PersistentState is loaded here (see 'PersistenData,java')
                if (this.parameters.containsKey("persistentstate"))
                    this.persistentPath = new FileLocation(
                            UUID.fromString((String) this.parameters.get("persistentstate"))).getFile();
                if (this.persistentPath != null && this.persistentPath.exists()) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    this.persistentState = objectMapper.readValue(this.persistentPath, PersistentState.class);
                } else {
                    this.persistentState = new PersistentState();
                }

                // The negotiation data paths are converted here from List<String> to List<File>
                // for improved usage. For safety reasons, this is more comprehensive than
                // normally.
                if (this.parameters.containsKey("negotiationdata")) {
                    List<String> dataPaths_raw = (List<String>) this.parameters.get("negotiationdata");
                    this.dataPaths = new ArrayList<>();
                    for (String path : dataPaths_raw)
                        this.dataPaths.add(new FileLocation(UUID.fromString(path)).getFile());
                }
                if ("Learn".equals(protocol)) {
                    // We are in the learning step: We execute the learning and notify when we are
                    // done. REMEMBER that there is a deadline of 60 seconds for this step.
                    learn();
                    getConnection().send(new LearningDone(me));
                } else {
                    // We are in the negotiation step.

                    // Create a new NegotiationData object to store information on this negotiation.
                    // See 'NegotiationData.java'.
                    this.negotiationData = new NegotiationData();

                    // Obtain our utility space, i.e. the problem we are negotiating and our
                    // preferences over it.
                    try {
                        this.profileint = ProfileConnectionFactory.create(settings.getProfile().getURI(),
                                getReporter());
                        domain = this.profileint.getProfile().getDomain();
                        // Create a Issues-Values frequency map
                        if(issueValFreqs == null) {
                        	// Map wasn't created before, create a new instance now
                        	issueValFreqs = new HashMap<IssueValKey,Integer>();
                        } 
                        else
                        	// Map was created before, but this is a new negotiation scenario, clear the old map.
                        	issueValFreqs.clear();
                        
                        // Obtain all of the issues in the current negotiation domain
                        Set<String> issues = domain.getIssues();
                        for (String s : issues) {
                        	  // Obtain all of the values for an issue "s"
                        	  ValueSet vs = domain.getValues(s);
                        	  for(Value v : vs) {
                        		  // Add a new entry in the frequency map for each (s,v,typeof(v)) 
                        		  // where typeof(v) is the value type of v (either DiscreteValue or Numeric) 
                        		  IssueValKey ivk = new IssueValKey();
                        		  ivk.issue = s;
                        		  if(v instanceof DiscreteValue) {
                        			  ivk.value_type = 0;
                        			  ivk.value = ((DiscreteValue)v).getValue();
                        		  } else if (v instanceof NumberValue) {
                        			  ivk.value_type = 1;
                        			  ivk.value = ((NumberValue)v).getValue().toPlainString();
                        		  }
                        		  issueValFreqs.put(ivk, 0);
                        	  }
                        	
                        }  
                        this.utilitySpace = ((UtilitySpace) profileint.getProfile());
                        allBidList = new AllBidsList(domain);
                        
                        // Attempt to find the optimal bid in a search-able bid space, if bid space size is small/equal to MAX_SEARCHABLE_BIDSPACE
                        int r = allBidList.size().compareTo(MAX_SEARCHABLE_BIDSPACE);
                        if(r == -1 || r == 0) {
                        	BigDecimal mx_util = new BigDecimal(0);
                        	int bidspace_size = allBidList.size().intValue();
                        	System.out.println("Searching for optimal bid in " + bidspace_size + " possible bids");
                        	for(int i = 0; i < bidspace_size;++i) {
                        		Bid b = allBidList.get(i);
                        		BigDecimal canidate = utilitySpace.getUtility(b);
                        		r = canidate.compareTo(mx_util);
                        		if(r == 1) {
                        			mx_util = canidate;
                        			optimalBid = b;
                        		}
                        	}
                        	System.out.println("agent has optimal bid with utility of " + mx_util.doubleValue());
                        } else {
                        	System.out.println("Searching for best bid in random subspace of possible bids, result might be sub-optimal");
                        	BigDecimal mx_util = new BigDecimal(0);
                        	// Iterate randomly through list of bids until we find a good bid
        					for (int attempt = 0; attempt < MAX_SEARCHABLE_BIDSPACE.intValue(); attempt++) {
        						long i = random.nextInt(allBidList.size().intValue());
        						Bid b = allBidList.get(BigInteger.valueOf(i));
        						BigDecimal canidate = utilitySpace.getUtility(b);
                        		r = canidate.compareTo(mx_util);
                        		if(r == 1) {
                        			mx_util = canidate;
                        			optimalBid = b;
                        		}
        					}
                        	System.out.println("agent has best (perhaps sub optimal) bid with utility of " + mx_util.doubleValue());
                        }
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            } else if (info instanceof ActionDone) {
                // The info object is an action that is performed by an agent.
                Action action = ((ActionDone) info).getAction();

                // Check if this is not our own action
                if (!this.me.equals(action.getActor())) {
                    // Check if we already know who we are playing against.
                    if (this.opponentName == null) {
                        // The part behind the last _ is always changing, so we must cut it off.
                        String fullOpponentName = action.getActor().getName();
                        int index = fullOpponentName.lastIndexOf("_");
                        this.opponentName = fullOpponentName.substring(0, index);

                        // Add name of the opponent to the negotiation data
                        this.negotiationData.setOpponentName(this.opponentName);
                    }
                    // Process the action of the opponent.
                    processAction(action);
                }
            } else if (info instanceof YourTurn) {
                // Advance the round number if a round-based deadline is set.
                if (progress instanceof ProgressRounds) {
                    progress = ((ProgressRounds) progress).advance();
                }

                // The info notifies us that it is our turn
                myTurn();
            } else if (info instanceof Finished) {
                // The info is a notification that th negotiation has ended. This Finished
                // object also contains the final agreement (if any).
                Agreements agreements = ((Finished) info).getAgreement();
                processAgreements(agreements);

                // Write the negotiation data that we collected to the path provided.
                if (this.dataPaths != null && this.negotiationData != null) {
                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.dataPaths.get(0),
                                this.negotiationData);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write negotiation data to disk", e);
                    }
                }

                // Log the final outcome and terminate
                getReporter().log(Level.INFO, "Final outcome:" + info);
                terminate();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle info", e);
        }
    }

    /** Let GeniusWeb know what protocols that agent is capable of handling */
    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(new HashSet<>(Arrays.asList("SAOP", "Learn")), Collections.singleton(Profile.class));
    }

    /** Terminate agent */
    @Override
    public void terminate() {
        super.terminate();
        if (this.profileint != null) {
            this.profileint.close();
            this.profileint = null;
        }
    }

    /*
     * *****************************NOTE:************************************
     * Everything below this comment is most relevant for the ANL competition.
     * **********************************************************************
     */
    
    private boolean isNearNegotiationEnd() {
    	 Boolean nearDeadline = progress.get(System.currentTimeMillis()) > 0.95;
    	 return nearDeadline;
    }
    /**
     *  Method: createBid
     *  Description: This function creates a bid based on a given issue values frequency map
     *  Input: p_issueValFreqs - a copy of the issue values frequency map
     *  Output: a new bid, such that for each issue s from that domain the matching value v is the most frequent in the negotiation scenario
     * */
	private Bid createBid(HashMap<IssueValKey, Integer> issueValFreqs_cpy) {
		// A new issuemap to create the Bid by
		Map<String, Value> issuemap = new HashMap<String, Value>();
		
		Set<String> issues = domain.getIssues();
		for (String s : issues) {
			// For issue "s" in the domain:
			
			// Create a list such that each element contains for an issue: value, value type and frequency of that value
			// In the current negotiation scenario
			List<IssueValueFreqElement> ls = IssueValKey.createValueFreqList(issueValFreqs_cpy, s);
			
			if (ls.size() > 0) {
				// If such a list contains values, then, we will search the value of which the frequency is maximal
				
				int max_freq = -1;
				int max_value_type = -1;
				String max_freq_value = "";
				
				// Searching for a maximal frequency value for issue "s"
				for (IssueValueFreqElement ele : ls) {
					Integer freq = ele.freq;
					if (freq > max_freq) {
						max_freq_value = ele.value;
						max_value_type = ele.value_type;
						max_freq = freq;

					}
				}
				Value v;
				if (max_value_type == 0) {
					v = new DiscreteValue(max_freq_value);
				} else if (max_value_type == 1) {
					v = new NumberValue(max_freq_value);
				} else
					throw new RuntimeException("Unknown Value type detected");
				// Remove the selected issue and value from the current copy of the frequency map
				IssueValKey ivk = new IssueValKey();
				ivk.issue = s;
				ivk.value = max_freq_value;
				issueValFreqs_cpy.remove(ivk);
				
				// Add to the issue map, the issue s, with the found maximal frequency v 
				issuemap.put(s, v);
			}

		}
		return new Bid(issuemap);
	}

    /** Provide a description of the agent */
    @Override
    public String getDescription() {
        return "This is the example party of ANL 2021. It can handle the Learn protocol and learns simple characteristics of the opponent.";
    }

    /**
     * Processes an Action performed by the opponent.
     * 
     * @param action
     */
    private void processAction(Action action) {
        if (action instanceof Offer) {
        	//If the action was an offer: Obtain the bid 
            this.lastReceivedBid = ((Offer) action).getBid();
            
            //Update the IssueValue-Frequency Map
            Map<String,Value> m = this.lastReceivedBid.getIssueValues();
            for (Map.Entry<String,Value> entry : m.entrySet()) {
            	  IssueValKey ivk = new IssueValKey();
            	  ivk.issue = entry.getKey();
            	  Value v = entry.getValue();
            	  if(v instanceof DiscreteValue) {
        			  ivk.value_type = 0;
        			  ivk.value = ((DiscreteValue)v).getValue();
        		  } else if (v instanceof NumberValue) {
        			  ivk.value_type = 1;
        			  ivk.value = ((NumberValue)v).getValue().toPlainString();
        		  }
            	  Integer freq = issueValFreqs.get(ivk);
            	  ++freq;
            	  issueValFreqs.put(ivk, freq);
            	  //System.out.println("(" + ivk.issue + "," + ivk.value + ") = " + freq);
            }
            //add it's value to our negotiation data.
            double utilVal = this.utilitySpace.getUtility(this.lastReceivedBid).doubleValue();
            this.negotiationData.addBidUtil(utilVal);
        }
    }

    /**
     * This method is called when the negotiation has finished. It can process the
     * final agreement.
     * 
     * @param agreements
     */
    private void processAgreements(Agreements agreements) {
        // Check if we reached an agreement (walking away or passing the deadline
        // results in no agreement)
        if (!agreements.getMap().isEmpty()) {
            // Get the bid that is agreed upon and add it's value to our negotiation data
            Bid agreement = agreements.getMap().values().iterator().next();
            this.negotiationData.addAgreementUtil(this.utilitySpace.getUtility(agreement).doubleValue());
        }
       
    }

    /**
     * send our next offer
     */
    private void myTurn() throws IOException {
        Action action;
        if (isGood(lastReceivedBid)) {
            // If the last received bid is good: create Accept action
            action = new Accept(me, lastReceivedBid);
        } else {
			Bid bid = null;
			if (!isNearNegotiationEnd()) {
				bid = optimalBid;
			}
			// Find a "good" counter bid
			if (bid == null) {
				int attempt = 0;
				// Create a copy of the current issue values frequency map
				HashMap<IssueValKey, Integer> issueValFreqs_cpy = IssueValKey.cloneIssueValMap(issueValFreqs);

				// Try to find a "good" bid, until either the copy of the issue values frequency
				// map is empty
				// Or enough attempts have failed.
				while (attempt < 500 && issueValFreqs_cpy.size() > 0) {

					bid = createBid(issueValFreqs_cpy);
					if (isGood(bid))
						break;
					else
						bid = null;
					attempt++;
				}

				if (bid == null) {
					// Iterate randomly through list of bids until we find a good bid
					for (attempt = 0; attempt < 5000 && !isGood(bid); attempt++) {
						long i = random.nextInt(allBidList.size().intValue());
						bid = allBidList.get(BigInteger.valueOf(i));
						// System.out.println("My utility for oppenent prefered bid is: " +
						// this.utilitySpace.getUtility(bid).doubleValue());
					}
				}
			}

			// Create offer action
			action = new Offer(me, bid);
        }

        // Send action
        getConnection().send(action);
    }

    /**
     * The method checks if a bid is good.
     * 
     * @param bid the bid to check
     * @return true iff bid is good for us.
     */
    private boolean isGood(Bid bid) {
        if (bid == null)
            return false;
        if(!isNearNegotiationEnd()) {
        	if(optimalBid != null) {
        		return this.utilitySpace.getUtility(bid).doubleValue() >= this.utilitySpace.getUtility(optimalBid).doubleValue();
        	}
        }
        // Check if we already know the opponent
       /* if (this.persistentState.knownOpponent(this.opponentName)) {
            // Obtain the average of the max utility that the opponent has offered us in
            // previous negotiations.
            Double avgMaxUtility = this.persistentState.getAvgMaxUtility(this.opponentName);

            // Request 5% more than the average max utility offered by the opponent.
            return this.utilitySpace.getUtility(bid).doubleValue() > (avgMaxUtility * 1.05);
        }*/

        // Check a simple business rule
        Boolean nearDeadline = isNearNegotiationEnd();
        Boolean acceptable = this.utilitySpace.getUtility(bid).doubleValue() > 0.9;
        Boolean good = this.utilitySpace.getUtility(bid).doubleValue() > 0.92;
        return (nearDeadline && acceptable) || good;
    }

    /**
     * This method is invoked if the learning phase is started. There is now time to
     * process previously stored data and use it to update our persistent state.
     * This persistent state is passed to the agent again in future negotiation
     * session. REMEMBER that there is a deadline of 60 seconds for this step.
     */
    private void learn() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Iterate through the negotiation data file paths
        for (File dataPath : this.dataPaths)
            try {
                // Load the negotiation data object of a previous negotiation
                NegotiationData negotiationData = objectMapper.readValue(dataPath, NegotiationData.class);

                // Process the negotiation data in our persistent state
                this.persistentState.update(negotiationData);
            } catch (IOException e) {
                throw new RuntimeException("Negotiation data provided to learning step does not exist", e);
            }

        // Write the persistent state object to file
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.persistentPath, this.persistentState);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write persistent state to disk", e);
        }
    }
}