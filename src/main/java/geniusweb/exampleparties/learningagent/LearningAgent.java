package geniusweb.exampleparties.learningagent; // TODO: change name

// TODO: change IssueValueFreqElement to String (why not?)

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

import javax.swing.plaf.basic.BasicSplitPaneUI.BasicVerticalLayoutManager;

import geniusweb.actions.FileLocation;

import java.util.UUID;
import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.LearningDone;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllBidsList;
import geniusweb.exampleparties.learningagent.NegotiationData;
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
	private HashMap<String, Pair> freqMap;
	
	// Average and standard deviation of the competition for determine "good" utility threshold
	private double avgUtil = 0.95;
	private double stdUtil = 0.1;
	private double opAvgUtil = 0.0;
	private double utilThreshold = 0.95;
	
	// estimate opponent time-variant threshold function
	private static final int tSplit = 40;
	private int[] opCounter = new int[tSplit];
	private double[] opSum = new double[tSplit];
	
	// agent has 2-phases - learning of the opponent and offering bids while considering opponent utility, this constant define the threshold between those two phases
	private static final double tPhase = 0.33;
	
	
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
					this.avgUtil = this.persistentState.getAvgUtility();
					this.stdUtil = this.persistentState.getStdUtility();					
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
						if(freqMap == null) {
							// Map wasn't created before, create a new instance now
							freqMap = new HashMap<String, Pair>();
						} 
						else // Map was created before, but this is a new negotiation scenario, clear the old map.
							freqMap.clear();

						// Obtain all of the issues in the current negotiation domain
						Set<String> issues = domain.getIssues();
						for (String s : issues) {
							// create new list of all the values for 
							Pair p = new Pair();
							p.vList = new HashMap<String, Integer>();
							
							// gather type of issue based on the first element 
							ValueSet vs = domain.getValues(s);
							if (vs.get(0) instanceof DiscreteValue)
								p.type = 0;
							else if (vs.get(0) instanceof NumberValue)
								p.type = 1;
							
							// Obtain all of the values for an issue "s"
							for(Value v : vs) {
								// Add a new entry in the frequency map for each (s,v,typeof(v)) 
								String vStr = valueToStr(v, p);
								p.vList.put(vStr, 0);
							}
							freqMap.put(s, p);

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
				if (this.me != null && !this.me.equals(action.getActor())) {
					// Check if we already know who we are playing against.
					if (this.opponentName == null) {
						// The part behind the last _ is always changing, so we must cut it off.
						String fullOpponentName = action.getActor().getName();
						int index = fullOpponentName.lastIndexOf("_");
						this.opponentName = fullOpponentName.substring(0, index);

						// Add name of the opponent to the negotiation data
						this.negotiationData.setOpponentName(this.opponentName);
						this.opAvgUtil = this.persistentState.getOpUtility(this.opponentName);
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
			System.out.println(e);
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

	private int isNearNegotiationEnd() {
		return progress.get(System.currentTimeMillis()) < tPhase ? 0 : 1; 
		
		
//		if () return 0; 		// first phase (00-20 seconds)
//		else if (progress.get(System.currentTimeMillis()) < 0.95) return 1; // second phase (20-59 seconds)
//		return 2; 															// third phase (59-60 seconds)
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
			updateFreqMap(lastReceivedBid);
			
			//add it's value to our negotiation data.
			double utilVal = this.utilitySpace.getUtility(this.lastReceivedBid).doubleValue();
			this.negotiationData.addBidUtil(utilVal);
			
//			if (this.counter > 500) {
//				System.out.print(false);
			
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
			this.negotiationData.setOpponentUtil(this.calcOpValue(agreement));
			this.negotiationData.updateOpponentOffers(this.opSum, this.opCounter);
			
			
			System.out.println("MY OWN THRESHOLD: " + this.utilThreshold);
			System.out.println("MY OWN UTIL: " + this.utilitySpace.getUtility(agreement).doubleValue());
			System.out.println("EXP OPPONENT UTIL: " + this.calcOpValue(agreement));

		}
		else
			System.out.println("!!!!!!!!!!!!!! NO AGREEMENT !!!!!!!!!!!!!!! /// MY THRESHOLD: " + this.utilThreshold);

	}

	// send our next offer
	private void myTurn() throws IOException {
		Action action;
		
		// save average of the last avgSplit offers (only when frequency table is stabilized)
		if (isNearNegotiationEnd() > 0) {
			int index = (int) ((tSplit - 1)/(1-tPhase) * (progress.get(System.currentTimeMillis()) - tPhase));  
			this.opSum[index] += this.calcOpValue(lastReceivedBid);
			this.opCounter[index]++;
//			this.opSum += 
//			if (++this.opCounter > avgSplit) {
//				this.negotiationData.addOpponentOffer(,  this.opSum/ avgSplit);
//				System.out.println(this.opSum/avgSplit);
//				
//				this.opCounter = 0;
//				this.opSum = 0;
//			}
		}

		// evaluate the offer and accept or give counter-offer
		if (isGood(lastReceivedBid)) {
			// If the last received bid is good: create Accept action
			action = new Accept(me, lastReceivedBid);
		} else {
			// there are 3 phases in the negotiation process:
			// 1. Send random bids that considered to be GOOD for our agent
			// 2. Send random bids that considered to be GOOD for both of the agents
			// 3. Near negotiation end - accept lower offers (degraded threshold) // TODO: this phase has been removed
			Bid bid = null;
			
			switch (isNearNegotiationEnd()) {
			case 0:
				for (int attempt = 0; attempt < 1000 && !isGood(bid); attempt++) {
					long i = random.nextInt(allBidList.size().intValue());
					bid = allBidList.get(BigInteger.valueOf(i));
					// System.out.println("My utility for opponent preferred bid is: " +
					// this.utilitySpace.getUtility(bid).doubleValue());
				}
//				System.out.println("My utility from bid: " + this.utilitySpace.getUtility(bid).doubleValue() + " Thershold: " + this.utilThreshold);
				bid = (isGood(bid)) ? bid : optimalBid;  // if the last bid isn't good, offer (default) the optimal bid
				break;
			
			case 1:	
			case 2:
				for (int attempt = 0; attempt < 1000 && !isGood(bid) && !isOpGood(bid); attempt++) {
					long i = random.nextInt(allBidList.size().intValue());
					bid = allBidList.get(BigInteger.valueOf(i));
					// System.out.println("My utility for opponent preferred bid is: " +
					// this.utilitySpace.getUtility(bid).doubleValue());
				}
//				System.out.println("Utility bid: " + this.utilitySpace.getUtility(bid).doubleValue() + " Thershold: " + this.utilThreshold);
				bid = (isGood(bid) && isOpGood(bid)) ? bid : optimalBid;  // if the last bid isn't good, offer (default) the optimal bid
				break;
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
//		if(isNearNegotiationEnd() < 3) {
		double maxVlue = (optimalBid != null) ? this.utilitySpace.getUtility(optimalBid).doubleValue() : 1.0;
		double avgMaxUtility = this.persistentState.knownOpponent(this.opponentName) ? this.persistentState.getAvgMaxUtility(this.opponentName) : this.avgUtil; 

		this.utilThreshold = maxVlue - (0.6*maxVlue + 0.4*avgMaxUtility - this.avgUtil + 1*this.stdUtil)*(Math.exp(progress.get(System.currentTimeMillis())-1))/(Math.E - 1);
		return this.utilitySpace.getUtility(bid).doubleValue() >= this.utilThreshold;
//		}
		// Check if we already know the opponent
		/* if (this.persistentState.knownOpponent(this.opponentName)) {
            // Obtain the average of the max utility that the opponent has offered us in
            // previous negotiations.
            Double avgMaxUtility = this.persistentState.getAvgMaxUtility(this.opponentName);

            // Request 5% more than the average max utility offered by the opponent.
            return this.utilitySpace.getUtility(bid).doubleValue() > (avgMaxUtility * 1.05);
        }*/

		// Check a simple business rule
//		Boolean nearDeadline = isNearNegotiationEnd() == 2;
//		Boolean acceptable = this.utilitySpace.getUtility(bid).doubleValue() > 0.9;
//		Boolean good = this.utilitySpace.getUtility(bid).doubleValue() > 0.92;
//		return (nearDeadline && acceptable) || good;
	}
	
	private void updateFreqMap(Bid bid) {
		if (bid != null) {
			Set<String> issues = bid.getIssues();

			for (String s : issues) {
				Pair p = freqMap.get(s);
				Value v = bid.getValue(s);

				String vs = valueToStr(v, p);
				p.vList.put(vs, p.vList.get(vs)+1);
			}
		}
	}

	private double calcOpValue(Bid bid) {
		double value = 0;

		Set<String> issues = bid.getIssues();
		double[] valUtil = new double[issues.size()];
		double[] issWeght = new double[issues.size()];
		int k = 0; // index

		for (String s : issues) {
			Pair p = freqMap.get(s); 
			Value v = bid.getValue(s);
			String vs = valueToStr(v, p);
			
			// calculate utility of value (in the issue)
			int sumOfValues = 0;
			int maxValue = 1;
			for (String vString : p.vList.keySet()) {
				sumOfValues += p.vList.get(vString);
				maxValue = Math.max(maxValue, p.vList.get(vString)); 
				// TODO: find the highest frequency of value
			}
			// calculate estimated utility of the issuevalue
//			valUtil[k] = (float)p.vList.get(vs)/sumOfValues;
			valUtil[k] = (float)p.vList.get(vs)/maxValue;
			
			// calculate the inverse std deviation of the array
			double mean = (float)sumOfValues/p.vList.size();
			for (String vString : p.vList.keySet())
				issWeght[k] += Math.pow(p.vList.get(vString) - mean, 2);
			issWeght[k] = 1.0/Math.sqrt((issWeght[k]+0.1)/p.vList.size());
			
			k++;
		}

		double sumOfWght = 0;
		for (k=0; k < issues.size(); k++) {
			value += valUtil[k]*issWeght[k];
			sumOfWght += issWeght[k];
		}
		
		return value/sumOfWght;
		}
	
	private boolean isOpGood(Bid bid) {
		if (bid == null)
			return false;
		
		double value = calcOpValue(bid);
		double opThreshold = this.opAvgUtil != 0 ? 0.8*this.opAvgUtil : 0.6; // default value 
		return (value > opThreshold); 
	}
	
	private String valueToStr(Value v, Pair p) {
		String v_str = "";
		if (p.type == 0) {
			v_str = ((DiscreteValue)v).getValue(); 
		} else if (p.type == 1) {
			v_str = ((NumberValue)v).getValue().toPlainString(); 
		}
		
		if (v_str.equals(""))
				System.out.println("Warning: Value wasn't found");
		return v_str;
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