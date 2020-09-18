/**
 * 
 */
package os.aws.lambda.cal.modal;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import static os.aws.lambda.cal.config.ConfigConstants.KEY_BODY;
import static os.aws.lambda.cal.config.ConfigConstants.KEY_WEIGHT;

/**
 * @author Brijesh Sharma
 *
 */
public class JsonPayload {
	
	private JSONArray jsonArray = null;	
	private String validationMessage = null;
	
	//TO-DO: Json body could be array, not JSON Object
	
	/****************************************BUILD METHODS*****************************************************/
	protected JsonPayload(JSONArray jsonArray) { 
		this.jsonArray = jsonArray;
		if(!validateJson()) throw new RuntimeException("Not A Valid Json Payload. Error [" + validationMessage + "]");
	}
	public static JsonPayload buildJsonPayload(JSONArray jsonArray) { return new JsonPayload(jsonArray); }
	
	/****************************************BUILD METHODS*****************************************************/
	public boolean validateJson(JSONArray jsonArray) {
		if(jsonArray == null || jsonArray.size() == 0) {
			validationMessage = "Null/Empty Json Payload";
			return false;
		}
		for (int i=0; i < jsonArray.size(); i++) {
			if (! (jsonArray.get(i) instanceof JSONObject)) {
				validationMessage = "Element Number [" + i + "] Is An Array Element, Which Is Currently Not Supported. Please Change It To Json Object";
				return false;	
			}
			
			JSONObject object = (JSONObject)jsonArray.get(i);
			if(!object.containsKey(KEY_BODY)) {
				validationMessage = "Element Number [" + i + "] Does Not Contain Key [" + KEY_BODY + "]";
				return false;
			}
		}
		return true;
	}
	public boolean validateJson() { return validateJson(this.jsonArray); 	}
	
	/****************************************GETTTER-SETTER*****************************************************/
	public String validationMessage() { return validationMessage;}
	public String getPayloadSpreadString(int invocationCount) {
		String returnMsg = "[";
		int []arr = getPayloadSpread(invocationCount);
		
		for(int i=0; i<arr.length; i++)
			returnMsg += " " +  arr[i];
		
		return returnMsg+"]";
	}
	public int[] getPayloadSpread(int invocationCount) {
		
		//Convert to float
		float totalWeight = getTotalWeight();
		float invocation = invocationCount;
		
		int totalSpread = 0;
		int [] arr = new int[getNumberOfPayloads()];
		
		for(int i=0; i<getNumberOfPayloads(); i++) {
			//System.out.println("Next Weight [" + getWeight(i) + ", Total Weight " + getTotalWeight());
			float nextWeight = getWeight(i);
			int nextSpread = (int) (nextWeight/totalWeight*invocation);
			totalSpread += nextSpread;
			if(totalSpread > invocationCount) nextSpread = nextSpread - (totalSpread - invocationCount);
			arr[i] = nextSpread;
		}
		if (totalSpread < invocationCount) arr[arr.length-1] = arr[arr.length-1] + (invocationCount - totalSpread);
		
		return arr;
		
	}
	public int getNumberOfPayloads( ) { return jsonArray.size(); }
	public JSONObject getBody(int index) { return (JSONObject) ((JSONObject)jsonArray.get(index)).get(KEY_BODY);}
	public int getWeight(int index) { 
		JSONObject obj = (JSONObject)jsonArray.get(index);
		if (obj == null) return 1;
		return obj.get(KEY_WEIGHT) == null ? 1 : Integer.parseInt(obj.get(KEY_WEIGHT).toString());
	}
	public int getTotalWeight() { 
		int totalWeight = 0;
		for(int i=0; i <getNumberOfPayloads(); i++)
			totalWeight += getWeight(i);
		return totalWeight == 0 ? 1: totalWeight;
	}
	
	public String toString() {
		return jsonArray.toJSONString();
	}
}
