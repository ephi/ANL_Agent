# Inroduction
AlphaBIU, described below, is an agent built for ANL (Automated Negotiation League) 2021.
The agent can elicit his preferences during the negotiation, by learning the opponent’s model and negotiate in order to get the bid with the best utility.
Leading concepts for the algorithm:
•	Using expended frequency model to estimate opponent utility.
•	Compromise after the opponent agent, using learning to model the opponent.
•	Use adaptive thresholds during the negotiation to select "good" bids to offer.

# Agent Model
In automated-negotiation terms, it is customary to represent an agent by BOA model[1]:
1. Bidding strategy
2. Opponent model
3. Acceptance strategy
We will discuss each part separately. The first subsection describes the bidding mechanism of the agent. The second subsection describes the opponent model, includes the opponent threshold model and utility function model. The third subsection describe our acceptance methodology and the adaptive-alpha algorithm.

## Bidding Strategy
In general, we don't try to offer the optimal bid at every step (when "optimal bid" is used to describe "the best bid we can offer"), but the first bid its value is above the threshold.
More precisely, we divide the negotiation into two phases, defined by tPhase, a hyperparameter that set the time (relative to negotiation duration) that the phase transition occurred. tPhase was set to 0.2 (i.e., 12 seconds for 60 second negotiation). It splits the negotiation into the next phases:
1. 1st phase – in this phase we evaluate the potential bids by our own utility, compared to the utility threshold, and offer the first bid that its utility is above the threshold.
2. 2nd phase – in this phase we evaluate the potential bids by our own utility and the opponent utility, as modeled by the opponent model (see section ‎2.2). The first bid that above our threshold and opponent threshold, is offered.

At every turn, we evaluate up to |X| potential bids. X is hyperparameter and was set to 1K in our agent. After evaluating |X| potential bids, if no bid is "good" (as defined above), we offer the optimalBid (that is the bid with the highest utility for our agent).
The optimalBid is calculated during the initialization phase (the highest-utility bid that found during the initialization phase, up to 50K bids evaluated). 
Offering the optimal bid as default offer has three main advantages:
1. At every step we have to make an offer; we didn't find a good offer, but optimalBid is good, at least for us ("better than random").
2. The opponent threshold apparently decreases over time – the opponent may accept our offer because its threshold decreased.
3. An opponent that uses frequency-model to model its opponent (that is our agent) updates its model based on our offers. Offering the optimalBid helps it to model us better and offer more relevant offers in the rest of the negotiation.

Very close to the end of the negotiation, we evaluate the best bid offered by the opponent. If it's above our threshold (that decreases over time), we offer it, assuming that if the opponent offered it earlier it apparently accepts it now, and late agreement is much better than no agreement at all.

## Opponent Model
Opponent model consists of two parts:
•	Model of opponent's utility function – its issue weights and values value.
•	Model of opponent's threshold (in general, over time).
We used extended frequency model to model the first part, and learning (during learning phases) to model the second.
Frequency model ‎[6] assumes that the mechanism of creating offers is random-based, and similar to our mechanism (evaluating large amount of offers, offer the first that above the threshold). Under this assumption, values with higher value tend to appear more times in the offers. In ‎[3], it was proven to be the most accurate opponent model.
The usual frequency model estimates only opponent values, but does not estimate opponent issues weights. We extended this model to approximate opponent's issue weights, in order to approximate opponent's utility function.
We used the standard deviation of the values frequencies to approximate the issues weights. Careful analysis shows that the standard deviation of the frequencies, assuming random-with-threshold offers mechanism, depends on the standard deviation of the real opponent's issue-values (e.g., in a specific issue, issue-values are v_(1…n). The higher std(V), the higher std(f) for V as issue-values vector and F as the frequency corresponds to each value.
The other factor affects std(f) is the issue-weight. The higher weight for the issue, the higher standard deviation of the frequencies of this issue. That is because for issue with weight of 0.01, the opponent doesn't care if it gets 0.0098 (issue-value = 0.98) or 0.0023 (issue-value = 0.23) for this issue, but for issue with weight of 0.8, the opponent does care if it gets 0.72 (0.9) or 0.64 (0.8).

We assumed that the std-s ratio represents the issue-weights more than the internal distribution of issue-values of each issue. Therefore, we used these formulae to estimate to opponent's utility:

<img src="https://latex.codecogs.com/gif.latex?\bg_white&space;v_j^{s_i}=\frac{f(v_j^{s_i})}{max_k&space;f(v_k^{s_i})};&space;s_i=&space;\frac{std(V_i)}{\sum_k{std(V_k)}&space;&plus;&space;\epsilon}" title="v_j^{s_i}=\frac{f(v_j^{s_i})}{max_k f(v_k^{s_i})}; s_i= \frac{std(V_i)}{\sum_k{std(V_k)} + \epsilon}"/>

In practice, we saw that the performance of the opponent's model depends on the utility profile of the opponent. In particular –
•	In some domains, the accuracy of the approximation function is better than other.
•	But, in all domains, the approximation function captures the opponents' behavior, e.g., the function found a linear decreasing (over time) pattern for a linear-decreasing opponents.
Abovewe mentioned that we divide the negotiation into two phases. Now we can explain that the reason for this division is the need to stabilize the frequency table (for example, after one offer only, it worth nothing).

After calculating the opponent's utility, we need to estimate its threshold. As default, we assumed for all the opponents constant threshold (about 0.6). We estimated that most of the agents have higher threshold, but we added margin because of the inaccuracy of the opponent model.
We took the advantage of learning rounds to better estimate the opponent threshold. For doing this, we divided the 2nd phase into tSplit timeslots (tSplit is hyperparameter, set to 40 in our agent). During the negotiation, for each offer we got, we saved it in the corresponds timeslot. When then negotiation ends, we calculate the average of all the offers in a specific timeslot and remember that value (data from some rounds in weighted, so that later round is more important).
When we meet a known opponent (an opponent with learned data), we smooth the learned data (using moving average with specific window size), and then for each timeslot we compare the utility of the opponent (as calculated by the extended frequency model) to the learned threshold that corresponds to the current timeslot.
The rational of this method is that for an agent with random-and-threshold mechanism to create offers, and bids that distributed roughly-uniform in the utility-space, the average of many offers in timeslot that is wide enough, represents the threshold – for example, if the threshold is 0.9, we expect (under the assumptions mentioned above) to average of 0.95, but if the threshold is 0.7, we expect to average of 0.85.
This rule is symmetrical, therefore when we see average of 0.85, we assume threshold of 0.7.

## Acceptance Strategy
Opponent offers are accepted if the utility is above our threshold. In the same way, bid is considered to be "good" if its utility is above the threshold. Our threshold is changing over time, and the accurate threshold function is determined during the learning rounds, for every opponent separately.
Our threshold function (over time) is –

<img src="https://latex.codecogs.com/gif.latex?\bg_white&space;T(t)&space;=&space;V_{max}-\frac{e^{\alpha&space;t}-1}{e^\alpha}\Delta&space;V\\*\\*&space;V_{max}=0.95\quad\quad&space;V_{min}=0.6V_T&plus;0.4V_O-std(V_T))\quad\quad&space;\Delta&space;V=V_{max}-V_{min}" title="T(t) = V_{max}-\frac{e^{\alpha t}-1}{e^\alpha}\Delta V\\*\\* V_{max}=0.95\quad\quad V_{min}=0.6V_T+0.4V_O-std(V_T))\quad\quad \Delta V=V_{max}-V_{min}" />

In practice, this image show this function's behaviour.
![image](https://user-images.githubusercontent.com/52425399/131990675-27f308e2-9431-4f65-8c4e-5030d98788bd.png)

As can be seen, the higher value of alpha, the later the agent start to compromise. The purpose of alpha is to start compromising after the opponent.
For calculating alpha, we're looking for the point that the opponent agent is already compromising, using the array we created for modeling the opponent.

This way, we increase our chance to achieve better utility from the negotiation round (because the opponent may compromise before we will), and yet compromise down to T(1) in the end of the negotiation round, for maximize chance to finish with good agreement.
As mentioned above, the threshold function is used also for determining if a specific bid is good.

# Conclusions
Throughout working on the agent, we learned some important points, but mainly exposed to the world of automated negotiation, include main techniques, architectures and models.
We cannot predict our agent's performance in the competition, but hope that it'll achieve good results. A lot of efforts was invested in this agent, hopefully making it a hard competitor. We think that some innovative and interesting ideas are inherent part of the agent (extended frequency model, opponent threshold model, adaptive point of compromising).

AlphaBIU was the winner agent in ANL 2021.

