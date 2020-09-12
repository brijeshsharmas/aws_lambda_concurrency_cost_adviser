package os.aws.lambda.cal;


import os.aws.lambda.cal.config.Config;
import os.aws.lambda.cal.modal.JsonPayload;
import os.aws.lambda.cal.service.AWSServiceFactory;
import os.aws.lambda.cal.util.Renderer;
import os.aws.lambda.cal.util.Util;

import static os.aws.lambda.cal.config.ConfigConstants.KEY_MIN_MAX_MEMORY;
import static os.aws.lambda.cal.config.ConfigConstants.KEY_NUM_INVOCATION;
import static os.aws.lambda.cal.config.ConfigConstants.KEY_INVOCATION_TYPE;

import static os.aws.lambda.cal.config.ConfigConstants.ARGUMENT_HELP;
import static os.aws.lambda.cal.config.ConfigConstants.ARGUMENT_HELP_JSON;
import static os.aws.lambda.cal.config.ConfigConstants.ARGUMENT_FILE;

import static os.aws.lambda.cal.config.ConfigConstants.KEY_WEIGHT;
import static os.aws.lambda.cal.config.ConfigConstants.ARGUMENT_HELP_VALIDATE;
import static os.aws.lambda.cal.config.ConfigConstants.CONFIRMATION_CONTINUUE_MSG;
import static os.aws.lambda.cal.config.ConfigConstants.CONFIRMATION_SAVE_CONFIGURATION_MSG;

import static os.aws.lambda.cal.util.Renderer.INT_STAR_LINE_WITH_MESSAGE;
import static os.aws.lambda.cal.util.Renderer.INT_UNDERSCORE_LINE;
import static os.aws.lambda.cal.util.Renderer.INT_BLANK_LINE;
import static os.aws.lambda.cal.util.Renderer.INT_DO_NOTHING;
import static os.aws.lambda.cal.util.Renderer.INT_MINUS_LINE;
import static os.aws.lambda.cal.util.Renderer.INT_FORWARD_LINE;

import java.util.Date;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.AWSLambdaException;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationResult;


public class AwsLdConCostCal {
	
	private static String buildVersion = "1.0";
	private static String name = "Lambda Concurrency & Cost Optimizer";
	
	private static Logger logger = Logger.getLogger();
	private static Renderer renderer = Renderer.getRenderer();
	private static Config config = Config.getConfig();
	private AWSServiceFactory factor = AWSServiceFactory.getFactory();
	

	/***********************************************MAIN-METHOD********************************************************************************/
	public static void main(String[] args) { 
		if (args.length > 0 && ARGUMENT_HELP.equalsIgnoreCase(args[0]))  {executeHelpSection(args); return;} 
		start(args);
	}
	
	/***********************************************PREP-AND-VALIDATION********************************************************************************/
	public void kickOff(String[] args) {
		
		int[] renderArgs = {INT_UNDERSCORE_LINE, INT_BLANK_LINE, INT_STAR_LINE_WITH_MESSAGE, INT_DO_NOTHING, INT_DO_NOTHING, INT_DO_NOTHING};
		args = Util.toLowerCase(args);
		
		switch(args.length) {
		case 0: 
			if(!captureConfiguration(args, renderArgs)) return;
			break;
		default:
			switch(args[0]) {
			case ARGUMENT_FILE:
				if (!loadConfiguration(args, renderArgs)) return;
			}
		}
		
		execute(renderArgs);	
	}
	private boolean loadConfiguration(String [] args, int[] renderArgs) {
		if(args.length < 2) {
			logger.print("Missing Config File/Path Argument. Plese Use " + ARGUMENT_FILE + " <<config.json>>");
			return false;
		}
		renderer.printLine(renderArgs, "Loading Configuration");
		boolean loadConfigStatus =  config.loadConfiguration(args[1]);
		renderArgs[2] = INT_DO_NOTHING;
		renderer.printLine(renderArgs);
		return loadConfigStatus;
		
	}
	private boolean captureConfiguration(String [] args, int[] renderArgs) { 
		renderer.printLine(renderArgs, "Begin Capturing Configuration");
		//Capture and Validate Input
		boolean captureConfigPassed = config.captureAndValidateInput();
		
		renderer.printLine(renderArgs, "Configuration Provided By User Are Below");
		config.printAllConfiguration();
		
		renderArgs[2] = INT_DO_NOTHING;
		renderer.printLine(renderArgs);
		
		if (config.confirmYesNo(CONFIRMATION_SAVE_CONFIGURATION_MSG)) config.saveConfiguration();
		renderer.printLine(renderArgs);
		
		return captureConfigPassed;
	}
	private void execute(int[] renderArgs) {
		renderArgs[0]=INT_DO_NOTHING; renderArgs[1]=INT_DO_NOTHING; renderArgs[2] = INT_STAR_LINE_WITH_MESSAGE;
		renderer.printLine(renderArgs, "Begin Executing Configuration");
		
		summarizeExecution();
		renderArgs[2] = INT_MINUS_LINE;
		renderer.printLine(renderArgs, "");
		
		if (!config.confirmYesNo(CONFIRMATION_CONTINUUE_MSG)) return;
		
		executeScenarios(renderArgs);
		
	}
	
	private static void start(String []args) {
		startupMessage();
		new AwsLdConCostCal().kickOff(args); 
	}
	
	/************************************************EXECUTION SECTION*******************************************************************************/
	private boolean executeScenarios(int[] renderArgs) {
		renderArgs[0]=INT_DO_NOTHING; renderArgs[1]=INT_DO_NOTHING; renderArgs[2] = INT_DO_NOTHING;
		for(int i=config.getMinMemoryNumber(); i<=config.getMaxMemoryNumber(); i=i+config.getIncrementMemoryNumber()) {
			renderArgs[0]=INT_UNDERSCORE_LINE; renderArgs[1]=INT_STAR_LINE_WITH_MESSAGE; 
			renderer.printLine(renderArgs, "Begin Execution For Memory [" + i + "]");
			
			if(doAbort_IfUpdateMemoryAdjustment_Fail(i, renderArgs)) return false;
			if(doAbort_IfLambdaInvocation_Fail()) return false;
			if(doAbort_IfLambdaMetricCollection_Fail()) return false;
			
			renderArgs[0]=INT_STAR_LINE_WITH_MESSAGE; renderArgs[1]=INT_UNDERSCORE_LINE;
			renderer.printLine(renderArgs, "Ended Execution For Memory [" + i + "]");
		}
		
		
		return true;
	}
	/**To Execute This Operation, IAM User Must Have lambda:UpdateFunctionConfiguraton permission**/
	private boolean doAbort_IfUpdateMemoryAdjustment_Fail(int memorySize, int[] renderArgs) {
		AWSLambda lambdaClient = factor.getAWSLambdaClient();
		UpdateFunctionConfigurationRequest updateConfigRequest = new UpdateFunctionConfigurationRequest()
				.withFunctionName(config.getLambdaFunctionARN())
				.withMemorySize(memorySize);
		UpdateFunctionConfigurationResult updateConfigResult = null;
		
		try { updateConfigResult = lambdaClient.updateFunctionConfiguration(updateConfigRequest);
		}catch(AWSLambdaException exception ) {
			printAbortMessage(renderArgs, "Aborting Operation-->AWSLambdaException-->Execute Update Function Configuration - Memory Adjustment [" + memorySize + "]. Error [" + exception.getMessage() + "]");
			return true;
		}catch(Exception exception) {
			printAbortMessage(renderArgs, "Aborting Operation-->GeneralException-->Execute Update Function Configuration - Memory Adjustment [" + memorySize + "]. Error [" + exception.getMessage() + "]");
			return true;
		}
		logger.print("Lambda Function Succesfully Updated With Memory [" + memorySize + "]");
		
		return false;
	}
	private boolean doAbort_IfLambdaInvocation_Fail() {
		
		return false;
	}
	private boolean doAbort_IfLambdaMetricCollection_Fail() {
		
		return false;
	}
	/************************************************HELP SECTION*******************************************************************************/
	private static void executeHelpSection(String args[]) {
		if(args.length == 1) printHelpSection();
		else if (args.length > 1 && args[1].equalsIgnoreCase(ARGUMENT_HELP_JSON)) executeJsonHelpSection(args);
	}
	private static void executeJsonHelpSection(String args[]) {
		if(args.length == 2) printJsonHelpSection();
		else if (args.length > 2 && args[2].equalsIgnoreCase(ARGUMENT_HELP_VALIDATE)) executeJsonValidateSection(args);
	}
	private static void executeJsonValidateSection(String args[]) {
		if(args.length == 3) {
			System.out.println("Missing json-file Path In Argument.\nValid Syntax Is: " + ARGUMENT_HELP + " " + ARGUMENT_HELP_JSON + " " + ARGUMENT_HELP_VALIDATE + " <<file_path>>");
			return;
		}
		JsonPayload jsonPayLoad = config.loadJson(args[3]);
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
		logger.print("For Help, Use " + ARGUMENT_HELP + ", Or For More Details, Visit https://tools.brijeshsharma.com");
	}
	private void summarizeExecution() {
		//Summarizing Execution Cycles
		logger.print("Planned Execution Summarized Below");
		logger.print("\tAs Per Config [Key=" +  KEY_MIN_MAX_MEMORY + ", Value=" + config.getStringConfigValue(KEY_MIN_MAX_MEMORY) + "], Calculator Will Make Total Of [" 
				+ config.getNumberOfMemoryAdjustmentCycles() + "] Memory Adjustments.");
		logger.print("\tFor Each Memory Adjustment Execution Cycle, Calculator Will Run [" + config.getNumberOfInvocationPerCycle() + "] Invocations.");
		if (config.getNumberOfPayloads() == 0) logger.print("\tNo Json Payload Will Be Used For Each Invocation");
		else logger.print("\tTotal Invocation [" + config.getNumberOfInvocationPerCycle() + "] Will Be Spread Across [" + 
				config.getNumberOfPayloads() + "] Json Payloads As " +  config.getPayloadSpreadString()  + " Respectively");
		logger.print("\tEach Invocation Type Will Be [" + (config.isInvocationTypeSynchronous() ? "Synchronous" : "Asynchronous") + "]");
	}
	private void printAbortMessage(int [] renderArgs, String msg) {
		renderArgs[0] = INT_BLANK_LINE; renderArgs[1] = INT_FORWARD_LINE;renderArgs[2] = INT_STAR_LINE_WITH_MESSAGE;
		renderArgs[3] = INT_FORWARD_LINE;renderArgs[4] = INT_BLANK_LINE;
		renderer.printLine(renderArgs,msg);
	}
	
}
