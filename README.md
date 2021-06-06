# ANL_Agent
An agent for ANL 2021
# Points of interest for ANL2021 (methods to focus on) in LearningAgent.java
1) void processAction(Action action) - once an oppenent has finished his turn we are notifited via this function. we can get oppenent (name, util func, etc) information here.
2) void processAgreements(Agreements agreements) - once negoration is finished this function is called, we can track history/ save the agreed utility etc here.
3) void myTurn() - in a negotiation process this function is called when this the agent turn. we do online learning (in the scope of negotiation) here/decide best course of action given what we know of the oppenent.
4) boolean isGood(Bid bid) - just a function which tries to decide how good a bid is, we can replace/modify it to fit our needs
5) void learn() - called at the learning stage of the agent. Here we are going over all negotiation data and doing some offline learning (outside the scope of negotitation)
# Points of interest in PersistentState.java
1) void update(NegotiationData negotiationData) - does the offline learning for the agent for a given negotiationData object
# Points of interest in NegotiationData.java
1) Nothing very interesting, keeps track about the oppenent name, best util we ever got from him and the util agreed upon at the end of a negotiation
# Analyzing the agent performance
download main.py script, call ANL_Analyzer function with a path to the folder which contrains the results ( .json files ) of a tournament given by the docker version found at https://github.com/brenting/ANL-2021-docker-runner running the script with show_all=True will display information of each negotiation in the tournament, that is, the utiltiy history of the offers over the turns and the last turn indicates the accepted offer (if accepted), afterwards, plot of every accepted utiltiy is displayed for all participants and the average utility is for each agent is printed in descending order 

# Offline Learning
1) Profile the opponent: utility threshold
2) Profile the competition - learn about most of agents in the competition (thresholds)
3) [Remember that the threshold may be time-variant]
4) -> Thresholds in isGood, isOpGood, time between phase 2&3
5) Scan the bids of the opponent and calculate the utility over time
