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
import static os.aws.lambda.cal.config.ConfigConstants.METRIC_COLLECTION_SLEEP_MSG;

import static os.aws.lambda.cal.util.Renderer.INT_STAR_LINE_WITH_MESSAGE;
import static os.aws.lambda.cal.util.Renderer.INT_UNDERSCORE_LINE;
import static os.aws.lambda.cal.util.Renderer.INT_BLANK_LINE;
import static os.aws.lambda.cal.util.Renderer.INT_DO_NOTHING;
import static os.aws.lambda.cal.util.Renderer.INT_MINUS_LINE;
import static os.aws.lambda.cal.util.Renderer.INT_FORWARD_LINE;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.DefaultRequest;
import com.amazonaws.metrics.RequestMetricCollector;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.AmazonCloudWatchException;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.DimensionFilter;
import com.amazonaws.services.cloudwatch.model.GetMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricDataResult;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import com.amazonaws.services.cloudwatch.model.ListMetricsResult;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.cloudwatch.model.MetricDataQuery;
import com.amazonaws.services.cloudwatch.model.MetricDataResult;
import com.amazonaws.services.cloudwatch.model.MetricStat;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.AWSLambdaException;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationResult;
import com.amazonaws.util.AWSRequestMetrics;


public class AwsLdConCostCal {
	
	private static String buildVersion = "1.0";
	private static String name = "Lambda Concurrency & Cost Optimizer";
	private ConcurrentHashMap<Integer, Double> mapMemoryAndResponse = new ConcurrentHashMap<Integer, Double>();
	private ConcurrentHashMap<Integer, Double> mapMemoryAndInvocations = new ConcurrentHashMap<Integer, Double>();
	
	private static Logger logger = Logger.getLogger();
	private static Renderer renderer = Renderer.getRenderer();
	private static Config config = Config.getConfig();
	private AWSServiceFactory factory = AWSServiceFactory.getFactory();
	private volatile float passedInvocation=0.0f, failedInvocation=0.0f, errorThreshold=0.02f;
	
	private InvokeRequest invokeRequest = null;
	private UpdateFunctionConfigurationRequest updateConfigRequest = null;
	private Dimension dimension = null;

	/***********************************************MAIN-METHOD********************************************************************************/
	public static void main(String[] args) { 
		if (args.length > 0 && ARGUMENT_HELP.equalsIgnoreCase(args[0]))  {executeHelpSection(args); return;} 
		start(args);
	}
	private static void start(String []args) {
		startupMessage();
		new AwsLdConCostCal().kickOff(args); 
	}
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
	
	/***********************************************PREP-AND-VALIDATION********************************************************************************/
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

	/************************************************EXECUTION SECTION*******************************************************************************/
	private void execute(int[] renderArgs) {
		renderArgs[0]=INT_DO_NOTHING; renderArgs[1]=INT_DO_NOTHING; renderArgs[2] = INT_STAR_LINE_WITH_MESSAGE;
		renderer.printLine(renderArgs, "Begin Executing Configuration");
		
		summarizeExecution();
		renderArgs[2] = INT_MINUS_LINE;
		renderer.printLine(renderArgs, "");
		
		//if (!config.confirmYesNo(CONFIRMATION_CONTINUUE_MSG)) return;
		
		executeScenarios(renderArgs);
		
	}
	private boolean executeScenarios(int[] renderArgs) {
		renderArgs[0]=INT_UNDERSCORE_LINE; renderArgs[1]=INT_STAR_LINE_WITH_MESSAGE; renderArgs[2] = INT_DO_NOTHING;
		//if(doAbort_IfPreliminaryTest_Fail(renderArgs)) return false;
		
		//Reset passed, failed Counters And Begin Execution For each Memory Size Configuration
		resetCounters();
		for(int memorySize=config.getMinMemoryNumber(); memorySize<=config.getMaxMemoryNumber(); memorySize=memorySize+config.getIncrementMemoryNumber()) {
			renderArgs[0]=INT_UNDERSCORE_LINE; renderArgs[1]=INT_STAR_LINE_WITH_MESSAGE; 
			renderer.printLine(renderArgs, "Begin Execution For Memory [" + memorySize + "]");
			
			//Update Lambda Memory Configuration
			if(doAbort_IfUpdateMemoryAdjustment_Fail(memorySize, renderArgs)) return false;
			
			//Reset Metric Collection Counter & Execute All Payloads
			long startTime = System.currentTimeMillis();
			if(doAbort_IfLambdaInvocation_AllPayloads_Fail(renderArgs)) return false;
			logger.print(METRIC_COLLECTION_SLEEP_MSG);
			sleepSeventySeconds();
			long endTime = System.currentTimeMillis();
			
			if(doAbort_IfLambdaMetricCollection_Fail(memorySize, startTime, endTime, true)) return false;
			
			renderArgs[0]=INT_STAR_LINE_WITH_MESSAGE; renderArgs[1]=INT_UNDERSCORE_LINE;
			renderer.printLine(renderArgs, "Ended Execution For Memory [" + memorySize + "]");
		}
		
		
		return true;
	}
	private boolean doAbort_IfPreliminaryTest_Fail(int[] renderArgs) {
		renderer.printLine(renderArgs, "Running-->Lambda Function Preliminary Tests");
		
		logger.print("Test-->DryRun-->Invoking Lambda Function With Invocation Type [" + InvocationType.DryRun + "]");
		resetInvokeRequestWithDryRunInvocationType();
		if(doAbort_IfLambdaInvocation_Fail(getInvokeRequest(), renderArgs, false)) return true;
		logger.print("Passed-->Test-->DryRun-->Invoking Lambda Function With Invocation Type [" + getInvokeRequest().getInvocationType() + "]");
		
		logger.print("Test-->Update Memory Configuration-->Set To 128");
		if (doAbort_IfUpdateMemoryAdjustment_Fail(128, renderArgs)) return true;
		logger.print("Passed-->Test-->Update Memory Configuration-->Set To 128");
		
		if(config.getNumberOfPayloads() == 0) 
			logger.print("Skiping Payload Tests As No Payload Specified In The Configuration");
		else {
			getInvokeRequest().setInvocationType(InvocationType.RequestResponse);
			logger.print("Test-->Payload Test-->Invoking Lambda Function With Each Payload Using Invocation Type [" + getInvokeRequest().getInvocationType() + "]");
			for(int i=0; i<config.getNumberOfPayloads(); i++) {
				if(config.getPayloadBody(i) == null) {
					logger.printAbortMessage("Aborting Operation-->Internal Error-->No Body Found For Payload Number [" + i + "] From Payload Json File");
					return false;
				}
				getInvokeRequest().withPayload(ByteBuffer.wrap(config.getPayloadBody(i).getBytes()));
				if(doAbort_IfLambdaInvocation_Fail(getInvokeRequest(), renderArgs, false)) return true;
				logger.print("Passed-->Payload [" + i + "] Test");
			}
			
			logger.print("Passed-->All Payloads Test");
		}
		
		//TO-DO Add test for Cloudwatch metrics
		
		renderArgs[1]=INT_UNDERSCORE_LINE; renderArgs[0]=INT_STAR_LINE_WITH_MESSAGE;
		renderer.printLine(renderArgs, "Passed-->Lambda Function Preliminary Tests");
		return false;
	}
	/**To Execute This Operation, IAM User Must Have lambda:UpdateFunctionConfiguraton permission**/
	private boolean doAbort_IfUpdateMemoryAdjustment_Fail(int memorySize, int[] renderArgs) {
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
	private boolean doAbort_IfLambdaInvocation_AllPayloads_Fail(int [] renderArgs) {
		resetInvokeRequestWithRequestResponseInvocationType();
		
		int[] payloadSpread = config.getPayloadSpread();
		for(int payloadCounter=0; payloadCounter <payloadSpread.length; payloadCounter++) {
			if(payloadSpread[payloadCounter] == 0) continue;
			logger.print("Begin Invoking Lambda Function For Payload [" + payloadCounter + "], Total Execution Planned [" + payloadSpread[payloadCounter] + 
					"] With Mode [" + getInvokeRequest().getInvocationType() + "]");
			
			String payloadBody = config.getPayloadBody(payloadCounter);
			if(payloadBody == null) {
				logger.printAbortMessage("Aborting Operation-->Internal Error-->No Body Found For Payload Number [" + payloadCounter + "] From Payload Json File");
				return false;
			}
			getInvokeRequest().withPayload(ByteBuffer.wrap(payloadBody.getBytes()));
			for(int i=0; i<payloadSpread[payloadCounter]; i++)
				if(doAbort_IfLambdaInvocation_Fail(invokeRequest, renderArgs, true)) return true;
			
		}
		logger.print("Succesfully Invoked Lambda Function For All Payloads With Mode [" + getInvokeRequest().getInvocationType() + "]");
		
		return false;
	}
	private boolean doAbort_IfLambdaMetricCollection_Fail(int memorySize, long startTime, long endTime, boolean firstAttemp) {
		AmazonCloudWatch cloudWatchClient = factory.getAWSCloudWatchClient();
		Date startDt = new Date(startTime);
		Date endDt = new Date(endTime);
		
		String secondAttempMsg = firstAttemp ? "": "One More Attempt With New End Time-->";
		logger.print(secondAttempMsg + "Begin Collecting Metrics With Start Time [" + startDt + "] And End Time [" + endDt + "]");
		
		try { 
			GetMetricDataRequest metricRequest = getMetricDataRequestForAverageResponse("m" + startTime, startDt, endDt);
			GetMetricDataResult result = cloudWatchClient.getMetricData(metricRequest);
			List<MetricDataResult> listResults = result.getMetricDataResults();
			//logger.print("Metric AVG(Duration) Found [" + listResults + "]");
			if(listResults == null || listResults.size() == 0 || listResults.get(0).getValues() == null || listResults.get(0).getValues().size() == 0) {
				if(firstAttemp)  {
					logger.print("No Metric Found For AVG(Duration) During First Attempt. Waiting For Another 30 Seconds For Metrics To Be Available In CloudWatch");
					sleep(30000);
					return doAbort_IfLambdaMetricCollection_Fail(memorySize, startTime, System.currentTimeMillis(), false);
				}
				else {
					logger.printAbortMessage("Aborting Operation-->No Metric Found For AVG(Duration) After Second Attempt");
					return true;
				}
			}
			
			Double avgResponse = listResults.get(0).getValues().get(0);
			Double invocations = 0.0d;
			mapMemoryAndResponse.put(memorySize, avgResponse);
			
			metricRequest = getMetricDataRequestForNumInvocation("m" + endTime, startDt, endDt);
			result = cloudWatchClient.getMetricData(metricRequest);
			listResults = result.getMetricDataResults();
			if(listResults == null || listResults.size() == 0 || listResults.get(0).getValues() == null || listResults.get(0).getValues().size() == 0) 
				logger.print("No Metric Found For SUM(Invocations), Hence Could Not Determine Metric AVG(Duration) Data Points");
			else {
				invocations = listResults.get(0).getValues().get(0);
				mapMemoryAndInvocations.put(memorySize, invocations);
			}
			
			logger.print("For Memory [" + memorySize + "], AVG(Duration) = [" + avgResponse + "], SUM(Invocations) = [" + invocations + "]");
			
		}catch(AmazonCloudWatchException exception ) {
			logger.printAbortMessage("Aborting Operation-->AmazonCloudWatchException-->Collecting Metrics. Error [" + exception.getMessage() + "]");
			return true;
		}catch(Exception exception) {
			logger.printAbortMessage("Aborting Operation-->AmazonCloudWatchException-->Collecting Metrics. Error [" + exception.getMessage() + "]");
			return true;
		}
		
		return false;
	}
	private boolean doAbort_IfLambdaInvocation_Fail(InvokeRequest invokeRequest, int[] renderArgs, boolean checkErrorRate) {
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
	private boolean hasErrorRateBreached() { return failedInvocation/(passedInvocation+failedInvocation) > errorThreshold ?  true: false;
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
		logger.print("Started [" + name + "] Build Version At [" + buildVersion + "] At [" + new Date(System.currentTimeMillis())  + "]");
		logger.print("For Help, Use " + ARGUMENT_HELP + ", Or For More Details, Visit https://tools.brijeshsharma.com");
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
	private void sleepSeventySeconds() { sleep(1000*70);}
	
	/*******************************************************BUILDER & GETTER METHODS**********************************************************************************/	
	private InvokeRequest getInvokeRequest() {
		if(invokeRequest != null) return invokeRequest;
		return invokeRequest = new InvokeRequest().withFunctionName(config.getLambdaFunctionConfig());
	}
	private void resetInvokeRequestWithRequestResponseInvocationType() {
		invokeRequest = new InvokeRequest().withFunctionName(config.getLambdaFunctionConfig()).withInvocationType(InvocationType.RequestResponse);
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
	private int getPeriod() { return 60;}
}
