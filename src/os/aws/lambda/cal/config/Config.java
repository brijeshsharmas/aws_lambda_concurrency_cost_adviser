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

import os.aws.lambda.cal.Logger;
import os.aws.lambda.cal.Util;
import os.aws.lambda.cal.modal.JsonPayload;

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
	
	/*******************************************************CAPTURE AND VALIDATE CONFIGURATION******************************************************************/
	public boolean confirmYesNo() {
		Scanner scanner = new Scanner(System.in);
		print(null,CONFIRMATION_MSG, 0);
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
		captureStringInput(scanner, KEY_LAMBDA_ARN, LAMBDA_ARN_MSG, ++inputCounter);
		if (!captureIntInput(scanner, KEY_NUM_TOTAL_INVOCATION, NUM_TOTAL_INVOCATION_MSG,0, ++inputCounter)) return false;
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
				mapStringConfig.put(KEY_JSON_PAYLOAD, jsonPayLoad.toString());
				return true;
			}
			
			mapStringConfig.put(KEY_JSON_PAYLOAD, DO_NOT_USE_VALUE);
			if(i<2) logger.print("Invalid File [" + payloadValue + "] Path Or Json Payload File. Please Try Again");
			else  {
				logger.print("Invalid File [" + payloadValue + "] Path Or Json Payload File. Max Attempts Tried, Aborting Operation");
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
			
			int minMemoryRange = Util.getIntValueByPosition(getStringConfigValue(KEY_MIN_MAX_MEMORY), 0, "-");
			int maxMemoryRange = Util.getIntValueByPosition(getStringConfigValue(KEY_MIN_MAX_MEMORY), 1, "-");
			int incrementMemoryRange = Util.getIntValueByPosition(getStringConfigValue(KEY_MIN_MAX_MEMORY), 2, "-");
			if(!isValidLambdaMemoryConfiguration(minMemoryRange, maxMemoryRange, incrementMemoryRange)) { 
				if (i  < 2) 
					logger.print("Invalid Min-Max-Increment Values Specified. Valid Value Must Be Number, Min Less Than Max, Min/Max Increment Of 64 mb. Please Try Again");
				else {
					logger.print("Tried Max Attempts. Invalid Min-Max-Increment Values Specified. Aborting Operation");
					return false;
				}
				
			}else {
				mapIntConfig.put(KEY_MIN_MEMORY, minMemoryRange);
				mapIntConfig.put(KEY_MAX_MEMORY, maxMemoryRange);
				mapIntConfig.put(KEY_INCREMENT_MEMORY, incrementMemoryRange);
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
				logger.print("Error Converting Input To Int. Max Attempt Reached. Aborting Operation.");
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
		
		mapIntConfig.put(KEY_MIN_MEMORY, MIN_MEMORY_DEFAULT_VALUE);
		mapIntConfig.put(KEY_MAX_MEMORY, MAX_MEMORY_DEFAULT_VALUE);
		mapIntConfig.put(KEY_INCREMENT_MEMORY, INCREMENT_MEMORY_DEFAULT_VALUE);
		
		mapBoolConfig.put(KEY_LOG_MESSAGE, false);
		
		//Populate Valid Value Memory List
		if (listValidLambdaMemoryValues == null) {
			listValidLambdaMemoryValues = new ArrayList<Integer>();
			for(int i = 128; i < 3009; i = i +64)
				listValidLambdaMemoryValues.add(i);
		}
		
	}
	
	/******************************************PRINT-LOGGING, CLEAN METHODS******************************************************************************************/
	private void printAttempMsg(String key, String inputMessage, int attemptCounter, int inputCounter) {
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

}
