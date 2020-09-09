/**
 * 
 */
package os.aws.lambda.cal.modal;

import java.util.ArrayList;
import java.util.List;

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
	
	
	/****************************************BUILD METHODS*****************************************************/
	public JsonPayload(JSONArray jsonArray) { this.jsonArray = jsonArray;}
	public static JsonPayload buildJsonPayload(JSONArray jsonArray) { return new JsonPayload(jsonArray); }
	
	/****************************************BUILD METHODS*****************************************************/
	public boolean validateJson(JSONArray jsonArray) {
		if(jsonArray == null || jsonArray.size() == 0) {
			validationMessage = "Null/Empty Json Payload";
			return false;
		}
		for (int i=0; i < jsonArray.size(); i++) {
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
	public void add(JSONObject body, JSONObject weight) {
		
	}
	public JSONObject getBody(int index) { return null;}
	public int getWeight(int index) { return 0;}
	
	public String toString() {
		return jsonArray.toJSONString();
	}
}
