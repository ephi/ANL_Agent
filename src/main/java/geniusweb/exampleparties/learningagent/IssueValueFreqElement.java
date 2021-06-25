package geniusweb.exampleparties.learningagent;

public class IssueValueFreqElement {
	public String value;
	public int    value_type = -1; //-1 - An invalid value type, 0 - Discrete value, 1 - Number value // TODO: in continuous bid space split to sections 
	public int    freq;
}