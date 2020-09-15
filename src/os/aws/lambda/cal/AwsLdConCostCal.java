/**
 * @author Brijesh Sharma
 *
 */

package os.aws.lambda.cal;

import os.aws.lambda.cal.config.Config;
import os.aws.lambda.cal.modal.JsonPayload;
import os.aws.lambda.cal.service.AWSServiceFactory;
import os.aws.lambda.cal.util.Util;

import static os.aws.lambda.cal.config.ConfigConstants.KEY_MIN_MAX_MEMORY;
import static os.aws.lambda.cal.config.ConfigConstants.ARGUMENT_HELP;
import static os.aws.lambda.cal.config.ConfigConstants.ARGUMENT_HELP_JSON;
import static os.aws.lambda.cal.config.ConfigConstants.ARGUMENT_FILE;
import static os.aws.lambda.cal.config.ConfigConstants.KEY_WEIGHT;
import static os.aws.lambda.cal.config.ConfigConstants.ARGUMENT_HELP_VALIDATE;
import static os.aws.lambda.cal.config.ConfigConstants.CONFIRMATION_CONTINUUE_MSG;
import static os.aws.lambda.cal.config.ConfigConstants.CONFIRMATION_SAVE_CONFIGURATION_MSG;
import static os.aws.lambda.cal.config.ConfigConstants.METRIC_COLLECTION_SLEEP_MSG;
import static os.aws.lambda.cal.config.ConfigConstants.LAMBDA_PERMISSION_MSG;
import static os.aws.lambda.cal.config.ConfigConstants.KEY_SUM;
import static os.aws.lambda.cal.config.ConfigConstants.KEY_AVERAGE;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.AmazonCloudWatchException;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricDataResult;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.cloudwatch.model.MetricDataQuery;
import com.amazonaws.services.cloudwatch.model.MetricDataResult;
import com.amazonaws.services.cloudwatch.model.MetricStat;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.AWSLambdaException;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;


public class AwsLdConCostCal {
	
	private static String buildVersion = "1.0";
	private static String name = "Lambda Concurrency & Cost Optimizer Tool";
	private ConcurrentHashMap<Integer, Integer> mapMemoryAndResponse = new ConcurrentHashMap<Integer, Integer>();
	private ConcurrentHashMap<Integer, Integer> mapMemoryAndInvocations = new ConcurrentHashMap<Integer, Integer>();
	SimpleDateFormat  dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss z");
	
	private static Logger logger = Logger.getLogger();
	private static Config config = Config.getConfig();
	private AWSServiceFactory factory = AWSServiceFactory.getFactory();
	private volatile float passedInvocation=0.0f, failedInvocation=0.0f, errorThreshold=0.02f;
	private double numInvocationMetricAcceptanceThresholdPercentage = 0.75d;
	
	private InvokeRequest invokeRequest = null;
	private UpdateFunctionConfigurationRequest updateConfigRequest = null;
	private Dimension dimension = null;
	
	private int periodSeconds = 60;
	private int waitPeriodSeconds = 60;

	/***********************************************MAIN-METHOD********************************************************************************/
	public static void main(String[] args) { 
		if (args.length > 0 && ARGUMENT_HELP.equalsIgnoreCase(args[0]))  {executeHelpSection(args); return;} 
		
		try { start(args);}finally {logger.closeFile();}
	}
	private static void start(String []args) {
		long startTime = System.currentTimeMillis();
		startupMessage();
		new AwsLdConCostCal().kickOff(args); 
		long milliseconds = System.currentTimeMillis()-startTime;
		long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
		logger.beginNewSection("[" + name + "] Execution Time Summary");
		logger.endNewSection("  Ran For Total Of [" + ( seconds < 60 ? seconds + " Seconds " 
				: seconds/60 +  " Mins & " + seconds%60 + " Seconds ") + "]");
		
	}
	public void kickOff(String[] args) {
		//Set Date Format To GMT
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		args = Util.toLowerCase(args);
		
		switch(args.length) {
		case 0: 
			if(!doAbort_IfCaptureConfiguration_Fail(args)) return;
			break;
		default:
			switch(args[0]) {
			case ARGUMENT_FILE:
				if (!loadConfiguration(args)) return;
			}
		}
		
		execute();	
	}
	
	/***********************************************PREP-AND-VALIDATION********************************************************************************/
	private boolean loadConfiguration(String [] args) {
		if(args.length < 2) {
			logger.print("Missing Config File/Path Argument. Plese Use " + ARGUMENT_FILE + " <<config.json>>");
			return false;
		}
		
		logger.beginNewSection("Begin Loading Configuration");
		if (!config.loadConfiguration(args[1])) return false;
		logger.endNewSection("End Loading Configuration");
		return true;
		
	}
	private boolean doAbort_IfCaptureConfiguration_Fail(String [] args) { 
		logger.beginNewSection("Begin Capturing Configuration");
		
		//Capture and Validate Input
		if (! config.captureAndValidateInput()) return false;
		
		logger.beginNewSubSection("Configuration Provided By User Are Below");
		config.printAllConfiguration();
		logger.endNewSubSection();
		
		if (config.confirmYesNo(CONFIRMATION_SAVE_CONFIGURATION_MSG)) config.saveConfiguration();
		
		logger.endNewSection("End Capturing Configuration");
		
		return true;
	}

	/************************************************EXECUTION SECTION*******************************************************************************/
	private void execute() {
		
		logger.beginNewSection("Begin Planned Execution Summary & Confirmation");
		summarizeExecution();
		if (!config.confirmYesNo(CONFIRMATION_CONTINUUE_MSG + LAMBDA_PERMISSION_MSG)) return;
		logger.endNewSection("End Planned Execution Summary & Confirmation");
		
		executeScenarios();
		
	}
	private boolean executeScenarios() {
		
		if(doAbort_IfPreliminaryTest_Fail()) return false;
		
		//Calculate #Invocation For Each Metric Collected Accepted As Valid
		Double numInvocationMetricAcceptanceThresholdNumber = numInvocationMetricAcceptanceThresholdPercentage * config.getNumberOfInvocationPerCycle();
		
		//Reset passed, failed Counters And Begin Execution For each Memory Size Configuration
		resetCounters();
		int cycleCounter = 0;
		for(int memorySize=config.getMinMemoryNumber(); memorySize<=config.getMaxMemoryNumber(); memorySize=memorySize+config.getIncrementMemoryNumber()) {
			
			logger.beginNewSection("Begin Execution For Memory [" + memorySize + "]");
			
			//Update Lambda Memory Configuration
			if(doAbort_IfUpdateMemoryAdjustment_Fail(memorySize)) return false;
			
			long nextSleepSeconds = 60-TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())%60;
			long startTime = System.currentTimeMillis() + (nextSleepSeconds*1000);
			if(nextSleepSeconds > 0) {
				logger.print("Need To Wait For Another [" + nextSleepSeconds + "] Seconds Before Begining Next Execution At [" +  
					formatToGMTDate(System.currentTimeMillis() + (nextSleepSeconds*1000))	+  "], To Avoid Time Overlapping For Metric Collection");
				sleep(nextSleepSeconds*1100);
			}
			
			//Reset Metric Collection Counter & Execute All Payloads
			if(doAbort_IfLambdaInvocation_AllPayloads_Fail(++cycleCounter)) return false;
			logger.print(METRIC_COLLECTION_SLEEP_MSG);
			sleepNinetySeconds();
			nextSleepSeconds = 60-TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())%60;
			long endTime = System.currentTimeMillis()+ (nextSleepSeconds*1000);
			
			if(doAbort_IfCollectLambdaMetricFails(memorySize, startTime, endTime, 1, numInvocationMetricAcceptanceThresholdNumber)) return false;
			logger.endNewSection("Ended Execution For Memory [" + memorySize + "]");
		}
		
		printFinalSummary();
		
		return true;
	}
	private String formatToGMTDate(long millisecond) { return dateFormat.format(new Date(millisecond)); }
	private boolean doAbort_IfPreliminaryTest_Fail() {
		logger.beginNewSection("Running-->Lambda Function Preliminary Tests");
		
		resetInvokeRequestWithDryRunInvocationType();
		logger.print("Test-->DryRun-->Invoking Lambda Function With Invocation Type [" + getInvokeRequest().getInvocationType() + "]");
		if(doAbort_IfLambdaInvocation_Fail(getInvokeRequest(), false)) return true;
		logger.print("\tPassed-->Test-->DryRun-->Invoking Lambda Function With Invocation Type [" + getInvokeRequest().getInvocationType() + "]");
		
		logger.print("Test-->Update Memory Configuration With Lowest Memory Configuration-->Set To [" + config.getMinMemoryNumber() + "]");
		if (doAbort_IfUpdateMemoryAdjustment_Fail(config.getMinMemoryNumber())) return true;
		logger.print("\tPassed-->Test-->Update Memory Configuration-->Set To [" + config.getMinMemoryNumber() + "]");
		
		if(config.getNumberOfPayloads() == 0) 
			logger.print("Skiping Payload Tests As No Payload Specified In The Configuration");
		else {
			getInvokeRequest().setInvocationType(config.getInvocationType());
			logger.print("Test-->Payload Test-->Invoking Lambda Function With Each Payload Using Invocation Type [" + getInvokeRequest().getInvocationType() + "]");
			for(int i=0; i<config.getNumberOfPayloads(); i++) {
				if(config.getPayloadBody(i) == null) {
					logger.printAbortMessage("Aborting Operation-->Internal Error-->No Body Found For Payload Number [" + i + "] From Payload Json File");
					return false;
				}
				getInvokeRequest().withPayload(ByteBuffer.wrap(config.getPayloadBody(i).getBytes()));
				if(doAbort_IfLambdaInvocation_Fail(getInvokeRequest(), false)) return true;
				logger.print("\tPassed-->Payload [" + i + "] Test");
			}
			
			logger.print("Passed-->All Payloads Test");
		}
		
		AmazonCloudWatch cloudWatchClient = factory.getAWSCloudWatchClient();
		long currentTime = System.currentTimeMillis();
		try { 
			logger.print("Test-->Collect Lambda Function Metrics From CloudWatch");
			GetMetricDataRequest metricRequest = getMetricDataRequestForAverageResponse("m" + currentTime, new Date(currentTime), new Date(currentTime+10000));
			cloudWatchClient.getMetricData(metricRequest);
			logger.print("\tPassed-->Collect Lambda Function Metrics From CloudWatch");
		}catch(AmazonCloudWatchException exception ) {
			logger.printAbortMessage("Aborting Operation-->AmazonCloudWatchException-->Collecting Metrics. Error [" + exception.getMessage() + "]");
			return true;
		}catch(Exception exception) {
			logger.printAbortMessage("Aborting Operation-->General Exception-->Collecting Metrics. Error [" + exception.getMessage() + "]");
			return true;
		}
		
		
		logger.endNewSection("Passed-->Lambda Function Preliminary Tests");
		return false;
	}
	/**To Execute This Operation, IAM User Must Have lambda:UpdateFunctionConfiguraton permission**/
	private boolean doAbort_IfUpdateMemoryAdjustment_Fail(int memorySize) {
		AWSLambda lambdaClient = factory.getAWSLambdaClient();
		getUpdateMemoryAdjustmentRequest().setMemorySize(memorySize);
		
		try { lambdaClient.updateFunctionConfiguration(updateConfigRequest);
		}catch(AWSLambdaException exception ) {
			logger.printAbortMessage("Aborting Operation-->AWSLambdaException-->Execute Update Function Configuration - Memory Adjustment [" + getUpdateMemoryAdjustmentRequest().getMemorySize() + "]. Error [" + exception.getMessage() + "]");
			return true;
		}catch(Exception exception) {
			logger.printAbortMessage("Aborting Operation-->GeneralException-->Execute Update Function Configuration - Memory Adjustment [" + getUpdateMemoryAdjustmentRequest().getMemorySize() + "]. Error [" + exception.getMessage() + "]");
			return true;
		}
		logger.print("Lambda Function Configuration Succesfully Updated With Memory [" + getUpdateMemoryAdjustmentRequest().getMemorySize() + "]");
		
		return false;
	}
	private boolean doAbort_IfLambdaInvocation_AllPayloads_Fail(int cycle) {
		long startTime = System.currentTimeMillis();
		logger.beginNewSubSection("Cycle [" +  cycle + "]-->Begin Invoking Lambda Functions For All Payloads At [" + formatToGMTDate(startTime) + "]");
		resetInvokeRequestWithRequestResponseInvocationType();
		
		int[] payloadSpread = config.getPayloadSpread();
		for(int payloadCounter=0; payloadCounter <payloadSpread.length; payloadCounter++) {
			if(payloadSpread[payloadCounter] == 0) continue;
			logger.print("\tBegin Invoking Lambda Function For Payload [" + payloadCounter + "], Total Execution Planned [" + payloadSpread[payloadCounter] + 
					"] With Mode [" + getInvokeRequest().getInvocationType() + "]");
			
			String payloadBody = config.getPayloadBody(payloadCounter);
			if(payloadBody == null) {
				logger.printAbortMessage("Aborting Operation-->Internal Error-->No Body Found For Payload Number [" + payloadCounter + "] From Payload Json File");
				return false;
			}
			getInvokeRequest().withPayload(ByteBuffer.wrap(payloadBody.getBytes()));
			for(int i=0; i<payloadSpread[payloadCounter]; i++)
				if(doAbort_IfLambdaInvocation_Fail(invokeRequest, true)) return true;
			
		}
		logger.endNewSubSection("Succesfully Completed Invoking Lambda Function For All Payloads With Mode [" + getInvokeRequest().getInvocationType() 
				+ "] At [" + formatToGMTDate(System.currentTimeMillis()) + "]");
		
		return false;
	}
	private boolean doAbort_IfCollectLambdaMetricFails(int memorySize, long startTime, long endTime, int attemptCounter, Double numInvocationMetricAcceptanceThresholdNumber) {
		AmazonCloudWatch cloudWatchClient = factory.getAWSCloudWatchClient();
		Date startDt =  new Date(startTime);
		Date endDt = new Date(endTime);
		
		String secondAttempMsg = attemptCounter == 1 ? "": "\tAttempt Number [" + attemptCounter + "]-->";
		logger.print(secondAttempMsg + "Begin Collecting Metrics With Start Time [" + dateFormat.format(startDt) + "] And End Time [" + dateFormat.format(endDt) + "]");
		
		try { 
			GetMetricDataRequest metricRequest = getMetricDataRequestForNumInvocation("m" + startTime, startDt, endDt);
			GetMetricDataResult result = cloudWatchClient.getMetricData(metricRequest);
			List<MetricDataResult> listResults = result.getMetricDataResults();
			
			Double invocations = Util.getMetricsData(listResults, KEY_SUM);
			if( invocations < numInvocationMetricAcceptanceThresholdNumber) {
				logger.print("Found #Invocation [" + invocations  + "] < #Accepted Threshold/Metric [" + numInvocationMetricAcceptanceThresholdNumber  +  "] During [" + attemptCounter + "] Attempt. Metric Data Found--> " + listResults);
				if(attemptCounter < 5)  {
					logger.print("Waiting For Another 60 Seconds For Metrics To Be Available In CloudWatch");
					sleep(waitPeriodSeconds);
					return doAbort_IfCollectLambdaMetricFails(memorySize, startTime, System.currentTimeMillis(), ++attemptCounter, numInvocationMetricAcceptanceThresholdNumber);
				}
				else 
					logger.printAbortMessage("Max Attempt Tried. Ignoring Metric Data Collection For Memory [" + memorySize + "] Execution");
					return false;
			}
			
			metricRequest = getMetricDataRequestForAverageResponse("m" + endTime, startDt, endDt);
			result = cloudWatchClient.getMetricData(metricRequest);
			listResults = result.getMetricDataResults();
			Double avgResponse = Util.getMetricsData(listResults, KEY_AVERAGE);
			logger.print("For Memory [" + memorySize + "], AVG(Duration-ms) = [" + round(avgResponse) + "], SUM(Invocations) = [" + 
					round(invocations) + "] And % Metric Data Available [" +  round(100*(invocations/config.getNumberOfInvocationPerCycle())) + "%]");
			if(avgResponse > 0.0d) {
				mapMemoryAndResponse.put(memorySize, round(avgResponse));
				mapMemoryAndInvocations.put(memorySize, round(invocations));
			} else logger.print("No Metric Found For AVG(Duration-ms), Hence Could Not Determine Metric AVG(Duration-ms) Data Points, Hence Ignoring Metric Data Collection For Memory [" + memorySize + "] Execution ");
			
		}catch(AmazonCloudWatchException exception ) {
			logger.printAbortMessage("Aborting Operation-->AmazonCloudWatchException-->Collecting Metrics. Error [" + exception.getMessage() + "]");
			return true;
		}catch(Exception exception) {
			logger.printAbortMessage("Aborting Operation-->General Exception-->Collecting Metrics. Error [" + exception.getMessage() + "]");
			return true;
		}
		
		return false;
	}
	private boolean doAbort_IfLambdaInvocation_Fail(InvokeRequest invokeRequest, boolean checkErrorRate) {
		try {  InvokeResult invokeResult = factory.getAWSLambdaClient().invoke(invokeRequest);
		
			switch (InvocationType.fromValue(invokeRequest.getInvocationType())) {
			case DryRun:
				return invokeResult.getStatusCode() == 204 ? false: true;
			case Event:
				if (invokeResult.getStatusCode() == 202) ++passedInvocation; else ++failedInvocation;
			case RequestResponse:
				if (invokeResult.getStatusCode() == 200) ++passedInvocation; else ++failedInvocation;
			}	
		}catch(AWSLambdaException exception ) {
			logger.printAbortMessage("Aborting Operation-->AWSLambdaException-->InvokeFunction-->InvocationType [" + invokeRequest.getInvocationType() + "]. Error [" + exception.getMessage() + "]");
			return true;
		}catch(Exception exception) {
			logger.printAbortMessage("Aborting Operation-->GeneralException-->InvokeFunctionInvocationType [" + invokeRequest.getInvocationType() + "]. Error [" + exception.getMessage() + "]");
			return true;
		}
		return checkErrorRate ? hasErrorRateBreached() : false;
	}
	private boolean hasErrorRateBreached() { return failedInvocation/(passedInvocation+failedInvocation) > errorThreshold ?  true: false;}
	private void printFinalSummary(){
		if(mapMemoryAndResponse.size() == 0) return;
		logger.beginNewSection("Begin Final Summary");
		String memory = "Memory-->\t\t||";
		String avgResponse = "Avg Response (ms)-->\t||";
		String invocation = "# Invocations-->\t||";
		String percMetricDataAvailable = "% Metric Available-->\t||";
		for(Map.Entry<Integer, Integer> entry: mapMemoryAndResponse.entrySet()) {
			memory += "\t" + entry.getKey() + "\t";
			avgResponse += "\t" + entry.getValue() + "\t";
			invocation += "\t" + mapMemoryAndInvocations.get(entry.getKey()) + "\t";
			percMetricDataAvailable += "\t" + round(100*(mapMemoryAndInvocations.get(entry.getKey())/ ((double)config.getNumberOfInvocationPerCycle()))) + "%\t";
		}
		logger.print("");
		logger.print(memory);
		logger.print(avgResponse);
		logger.print(invocation);
		logger.print(percMetricDataAvailable);
		logger.print("");
		logger.endNewSection("End Final Summary");
		
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
		logger.printBlankLine();
		logger.printForwardSlashLine();
		logger.print("Started [" + name + "], Build Version [" + buildVersion + "] At [" + new Date(System.currentTimeMillis())  + "]");
		logger.print("For Help, Use " + ARGUMENT_HELP + ", Or For More Details, Visit https://tools.brijeshsharma.com");
		logger.print("For Bug & Support, Please Send It To support@tools.brijeshsharma.com");
		logger.printForwardSlashLine();
		logger.printBlankLine();
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
	
	private void resetCounters() { passedInvocation = 0.0f; failedInvocation = 0.0f;}
	private void sleep(long millisecond) { try {Thread.sleep(millisecond);}catch(Exception e) {}}
	private void sleepNinetySeconds() { sleep(1000*waitPeriodSeconds);}
	
	/*******************************************************BUILDER & GETTER METHODS**********************************************************************************/	
	private InvokeRequest getInvokeRequest() {
		if(invokeRequest != null) return invokeRequest;
		return invokeRequest = new InvokeRequest().withFunctionName(config.getLambdaFunctionConfig());
	}
	private void resetInvokeRequestWithRequestResponseInvocationType() {
		invokeRequest = new InvokeRequest().withFunctionName(config.getLambdaFunctionConfig()).withInvocationType(config.getInvocationType());
	}
	private void resetInvokeRequestWithDryRunInvocationType() {
		invokeRequest = new InvokeRequest().withFunctionName(config.getLambdaFunctionConfig()).withInvocationType(InvocationType.DryRun);
	}
	private UpdateFunctionConfigurationRequest getUpdateMemoryAdjustmentRequest() {
		if (updateConfigRequest != null) return updateConfigRequest;
		return updateConfigRequest = new UpdateFunctionConfigurationRequest().withFunctionName(config.getLambdaFunctionConfig());
	}
	private Dimension getDimension() {
		if(dimension != null) return dimension;
		return dimension = new Dimension().withName("FunctionName").withValue(config.getLambdaFunctionConfig());
	}
	private Metric getMetric(String metricName) { return new Metric().withDimensions(getDimension()).withNamespace("AWS/Lambda").withMetricName(metricName); }
	private MetricStat getMetricStat(String metricName, String stat) { return  new MetricStat().withMetric(getMetric(metricName)).withStat(stat).withPeriod(getPeriod()); }
	private MetricDataQuery getMetricDataQuery(String id, String metricName, String stat) { return new MetricDataQuery().withMetricStat(getMetricStat(metricName, stat)).withId(id); }
	private GetMetricDataRequest getMetricDataRequestForAverageResponse(String id, Date startTime, Date endTime) {
		return new GetMetricDataRequest().withMetricDataQueries(getMetricDataQuery(id, "Duration", "Average")).withStartTime(startTime).withEndTime(endTime);
	}
	private GetMetricDataRequest getMetricDataRequestForNumInvocation(String id, Date startTime, Date endTime) {
		return new GetMetricDataRequest().withMetricDataQueries(getMetricDataQuery(id, "Invocations", "Sum")).withStartTime(startTime).withEndTime(endTime);
	}
	private int getPeriod() { return periodSeconds;}
	private int round(double value) { return (int)Math.round(value);}
}
