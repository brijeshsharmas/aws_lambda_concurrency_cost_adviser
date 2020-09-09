package os.aws.lambda.cal;


import os.aws.lambda.cal.config.Config;
import os.aws.lambda.cal.modal.JsonPayload;

import static os.aws.lambda.cal.config.ConfigConstants.KEY_HELP;
import static os.aws.lambda.cal.config.ConfigConstants.KEY_HELP_JSON;
import static os.aws.lambda.cal.config.ConfigConstants.KEY_WEIGHT;
import static os.aws.lambda.cal.config.ConfigConstants.KEY_HELP_VALIDATE;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Date;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class AwsLdConCostCal {
	
	private static String buildVersion = "1.0";
	private static String name = "Lambda Concurrency & Cost Optimizer";
	
	private static Logger logger = Logger.getLogger();
	
	public void kickOff() {
		
		logger.printUnderscoreLine();
		logger.printBlankLine();
		logger.printStarLine("Begin Capturing Configuration");
		
		//Capture and Validate Input
		Config config = Config.getConfig();
		boolean captureConfigPassed = config.captureAndValidateInput();
		logger.printUnderscoreLine();;
		
		logger.printBlankLine();
		logger.printStarLine("Configuration Provided By User Are Below");
		config.printAllConfiguration();
		logger.printUnderscoreLine();;
		
		if (!captureConfigPassed) return;
		
		logger.printBlankLine();
		if (config.confirmYesNo()) execute(config);
		
	}
	
	private void execute(Config config ) {
		logger.printUnderscoreLine();
		logger.printBlankLine();
		logger.printStarLine("Begin Executing Configuration");
		logger.print("Inside Execute");
		logger.printUnderscoreLine();
	}

	public static void main(String[] args) { 
		if(args.length == 0) { start(); return;}
		
		if (KEY_HELP.equalsIgnoreCase(args[0])) executeHelpSection(args);
	}
	
	private static void start() {
		startupMessage();
		new AwsLdConCostCal().kickOff(); 
	}
	
	/************************************************HELP SECTION*******************************************************************************/
	private static void executeHelpSection(String args[]) {
		if(args.length == 1) printHelpSection();
		else if (args.length > 1 && args[1].equalsIgnoreCase(KEY_HELP_JSON)) executeJsonHelpSection(args);
	}
	private static void executeJsonHelpSection(String args[]) {
		if(args.length == 2) printJsonHelpSection();
		else if (args.length > 2 && args[2].equalsIgnoreCase(KEY_HELP_VALIDATE)) executeJsonValidateSection(args);
	}
	private static void executeJsonValidateSection(String args[]) {
		if(args.length == 3) {
			System.out.println("Missing json-file Path In Argument.\nValid Syntax Is: " + KEY_HELP + " " + KEY_HELP_JSON + " " + KEY_HELP_VALIDATE + " <<file_path>>");
			return;
		}
		JsonPayload jsonPayLoad = Config.getConfig().loadJson(args[3]);
		if(jsonPayLoad == null) return;
		if (jsonPayLoad.validateJson()) System.out.println("Json Validated Succesfully");
		else System.out.println("Json Validation Failed. Error [" + jsonPayLoad.validationMessage() + "]");
	}
	private static void printHelpSection() { System.out.println("use --json after --help to get help on json payload ");}
	private static void printJsonHelpSection() {
		String msg = "Your Josn Payload Is An Array Of Payload Elements That You Want To Use While Executing Your Lambda Function";
		msg += "\nEach Element Must Have Two Keys [Body, Weight].";
		msg += "\nBelow Is The Sample Json File With Two Payload Bodies. Total " + KEY_WEIGHT + " = 3, First Body " + KEY_WEIGHT + " = 1/3 (33%) and Second Body " + KEY_WEIGHT + " 2/3 (66%)";
		msg += "\n[";
		msg += "\n\t{";
		msg += "\n\t\t\"Body\": {\"Key1\":\"Value1\", \"Key2\":\"Value2\"},";
		msg += "\n\t\t\"" + KEY_WEIGHT + "\":1";
		msg += "\n\t}";
		msg += "\n\t{";
		msg += "\n\t\t\"Body\": {\"Key3\":\"Value3\", \"Key4\":\"Value4\"},";
		msg += "\n\t\t\"" + KEY_WEIGHT + "\":2";
		msg += "\n\t}";
		msg += "\n]";
		msg += "\n" + KEY_WEIGHT + " Is Optional. If Not Provided, Will Be Default Set To 1";
		
		msg += "\nUse --validate <<json-file-path>> To Validate Your Json File";
		
		System.out.println(msg);
	}

/*******************************************************Helper Methods**********************************************************************************/	
	private static void startupMessage() {
		logger.print("Started [" + name + "] Build Version At [" + buildVersion + "] At [" + new Date(System.currentTimeMillis())  + "]");
		logger.print("For Help, Use " + KEY_HELP);
	}
	
}
