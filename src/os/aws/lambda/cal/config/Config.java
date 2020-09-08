/**
 * 
 */
package os.aws.lambda.cal.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import os.aws.lambda.cal.Logger;
import os.aws.lambda.cal.Util;

/**
 * @author Brijesh Sharma
 *
 */
public class Config implements ConfigConstants {
	
	private Logger logger = Logger.getLogger();
	private Map<String, String> mapStringConfig = new HashMap<String, String>();
	private Map<String, Integer> mapIntConfig = new HashMap<String, Integer>();
	private Map<String, Boolean> mapBoolConfig = new HashMap<String, Boolean>();
	
	private int minMemoryRange = 0, maxMemoryRange = 0;
	
	public boolean captureAndValidateInput() {
		
		Scanner scanner = new Scanner(System.in);

		//Capture if Log are to be written to a log file
		if(captureBooleanInput(scanner, LOG_MESSAGE, LOG_MESSAGE_MSG)) logger.enableWriteToFile();
		
		//Capture all string inputs AWS_ACCESS_KEY,  AND AWS_ACCESS_SECRET_KEY, etc
		captureStringInput(scanner, AWS_ACCESS_KEY, AWS_ACCESS_KEY_MSG);
		captureStringInput(scanner, AWS_ACCESS_SECRET_KEY, AWS_ACCESS_SECRET_KEY_MSG);
		captureStringInput(scanner, LAMBDA_ARN, LAMBDA_ARN_MSG);
		captureStringInput(scanner, PROXY_HOST, PROXY_HOST_MSG);
		
		if(!"NO".equalsIgnoreCase(getStringConfigValue(PROXY_HOST))) {
			if (!captureIntInput(scanner, PROXY_PORT, PROXY_PORT_MSG)) return false;
		}
		
		//Special Handling For Memory Range
		if (!captureMemoryRange(scanner)) return false;
			
		return true;
	}
	
	/************************************************GETTER-METHOD**************************************************************************************/
	public String getStringConfigValue(String key) { return mapStringConfig.get(key);	}
	public int getIntConfigValue(String key) { return mapIntConfig.get(key);	}
	public boolean getBooleanConfigValue(String key) { return mapBoolConfig.get(key);	}
	
	/*****************************************************INPUT-CAPTURE-METHODS***********************************************************************/
	private boolean captureMemoryRange(Scanner scanner) {
		for(int i = 0; i < 3; i++) {
			captureStringInput(scanner, MIN_MAX_MEMORY, MIN_MAX_MEMORY_MSG);
			if(!"NO".equalsIgnoreCase(getStringConfigValue(MIN_MAX_MEMORY))) {
				minMemoryRange = Util.getIntValueByPosition(getStringConfigValue(MIN_MAX_MEMORY), 0, "-");
				maxMemoryRange = Util.getIntValueByPosition(getStringConfigValue(MIN_MAX_MEMORY), 1, "-");
				if(!isValidMemoryConfiguration(minMemoryRange, maxMemoryRange)) { 
					if (i  <2) {
						logger.print("Invalid Min-Max Values Specified. Valid Value Must Be Number, Min Less Than Max, Min/Max Increment Of 64 mb. Please Try Again");
					} else {
						logger.print("Tried Max Attempt. Invalid Min-Max Values Specified. Aborting Operation");
						return false;
					}
					
				}
			} else {
				setDefaultMemoryRange();
				return true;
			}
		}
		return true;
	}
	private boolean captureStringInput(Scanner scanner, String key, String inputMsg ) {
		logger.print(inputMsg);
		mapStringConfig.put(key, scanner.next().trim());
		return true;
	}
	private boolean captureIntInput(Scanner scanner, String key, String inputMsg, int attemptCounter ) {
		printAttempMsg(inputMsg, attemptCounter);
		
		try {
			int parsedValue = Integer.parseInt(scanner.next());
			mapIntConfig.put(key, parsedValue);
		}catch(NumberFormatException e) {
			if(attemptCounter > 1) {
				logger.print("Error Converting Input To Int. Max Attempt Reached. Aborting Operation.");
				return false;
			}
			logger.print("Error Converting Input To Int. Please Try Again");
			return captureIntInput(scanner, key, inputMsg, ++attemptCounter);
		}
		
		return true;
	}
	private boolean captureBooleanInput(Scanner scanner, String key, String inputMsg ) {
		printAttempMsg(inputMsg, 0);
		
		boolean boolValue =  Boolean.parseBoolean(scanner.next());
		mapBoolConfig.put(key, boolValue);
		return boolValue;
		
	}
	private boolean captureIntInput(Scanner scanner, String key, String inputMsg ) { return captureIntInput(scanner, key, inputMsg, 0); }
	private void printAttempMsg(String inputMessage, int attemptCounter) {
		if(attemptCounter > 0)
			logger.print("Attempt No (" + (attemptCounter+1) + ")" + inputMessage);
		else
			logger.print(inputMessage);
	}
	
	
	/******************************************VALIDATIONS METHOD******************************************************************************************/
	private boolean isValidMemoryConfiguration(int min, int max) {
		if(min == CODE_ERROR_FORMATTING || max == CODE_ERROR_FORMATTING || max < min)
			return false;
		return true;
	}
	
	/******************************************DEFAULT SETTING METHOD******************************************************************************************/
	private void setDefaultMemoryRange() {
		mapStringConfig.put(MIN_MAX_MEMORY, "128-1028");
		minMemoryRange = 128;
		maxMemoryRange = 1028;
	}
	
	/******************************************LOGGING METHODS******************************************************************************************/
	public void printAllConfiguration() {
		for(Map.Entry<String, String> entry: mapStringConfig.entrySet())
			logger.print(entry.getKey() + "-->" + entry.getValue());
		
		for(Map.Entry<String, Integer> entry: mapIntConfig.entrySet())
			logger.print(entry.getKey() + "-->" + entry.getValue());
		
		for(Map.Entry<String, Boolean> entry: mapBoolConfig.entrySet())
			logger.print(entry.getKey() + "-->" + entry.getValue());
		
	}

}
