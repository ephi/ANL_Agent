package geniusweb.exampleparties.learningagent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IssueValKey {

	public String issue;
	public String value;
	public int    value_type = -1; //-1 - An invalid value type, 0 - Discrete value, 1 - Number value
	
	@Override
    public boolean equals(Object obj) {
        if(obj != null && obj instanceof IssueValKey) {
        	IssueValKey s = (IssueValKey)obj;
            return issue.equals(s.issue) && value.equals(s.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (issue + value).hashCode();
    }
    public static List<IssueValueFreqElement> createValueFreqList(HashMap<IssueValKey, Integer> m, String issue){
    	List<IssueValueFreqElement> ls = new ArrayList<IssueValueFreqElement>();
    	 for (Map.Entry<IssueValKey,Integer> entry : m.entrySet()) {
    		 IssueValKey ivk = entry.getKey();
    		 if(ivk.issue.equals(issue)) {
    			 IssueValueFreqElement e = new IssueValueFreqElement(); // TODO: WHAT IS 
    			 e.freq = entry.getValue();
    			 e.value_type = ivk.value_type;
    			 e.value      = ivk.value;
    			 ls.add(e);
    		 }
    	 }
    	
    	return ls;
    }
    public static HashMap<IssueValKey, Integer> cloneIssueValMap(HashMap<IssueValKey, Integer> m){
    	HashMap<IssueValKey, Integer> mp = new HashMap<IssueValKey,Integer>();
    	for (Map.Entry<IssueValKey,Integer> entry : m.entrySet()) {
    		 IssueValKey ivk = entry.getKey();
    		 IssueValKey n_ivk = new IssueValKey();
    		 n_ivk.issue = ivk.issue;
    		 n_ivk.value = ivk.value;
    		 n_ivk.value_type = ivk.value_type;
    		 Integer     val = entry.getValue();
    		 mp.put(n_ivk, val);
    	}
    	return mp;
    }
}