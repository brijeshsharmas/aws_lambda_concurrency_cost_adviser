/**
 * 
 */
package os.aws.lambda.cal.config;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.amazonaws.services.lambda.model.InvocationType;

import os.aws.lambda.cal.modal.JsonPayload;
import os.aws.lambda.cal.service.Logger;
import os.aws.lambda.cal.util.Util;

/**
 * @author Brijesh Sharma
 *
 */
public class Config implements ConfigConstants {
	
	private Logger logger = Logger.getLogger();
	private Map<String, String> mapStringConfig = new HashMap<String, String>();
	private Map<String, Integer> mapIntConfig = new HashMap<String, Integer>();
	private Map<String, Boolean> mapBoolConfig = new HashMap<String, Boolean>();
	private List<Integer> listValidLambdaMemoryValues = null;
	private JsonPayload jsonPayLoad = null;
	private static Config config = null;
	
	/*******************************************************SINGLETOM IMPLEMENTATION**********************************************************************/
	protected Config() { setDefault();}
	
	public static Config getConfig() {
		if(config != null) return config;
		
		synchronized (Config.class) {
			if(config != null) return config;
			
			return config = new Config();
		}
		
	}
	
	/************************************************GETTER-METHOD**************************************************************************************/
	public String getStringConfigValue(String key) { return mapStringConfig.get(key);	}
	public int getIntConfigValue(String key) { return mapIntConfig.get(key);	}
	public boolean getBooleanConfigValue(String key) { return mapBoolConfig.get(key);	}
	
	public boolean doEnableLogging() { return mapBoolConfig.get(KEY_LOG_MESSAGE);	}
	
	public String getLambdaFunctionConfig() { return mapStringConfig.get(KEY_LAMBDA_FUNCTION_NAME);}
	
	public int getMinMemoryNumber() {return Util.getIntValueByPosition(mapStringConfig.get(KEY_MIN_MAX_MEMORY), 0, MIN_MAX_DELIMETER);}
	public int getMaxMemoryNumber() {return Util.getIntValueByPosition(mapStringConfig.get(KEY_MIN_MAX_MEMORY), 1, MIN_MAX_DELIMETER);}
	public int getIncrementMemoryNumber() {return Util.getIntValueByPosition(mapStringConfig.get(KEY_MIN_MAX_MEMORY), 2, MIN_MAX_DELIMETER);}
	
	public int getNumberOfInvocationPerCycle() { return mapIntConfig.get(KEY_NUM_INVOCATION);}
	public int getNumberOfMemoryAdjustmentCycles() { return 1 + (getMaxMemoryNumber()-getMinMemoryNumber()) / getIncrementMemoryNumber();}
	
	public int getNumberOfPayloads() { return jsonPayLoad == null ? 0: jsonPayLoad.getNumberOfPayloads();}
	public int[] getPayloadSpread() { return jsonPayLoad == null ? new int[] {getNumberOfInvocationPerCycle()} : jsonPayLoad.getPayloadSpread(getNumberOfInvocationPerCycle());}
	public String getPayloadSpreadString() {return jsonPayLoad == null ? "" : jsonPayLoad.getPayloadSpreadString(getNumberOfInvocationPerCycle()); }
	public String getPayloadBody(int index) { return jsonPayLoad == null ? null : (jsonPayLoad.getBody(index) == null ? null : jsonPayLoad.getBody(index).toJSONString());}
	
	public boolean isInvocationTypeSynchronous( ) { return mapBoolConfig.get(KEY_SYNCH_INVOCATION_TYPE); }
	public InvocationType getInvocationType( ) { return isInvocationTypeSynchronous() ? InvocationType.RequestResponse : InvocationType.Event;}
	
	public String getProxyHost() { return mapStringConfig.get(KEY_PROXY_HOST);}
	public int getProxyPort() { return mapIntConfig.get(KEY_PROXY_PORT);}
	public boolean hasProxy() {return DO_NOT_USE_VALUE.equalsIgnoreCase(mapStringConfig.get(KEY_PROXY_HOST)) ? false : true;}
	
	public String getAWSRegion() { return mapStringConfig.get(KEY_AWS_REGION);}
	
	public String getAcessKey() { return mapStringConfig.get(KEY_AWS_ACCESS_KEY);}
	public String getSecretAccessKey() { return mapStringConfig.get(KEY_AWS_ACCESS_SECRET_KEY);}
	public boolean useDefaultCredentialsProvider() {return USE_DEFAULT_VALUE.equalsIgnoreCase(mapStringConfig.get(KEY_AWS_ACCESS_KEY)) ? true : false;}
	
	/*******************************************************CAPTURE AND VALIDATE CONFIGURATION******************************************************************/
	public boolean confirmYesNo(String confirmMessage) {
		Scanner scanner = new Scanner(System.in);
		print(null,confirmMessage, 0);
		String value = scanner.next().trim();
		return YES_DEFAULT_VALUE.equalsIgnoreCase(value) ? true: false;
	}
	public boolean captureAndValidateInput() {
		
		int inputCounter = 0;
		Scanner scanner = new Scanner(System.in);

		//Capture if Log are to be written to a log file
		if(captureBooleanInput(scanner, KEY_LOG_MESSAGE, LOG_MESSAGE_MSG, ++inputCounter)) logger.enableWriteToFile();
		
		//Capture all string inputs AWS_ACCESS_KEY,  AND AWS_ACCESS_SECRET_KEY, etc
		if (!DO_NOT_USE_VALUE.equalsIgnoreCase(captureStringInput(scanner, KEY_AWS_ACCESS_KEY, AWS_ACCESS_KEY_MSG, ++inputCounter)))
			captureStringInput(scanner, KEY_AWS_ACCESS_SECRET_KEY, AWS_ACCESS_SECRET_KEY_MSG, ++inputCounter);
		
		if (!DO_NOT_USE_VALUE.equalsIgnoreCase(captureStringInput(scanner, KEY_PROXY_HOST, PROXY_HOST_MSG, ++inputCounter)))
			if (!captureIntInput(scanner, KEY_PROXY_PORT, PROXY_PORT_MSG,0, ++inputCounter)) return false;
		
		//Lambda Config
		captureStringInput(scanner, KEY_LAMBDA_FUNCTION_NAME, LAMBDA_FUNCTION_MSG, ++inputCounter);
		captureStringInput(scanner, KEY_AWS_REGION, AWS_REGION_MSG, ++inputCounter);
		captureBooleanInput(scanner, KEY_SYNCH_INVOCATION_TYPE, INVOCATION_TYPE_MSG, ++inputCounter);
		if (!captureIntInput(scanner, KEY_NUM_INVOCATION, NUM_INVOCATION_MSG,0, ++inputCounter)) return false;
		//Special Handling For Memory Range
		if (!captureMemoryRange(scanner, ++inputCounter)) return false;
		
		if (!capturePayload(scanner, ++inputCounter)) return false;
		
		return true;
	}
	private boolean capturePayload(Scanner scanner, int inputCounter) {
		String payloadValue = null;
		for(int i = 0; i < 3; i++) {//Allowed Three Attempts
			if (i==0) payloadValue = captureStringInput(scanner, KEY_JSON_PAYLOAD, JSON_PAYLOAD_MSG, inputCounter);
			else payloadValue = captureStringInput(scanner, KEY_JSON_PAYLOAD, "Attempt No (" + (i+1) + ")-->" + JSON_PAYLOAD_MSG, inputCounter);
			
			if(DO_NOT_USE_VALUE.equalsIgnoreCase(payloadValue)) return true;
			if ((jsonPayLoad = loadJson(payloadValue)) != null) { 
				mapStringConfig.put(KEY_JSON_PAYLOAD, payloadValue);
				return true;
			}
			
			mapStringConfig.put(KEY_JSON_PAYLOAD, DO_NOT_USE_VALUE);
			if(i<2) logger.print("Invalid File [" + payloadValue + "] Path Or Json Payload File. Please Try Again");
			else  {
				logger.printAbortMessage("Invalid File [" + payloadValue + "] Path Or Json Payload File. Max Attempts Tried, Aborting Operation");
				return false;
			}
		}
		return true;
	}
	public JsonPayload loadJson(String path) {
		if(!Util.isFileExists(path)) {
			logger.print("Invalid File Or File-Path [" + path + "]");
			return null;
		}
		Reader reader = null;
		try { 
			reader = new FileReader(path); 
			JSONParser parser = new JSONParser();
			JSONArray jsonRoot = (JSONArray)parser.parse(reader);
			return JsonPayload.buildJsonPayload(jsonRoot);
		} catch(IOException e) { logger.print("Error Loading Json File [" + path + "]. Error [" + e.getMessage() + "]"); 			
		} catch(ParseException e) { logger.print("Error Parsing Json File [" + path + "]. Error [" + e.getMessage() + "]");
		} finally {close(reader);}
		return null;
	}
	private boolean captureMemoryRange(Scanner scanner, int inputCounter) {
		String minMaxIncrement = null;
		for(int i = 0; i < 3; i++) {//Allowed Three Attempts
			if(i==0) minMaxIncrement = captureStringInput(scanner, KEY_MIN_MAX_MEMORY, MIN_MAX_MEMORY_MSG + "\n" + MIN_MAX_MEMORY_LIST_MSG, inputCounter);
			else minMaxIncrement = captureStringInput(scanner, KEY_MIN_MAX_MEMORY, MIN_MAX_MEMORY_MSG, inputCounter);
			
			if(DO_NOT_USE_VALUE.equalsIgnoreCase(minMaxIncrement)) return true;
			
			int minMemoryRange = Util.getIntValueByPosition(getStringConfigValue(KEY_MIN_MAX_MEMORY), 0, MIN_MAX_DELIMETER);
			int maxMemoryRange = Util.getIntValueByPosition(getStringConfigValue(KEY_MIN_MAX_MEMORY), 1, MIN_MAX_DELIMETER);
			int incrementMemoryRange = Util.getIntValueByPosition(getStringConfigValue(KEY_MIN_MAX_MEMORY), 2, MIN_MAX_DELIMETER);
			if(!isValidLambdaMemoryConfiguration(minMemoryRange, maxMemoryRange, incrementMemoryRange)) { 
				if (i  < 2) 
					logger.print(INVALID_MIN_MAX_INCREMENT_MSG);
				else {
					logger.printAbortMessage("Tried Max Attempts. Invalid Min-Max-Increment Values Specified. Aborting Operation");
					return false;
				}
				
			}else {
				//mapIntConfig.put(KEY_MIN_MEMORY, minMemoryRange);
				//mapIntConfig.put(KEY_MAX_MEMORY, maxMemoryRange);
				//mapIntConfig.put(KEY_INCREMENT_MEMORY, incrementMemoryRange);
				return true;
			}
		}
		return true;
	}
	/**Store String Input Only If Value Provided Is Not Equals To {@link ConfigConstants#DO_NOT_USE_VALUE}**/
	private String captureStringInput(Scanner scanner, String key, String inputMsg, int inputCounter ) {
		printAttempMsg(key, inputMsg, 0, inputCounter);
		String inputValue = scanner.next().trim();
		if(!DO_NOT_USE_VALUE.equalsIgnoreCase(inputValue))
			mapStringConfig.put(key, inputValue);
		return inputValue;
	}
	private boolean captureIntInput(Scanner scanner, String key, String inputMsg, int attemptCounter, int inputCounter ) {
		printAttempMsg(key, inputMsg, attemptCounter, inputCounter);
		
		try {
			int parsedValue = Integer.parseInt(scanner.next());
			mapIntConfig.put(key, parsedValue);
		}catch(NumberFormatException e) {
			if(attemptCounter > 1) {
				logger.printAbortMessage("Error Converting Input To Int. Max Attempt Reached. Aborting Operation.");
				return false;
			}
			logger.print("Error Converting Input To Int. Please Try Again");
			return captureIntInput(scanner, key, inputMsg, ++attemptCounter, inputCounter);
		}
		
		return true;
	}
	private boolean captureBooleanInput(Scanner scanner, String key, String inputMsg, int inputCounter ) {
		printAttempMsg(key, inputMsg, 0, inputCounter);
		
		boolean boolValue =  Boolean.parseBoolean(scanner.next());
		mapBoolConfig.put(key, boolValue);
		return boolValue;
		
	}
	/******************************************VALIDATIONS METHOD******************************************************************************************/
	public boolean isValidLambdaMemory(int memoryMb) { return listValidLambdaMemoryValues.contains(memoryMb); }
	private boolean isValidLambdaMemoryConfiguration(int min, int max, int increment) {
		if(min == CODE_ERROR_FORMATTING_VALUE || max == CODE_ERROR_FORMATTING_VALUE ||  increment == CODE_ERROR_FORMATTING_VALUE 
				|| max < min || increment % 64 != 0 
				|| !isValidLambdaMemory(min) || !isValidLambdaMemory(max))
			return false;
		return true;
	}
	public String validationMessage() { return jsonPayLoad == null ? null : jsonPayLoad.validationMessage();}
	/******************************************DEFAULT SETTING METHOD******************************************************************************************/
	private void setDefault() {
		mapStringConfig.put(KEY_AWS_ACCESS_KEY, USE_DEFAULT_VALUE);
		mapStringConfig.put(KEY_AWS_ACCESS_SECRET_KEY, USE_DEFAULT_VALUE);
		mapStringConfig.put(KEY_MIN_MAX_MEMORY, MIN_MAX_MEMORY_DEFAULT_VALUE);
		mapStringConfig.put(KEY_JSON_PAYLOAD, DO_NOT_USE_VALUE);
		mapStringConfig.put(KEY_PROXY_HOST, DO_NOT_USE_VALUE);
		
		mapBoolConfig.put(KEY_LOG_MESSAGE, LOG_MESSAGE_DEFAULT_VALUE);
		mapBoolConfig.put(KEY_SYNCH_INVOCATION_TYPE, INVOCATION_TYPE_DEFAULT);
		
		//Populate Valid Value Memory List
		if (listValidLambdaMemoryValues == null) {
			listValidLambdaMemoryValues = new ArrayList<Integer>();
			for(int i = 128; i < 3009; i = i +64)
				listValidLambdaMemoryValues.add(i);
		}
		
	}
	
	/******************************************PRINT-LOGGING, CLEAN METHODS******************************************************************************************/
	private void printAttempMsg(String key, String inputMessage, int attemptCounter, int inputCounter) {
		logger.printBlankLine();
		if(attemptCounter > 0)
			print(key, "Attempt No (" + (attemptCounter+1) + ")-->Input Message=" + inputMessage, inputCounter);
		else
			print(key, "InputMessage=" + inputMessage, inputCounter);
	}
	private void print(String key, String inputMessage, int inputCounter) { 
		logger.print((inputCounter > 0 ? "Input Counter=" + inputCounter + "-->": "") + 
				( key!=null ? "Key=" + key + "-->" : "") + inputMessage);
	}
	public void printAllConfiguration() {
		for(Map.Entry<String, String> entry: mapStringConfig.entrySet()) {
			if(entry.getKey().equals(KEY_AWS_ACCESS_KEY) && !USE_DEFAULT_VALUE.equals(entry.getValue()))
				logger.print(entry.getKey() + "-->********************");
			else if(entry.getKey().equals(KEY_AWS_ACCESS_SECRET_KEY) && !USE_DEFAULT_VALUE.equals(entry.getValue()))
				logger.print(entry.getKey() + "-->********************");
			else logger.print(entry.getKey() + "-->" + entry.getValue());
		}
		
		for(Map.Entry<String, Integer> entry: mapIntConfig.entrySet())
			logger.print(entry.getKey() + "-->" + entry.getValue());
		
		for(Map.Entry<String, Boolean> entry: mapBoolConfig.entrySet())
			logger.print(entry.getKey() + "-->" + entry.getValue());
		
	}
	private void close(Reader reader) {try { if (reader != null) reader.close();}catch(Exception e) {}}
	
	/******************************************CONFIGURATION SAVE AND LOAD METHODS******************************************************************************************/
	public boolean saveConfiguration() {
		JSONObject jsonObj = new JSONObject(mapIntConfig);
		jsonObj.putAll(mapBoolConfig);
		for(Map.Entry<String, String> entry: mapStringConfig.entrySet()) {
			if (USE_DEFAULT_VALUE.equalsIgnoreCase(entry.getValue()) || DO_NOT_USE_VALUE.equalsIgnoreCase(entry.getValue())) continue;
			jsonObj.put(entry.getKey(), entry.getValue());
		}
		if ( Util.writeToFile(CONFIG_FILE_NAME, jsonObj.toJSONString())) {
			logger.print("Configuration Saved Succesfully To File [" + CONFIG_FILE_NAME + "] In Current Directory. You Can Use --file " + CONFIG_FILE_NAME + " option To Execute With Saved Configuration");
			return true;
		} 
		return false;
	}
	public boolean loadConfiguration(String configFilePath) {
		JSONObject jsonObject = Util.loadJsonObject(configFilePath);
		if (jsonObject == null) return false;
		
		//Check & Store Property In The Same Order As It Was Done While Capturing
		Object obj = jsonObject.get(KEY_AWS_ACCESS_KEY);
		if (obj != null && !USE_DEFAULT_VALUE.equalsIgnoreCase(obj.toString())) {
			String secret = (String)jsonObject.get(KEY_AWS_ACCESS_SECRET_KEY);
			if(secret == null) {
				logger.printAbortMessage("Aborting Operation-->Missing Key [" + KEY_AWS_ACCESS_SECRET_KEY + "] From Config File");
				return false;
			}
			mapStringConfig.put(KEY_AWS_ACCESS_KEY, obj.toString());
			mapStringConfig.put(KEY_AWS_ACCESS_SECRET_KEY, secret);
		}
		
		obj = jsonObject.get(KEY_LOG_MESSAGE);
		if(obj != null) mapBoolConfig.put(KEY_LOG_MESSAGE, Boolean.parseBoolean(obj.toString()));
		if(doEnableLogging()) logger.enableWriteToFile();
		
		obj = jsonObject.get(KEY_SYNCH_INVOCATION_TYPE);
		if(obj != null) mapBoolConfig.put(KEY_SYNCH_INVOCATION_TYPE, Boolean.parseBoolean(obj.toString()));
		 
		obj = jsonObject.get(KEY_PROXY_HOST);
		if (obj != null && !DO_NOT_USE_VALUE.equalsIgnoreCase(obj.toString())) {
			Object port = jsonObject.get(KEY_PROXY_PORT);
			if (port == null || !Util.isNumeric(port.toString())) {
				logger.printAbortMessage("Aborting Operation-->Invalid Key [" + KEY_PROXY_PORT + "] From Config File As " + (port == null ? "It Does Not Exist" : "It Is Not A Number"));
				return false;
			}
			mapStringConfig.put(KEY_PROXY_HOST, obj.toString());
			mapIntConfig.put(KEY_PROXY_PORT, Integer.parseInt(port.toString()));
		}
		
		obj = jsonObject.get(KEY_LAMBDA_FUNCTION_NAME);
		if (obj == null) {
			logger.printAbortMessage("Aborting Operation-->Missing Mandatory Key [" + KEY_LAMBDA_FUNCTION_NAME + "] From Config File");
			return false;
		} else mapStringConfig.put(KEY_LAMBDA_FUNCTION_NAME, obj.toString());
		obj = jsonObject.get(KEY_AWS_REGION);
		if (obj == null) {
			logger.printAbortMessage("Aborting Operation-->Missing Mandatory Key [" + KEY_AWS_REGION + "] From Config File");
			return false;
		} else mapStringConfig.put(KEY_AWS_REGION, obj.toString());
		
		obj = jsonObject.get(KEY_NUM_INVOCATION);
		if (obj == null || !Util.isNumeric(obj.toString())) {
			logger.printAbortMessage("Aborting Operation-->Invalid Key [" + KEY_NUM_INVOCATION + "] From Config File As " + (obj == null ? "It Does Not Exist" : "It Is Not A Number"));
			return false;
		} else mapIntConfig.put(KEY_NUM_INVOCATION, Integer.parseInt(obj.toString()));
		
		obj = jsonObject.get(KEY_MIN_MAX_MEMORY);
		if (obj != null ) {
			int minMemoryRange = Util.getIntValueByPosition(obj.toString(), 0, "-");
			int maxMemoryRange = Util.getIntValueByPosition(obj.toString(), 1, "-");
			int incrementMemoryRange = Util.getIntValueByPosition(obj.toString(), 2, "-");
			if(!isValidLambdaMemoryConfiguration(minMemoryRange, maxMemoryRange, incrementMemoryRange)) { 
				logger.printAbortMessage("Aborting Operation-->" + INVALID_MIN_MAX_INCREMENT_MSG);
				return false;
			}
			mapStringConfig.put(KEY_MIN_MAX_MEMORY, obj.toString());
		}
		
		obj = jsonObject.get(KEY_JSON_PAYLOAD);
		if (obj != null && !DO_NOT_USE_VALUE.equalsIgnoreCase(obj.toString())) {
			if ((jsonPayLoad = loadJson(obj.toString())) == null) { 
				logger.print("Invalid File [" + obj + "] Path Or Json Payload File. Please Try Again");
				return false;
			}
			mapStringConfig.put(KEY_JSON_PAYLOAD, obj.toString());
		}
		
		logger.print("Configuration From File [" + configFilePath + "] Loaded Succesfully. Configuration Loaded Are Below");
		printAllConfiguration();
		
		return true;
	}
}
