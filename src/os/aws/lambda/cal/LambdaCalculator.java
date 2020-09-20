/**
 * @author Brijesh Sharma
 *
 */

package os.aws.lambda.cal;

import os.aws.lambda.cal.config.Config;
import os.aws.lambda.cal.modal.JsonPayload;
import os.aws.lambda.cal.service.AWSServiceFactory;
import os.aws.lambda.cal.service.Logger;
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
import static os.aws.lambda.cal.config.ConfigConstants.MAX_ATTEMPT_METRIC_COLLECTION_DEFAULT;
import static os.aws.lambda.cal.config.ConfigConstants.PERIOD_METRIC_COLLECTION_DEFAULT;
import static os.aws.lambda.cal.config.ConfigConstants.WAIT_INTERVAL_METRIC_COLLECTION_DEFAULT;
import static os.aws.lambda.cal.config.ConfigConstants.METRIC_ACCEPTANCE_THRESHOLD_PERCENTAGE_DEFAULT;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
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
import com.amazonaws.services.lambda.model.GetFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.GetFunctionConfigurationResult;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;


public class LambdaCalculator {
	
	private static String buildVersion = "1.0";
	private static String name = "Lambda Concurrency & Cost Optimizer Tool";
	private TreeMap<Integer, Integer> mapMemoryAndResponse = new TreeMap<Integer, Integer>();
	private TreeMap<Integer, Integer> mapMemoryAndInvocations = new TreeMap<Integer, Integer>();
	private TreeMap<Integer, List<Long>> mapTimeSeries = new TreeMap<Integer, List<Long>>();
	
	private static Logger logger = Logger.getLogger();
	private static Config config = Config.getConfig();
	private AWSServiceFactory factory = AWSServiceFactory.getFactory();
	private volatile float passedInvocation=0.0f, failedInvocation=0.0f, errorThreshold=0.02f;
	private int functionOriginalMemorySize = 0;
	
	private InvokeRequest invokeRequest = null;
	private UpdateFunctionConfigurationRequest updateConfigRequest = null;
	private Dimension dimension = null;

	/***********************************************MAIN-METHOD********************************************************************************/
	public static void main(String[] args) { 
		
		if (args.length > 0 && ARGUMENT_HELP.equalsIgnoreCase(args[0]))  {executeHelpSection(args); return;} 
		
		try { start(args);}finally {logger.closeFile();}
	}
	private static void start(String []args) {
		long startTime = System.currentTimeMillis();
		startupMessage();
		new LambdaCalculator().kickOff(args); 
		long milliseconds = System.currentTimeMillis()-startTime;
		long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
		logger.beginNewSection("[" + name + "] Execution Time Summary");
		logger.endNewSection("  Ran For Total Of [" + ( seconds < 60 ? seconds + " Seconds " 
				: seconds/60 +  " Mins & " + seconds%60 + " Seconds ") + "]");
		
	}
	public void kickOff(String[] args) {
		
		args = Util.toLowerCase(args);
		
		switch(args.length) {
		case 0: 
			if(!doAbort_IfCaptureConfiguration_Fail(args)) return;
			break;
		default:
			switch(args[0]) {
			case ARGUMENT_FILE:
				if (!loadConfiguration(args)) return;
				break;
			default:
				System.out.println(args[0] + " Is Not A Valid Option");
				return;
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
		
		if(functionOriginalMemorySize > 0)  {
			logger.beginNewSection("Begin - Reseting Function Memory To Original Function Memory [" + functionOriginalMemorySize + "]");
			doAbort_IfUpdateMemoryAdjustment_Fail(functionOriginalMemorySize);
			logger.endNewSection("End - Reseting Function Memory To Original Function Memory [" + functionOriginalMemorySize + "]");
		}
		
	}
	private boolean executeScenarios() {
		
		logger.beginNewSection("Begin - Getting Function Current Memory Configuration");
		functionOriginalMemorySize = getFunctionMemoryconfig();
		logger.endNewSection("Begin - Getting Function Current Memory Configuration");
		if(functionOriginalMemorySize == -1) return false;
		
		if(doAbort_IfPreliminaryTest_Fail()) return false;
		
		//Calculate #Invocation For Each Metric Collected Accepted As Valid
		Double numInvocationMetricAcceptanceThresholdNumber =  METRIC_ACCEPTANCE_THRESHOLD_PERCENTAGE_DEFAULT * config.getNumberOfInvocationPerCycle();
		
		//Reset passed, failed Counters And Begin Execution For each Memory Size Configuration
		resetCounters();
		int cycleCounter = 0;
		for(int memorySize=config.getMinMemoryNumber(); memorySize<=config.getMaxMemoryNumber(); memorySize=memorySize+config.getIncrementMemoryNumber()) {
			
			logger.beginNewSection("Begin Execution For Memory [" + memorySize + "]");
			
			//Update Lambda Memory Configuration
			if(doAbort_IfUpdateMemoryAdjustment_Fail(memorySize)) return false;
			
			//Wait For Next Few Seconds To Ensure Start Time Is Nearest To a Minute Such As [HH:MM:00]
			long nextSleepSeconds = Util.getSecondNearestToMinute();
			long startTime = Util.getMilliNearestToMinute();
			if(nextSleepSeconds > 0) {
				logger.print("Need To Wait For Another [" + (nextSleepSeconds+30) + "] Seconds Before Begining Next Execution Cycle Period Starting [" +  
					Util.formatToUTCDate(startTime)	+  "], To Avoid Time Overlapping For Metric Collection");
				sleep(Util.convertSecondToMilli(nextSleepSeconds+30));
			}
			
			//Reset Metric Collection Counter & Execute All Payloads
			if(doAbort_IfLambdaInvocation_AllPayloads_Fail(++cycleCounter)) return false;
			
			//Similarly Wait And Evaluate For Next Few Seconds To Ensure End Time Is Nearest To a Minute Such As [HH:MM:00]
			nextSleepSeconds = Util.getSecondNearestToMinute();
			long endTime = Util.getMilliNearestToMinute();
			logger.print(String.format(METRIC_COLLECTION_SLEEP_MSG, nextSleepSeconds+30));
			sleep(Util.convertSecondToMilli(nextSleepSeconds+30)); //Sleep Extra 30 Seconds Allow Lambda To Publish Metrics
			
			if(doAbort_IfCollectLambdaMetricFails(memorySize, startTime, endTime, 
					numInvocationMetricAcceptanceThresholdNumber, mapMemoryAndInvocations, mapMemoryAndResponse, mapTimeSeries)) return false;
			logger.endNewSection("Ended Execution For Memory [" + memorySize + "]");
		}
		
		reComputeIfNecessary();
		printFinalSummary();
		
		return true;
	}
	private boolean doAbort_IfCollectLambdaMetricFails(int memorySize, long startTime, long endTime, 
			Double numInvocationMetricAcceptanceThresholdNumber, Map<Integer, Integer> mapMemInvocation, Map<Integer, Integer> mapMemAvgResponse, 
			Map<Integer, List<Long>> mapArgTimeSeries) {
		AmazonCloudWatch cloudWatchClient = factory.getAWSCloudWatchClient();
		Date startDt =  new Date(startTime);
		
		//Five Attempts With Every 30 Seconds Interval
		for(int i=0; i<MAX_ATTEMPT_METRIC_COLLECTION_DEFAULT; i++) {
			Date endDt = new Date(endTime);
			
			logger.print("Attempt Number[" + (i+1)  + "]-->Begin Collecting Metrics With Start Time [" + Util.formatToUTCDate(startTime) 
				+ "] And End Time [" + Util.formatToUTCDate(endTime) + "]");
			
			try { 
				GetMetricDataRequest metricRequest = getMetricDataRequestForNumInvocation("m" + endTime, startDt, endDt);
				GetMetricDataResult result = cloudWatchClient.getMetricData(metricRequest);
				List<MetricDataResult> listResults = result.getMetricDataResults();
				
				Double invocations = Util.getMetricsData(listResults, KEY_SUM);
				if( invocations < numInvocationMetricAcceptanceThresholdNumber) {
					logger.print("Found #Invocation [" + invocations  + "] < #Accepted Threshold/Metric [" + numInvocationMetricAcceptanceThresholdNumber  +  "]");
					
					logger.print("Waiting For Another [" + WAIT_INTERVAL_METRIC_COLLECTION_DEFAULT +  "] Seconds For Metrics To Be Available In CloudWatch");
					sleep(WAIT_INTERVAL_METRIC_COLLECTION_DEFAULT*1000);
					//endTime = Util.getMilliNearestToMinute();
					if (i < (MAX_ATTEMPT_METRIC_COLLECTION_DEFAULT-1)) continue;
				}
				
				metricRequest = getMetricDataRequestForAverageResponse("m" + startTime, startDt, endDt);
				result = cloudWatchClient.getMetricData(metricRequest);
				listResults = result.getMetricDataResults();
				Double avgResponse = Util.getMetricsData(listResults, KEY_AVERAGE);
				
				logger.print("For Memory [" + memorySize + "], AVG(Duration-ms) = [" + round(avgResponse) + "], SUM(Invocations) = [" + 
						round(invocations) + "] And % Metric Data Available [" +  round(100*(invocations/config.getNumberOfInvocationPerCycle())) + "%]");
				
				if(mapMemAvgResponse != null) mapMemAvgResponse.put(memorySize, round(avgResponse));
				if(mapMemInvocation != null) mapMemInvocation.put(memorySize, round(invocations));
				if(mapArgTimeSeries != null) mapArgTimeSeries.put(memorySize, Arrays.asList(startTime, endTime));
				return false;
				
			}catch(AmazonCloudWatchException exception ) {
				logger.printAbortMessage("Aborting Operation-->AmazonCloudWatchException-->Collecting Metrics. Error [" + exception.getMessage() + "]");
				return true;
			}catch(Exception exception) {
				logger.printAbortMessage("Aborting Operation-->General Exception-->Collecting Metrics. Error [" + exception.getMessage() + "]");
				return true;
			}
		}
		return false;
	}
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
	/**To Execute This Operation, IAM User Must Have lambda:UpdateFunctionConfiguraton permission**/
	private int getFunctionMemoryconfig() {
		AWSLambda lambdaClient = factory.getAWSLambdaClient();
		GetFunctionConfigurationRequest request = getFunctionConfigurationRequest();
		
		try { 
			GetFunctionConfigurationResult result= lambdaClient.getFunctionConfiguration(request);
			int memorySize = result.getMemorySize();
			logger.print("Lambda Function Configuratio-->Memory Size--> [" + memorySize + "]");
			return memorySize;
		}catch(AWSLambdaException exception ) {
			logger.printAbortMessage("Aborting Operation-->AWSLambdaException-->Execute Get Function Configuration. Error [" + exception.getMessage() + "]");
			return -1;
		}catch(Exception exception) {
			logger.printAbortMessage("Aborting Operation-->GeneralException-->Execute Get Function Configuration. Error [" + exception.getMessage() + "]");
			return -1;
		}
	}
	private boolean doAbort_IfLambdaInvocation_AllPayloads_Fail(int cycle) {
		long startTime = System.currentTimeMillis();
		logger.beginNewSubSection("Cycle [" +  cycle + "]-->Begin Invoking Lambda Functions For All Payloads At Current Time [" + Util.formatToUTCDate(startTime) + "]");
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
				+ "] At [" + Util.formatToUTCDate(System.currentTimeMillis()) + "]");
		
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
	
	private void reComputeIfNecessary(){
		logger.beginNewSection("Begin Reconciling & Recomputing If Necessary");
		
		logger.beginNewSubSection("*****************Metric Collection Time-Series********************");
		logger.print("Memory\t\t\tStart Period\t\t\t\tEnd Period");
		for(Map.Entry<Integer, List<Long>> entry: mapTimeSeries.entrySet()) 
			logger.print(entry.getKey() + " " + "\t\t\t" + Util.formatToUTCDate(entry.getValue().get(0)) + "\t\t" +  Util.formatToUTCDate(entry.getValue().get(1)));
		
		logger.endNewSubSection();
		
		long startTime = Util.getLowestTimeSeries(mapTimeSeries);
		long endTime = Util.getHighestTimeSeries(mapTimeSeries);
		logger.print("Begin Collecting Metric For Entire Duration--> Start Time [" + Util.formatToUTCDate(startTime) + 
				"], End Time [" + Util.formatToUTCDate(endTime) + "]");
		
		List<MetricDataResult> listResults = getMetricData(KEY_SUM, startTime, endTime);
		Double invocations = Util.getMetricsData(listResults, KEY_SUM);
		int existingInvocation = Util.sumValue(mapMemoryAndInvocations);
		
		if(invocations != existingInvocation) {
			logger.print("Old #Invocation [" + existingInvocation + "] Do Not Match With Current #Invocation [" + invocations + "], Hence Recomputing");
			reCompute();
		} else 
			logger.print("Old #Invocation [" + existingInvocation + "] Match With Current #Invocation [" + invocations + "], Hence Skipping Recomputing");
			
		logger.endNewSection("End Reconciling & Recomputing If Necessary");
	}
	private void reCompute() {
		logger.beginNewSubSection("Begin ReComputing Metric");
		
		for(Map.Entry<Integer, Integer> entry: mapMemoryAndInvocations.entrySet()) {
			if(entry.getValue() == config.getNumberOfInvocationPerCycle()) continue;
			
			List<Long> listSeries = mapTimeSeries.get(entry.getKey());
			if(listSeries == null || listSeries.size() != 2) {
				logger.printAbortMessage("Internal Error. Please Report Bug With Subject Message [Missing TimeSeries For A Given Memory Size]");
				continue;
			}
			
			long startTime = listSeries.get(0);
			long endTime = listSeries.get(1);
			logger.print("Begin Collecting Metrics Again For Memory [" + entry.getKey() + "], And Time Series Start[" + 
					Util.formatToUTCDate(startTime) + "], End[" +  Util.formatToUTCDate(endTime) + "]");
			
			List<MetricDataResult> listResults = getMetricData(KEY_SUM, startTime, endTime);
			Double invocations = Util.getMetricsData(listResults, KEY_SUM);
			mapMemoryAndInvocations.put(entry.getKey(), round(invocations));
			listResults = getMetricData(KEY_AVERAGE, startTime, endTime);
			Double response = Util.getMetricsData(listResults, KEY_AVERAGE);
			mapMemoryAndResponse.put(entry.getKey(), round(response));
			logger.print("Metrics Reset For For Memory [" + entry.getKey() + "] As SUM(Invocation)[" + round(invocations) 
				+ "], AVG(Response)[" +  round(response)  + "]");
		}
		
		logger.endNewSubSection("End ReComputing Metric");
		
	}
	private List<MetricDataResult> getMetricData(String stat, long startTime, long endTime) {
		AmazonCloudWatch cloudWatchClient = factory.getAWSCloudWatchClient();
		try { 
			GetMetricDataRequest metricRequest = null;
			if (stat.equalsIgnoreCase(KEY_SUM)) metricRequest = getMetricDataRequestForNumInvocation("m" + endTime, new Date(startTime), new Date(endTime));
			else metricRequest = getMetricDataRequestForAverageResponse("m" + endTime, new Date(startTime), new Date(endTime));
			GetMetricDataResult result = cloudWatchClient.getMetricData(metricRequest);
			return result.getMetricDataResults();
		}catch(Exception exception) {
			logger.printAbortMessage("General Exception-->Collecting Metrics. Error [" + exception.getMessage() + "]");
			return null;
		}
	}
	
	
	private void printFinalSummary(){
		if(mapMemoryAndResponse.size() == 0) return;
		logger.beginNewSection("Begin Final Summary");
		String memory = "Memory-->\t\t||";
		String avgResponse = "Avg Response (ms)-->\t||";
		String invocation = "# Invocations-->\t||";
		String percMetricDataAvailable = "% Metric Available-->\t||";
		String costVariation = "% Var Cost-->\t\t||";
		String memVariation = "% Var Memory-->\t||";
		String avgResponseVariation = "% Var Avg-Res-->\t||";
		double tenMil = 1.0d;
		double baseCost = 1.0d, baseResponse = 1.0;
		for(Map.Entry<Integer, Integer> entry: mapMemoryAndResponse.entrySet()) {
			memory += "\t" + entry.getKey() + "\t";
			avgResponse += "\t" + entry.getValue() + "\t";
			invocation += "\t" + mapMemoryAndInvocations.get(entry.getKey()) + "\t";
			percMetricDataAvailable += "\t" + round(100*(mapMemoryAndInvocations.get(entry.getKey())/ ((double)config.getNumberOfInvocationPerCycle()))) + "%\t";
			if(entry.getKey() == config.getMinMemoryNumber()) {
				costVariation += "\t(Base)\t";
				memVariation += "\t(Base)\t";
				avgResponseVariation += "\t(Base)\t";
				baseCost = entry.getKey() * Util.nearestHunread(entry.getValue());
				baseCost = baseCost / 1024 * tenMil;
				baseResponse = entry.getValue();
			} else {
				double cost = tenMil * ((double)(entry.getKey() * Util.nearestHunread(entry.getValue())))/1024;
				costVariation += "\t" + round((100*((cost-baseCost)/baseCost))) + "%\t";
				memVariation += "\t" + round((100*((entry.getKey()-config.getMinMemoryNumber())/config.getMinMemoryNumber()))) + "%\t";
				avgResponseVariation += "\t" + round((100*((entry.getValue()-baseResponse)/baseResponse))) + "%\t";
			}
		}
		logger.print("");
		logger.print(memory);
		logger.print(avgResponse);
		logger.print(invocation);
		logger.print(percMetricDataAvailable);
		logger.printMinusLine();
		logger.print(memVariation);
		logger.print(avgResponseVariation);
		logger.print(costVariation);
		logger.print("");
		logger.endNewSection("End Final Summary");
		
	}
	private void dummySet() {
		Random random = new Random();
		List<Integer> listResponse = new ArrayList<Integer>();
		listResponse.add(2748);
		listResponse.add(1376);
		listResponse.add(920);
		listResponse.add(693);
		listResponse.add(548);
		listResponse.add(449);
		listResponse.add(392);
		listResponse.add(338);
		listResponse.add(307);
		listResponse.add(271);
		listResponse.add(2748);
		listResponse.add(2748);
		
		int j=0;
		for (int i=128; i <= 1280; i=i+128) {
			mapMemoryAndInvocations.put(i, random.nextInt(100));
			mapMemoryAndResponse.put(i, listResponse.get(j++));
			mapTimeSeries.put(i, Arrays.asList(System.currentTimeMillis(), System.currentTimeMillis()));
		}
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
		logger.print("For Bug & Support, Please Send It To tools-support@brijeshsharma.com");
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
	private GetFunctionConfigurationRequest getFunctionConfigurationRequest() {
		return new GetFunctionConfigurationRequest().withFunctionName(config.getLambdaFunctionConfig());
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
	private int getPeriod() { return PERIOD_METRIC_COLLECTION_DEFAULT;}
	private int round(double value) { return (int)Math.round(value);}
}
