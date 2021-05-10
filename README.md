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
