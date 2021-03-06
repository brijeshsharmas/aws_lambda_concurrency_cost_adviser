/**
 * @author Brijesh Sharma
 * Copyright (c) 2020, Brijesh Sharma 
 * All rights reserved.
 * This source code is licensed under the MIT license found in the LICENSE file in the root directory of this source tree. 
 */


package os.aws.lambda.cal.config;

public interface ConfigConstants {
	
	//Constants for default values
	public static int MIN_MEMORY_DEFAULT_VALUE = 128;
	public static int MAX_MEMORY_DEFAULT_VALUE = 1028;
	public static int INCREMENT_MEMORY_DEFAULT_VALUE = 128;
	public static int CODE_ERROR_FORMATTING_VALUE = 1212121;
	public static boolean LOG_MESSAGE_DEFAULT_VALUE = false;
	public static String DO_NOT_USE_VALUE = "NO";
	public static String YES_DEFAULT_VALUE = "YES";
	public static String USE_DEFAULT_VALUE = "USE_DEFAULT";
	public static String MIN_MAX_MEMORY_DEFAULT_VALUE = "128-1024-128";
	public static String MIN_MAX_DELIMETER="-";
	public static String CONFIG_FILE_NAME = "config.json";
	public static boolean INVOCATION_TYPE_DEFAULT = true;
	public static String TIMEZONE_DEFAULT = "UTC";
	public static int MAX_ATTEMPT_METRIC_COLLECTION_DEFAULT = 1;
	public static int PERIOD_METRIC_COLLECTION_DEFAULT = 60;
	public static int WAIT_INTERVAL_METRIC_COLLECTION_DEFAULT = 30;
	public static double METRIC_ACCEPTANCE_THRESHOLD_PERCENTAGE_DEFAULT = 0.75d;
	
	//Keys
	public static String KEY_AWS_ACCESS_KEY = "AWS_ACCESS_KEY";
	public static String KEY_AWS_ACCESS_SECRET_KEY = "AWS_ACCESS_SECRET_KEY";
	public static String KEY_PROXY_HOST = "PROXY_HOST";
	public static String KEY_PROXY_PORT = "PROXY_PORT";
	public static String KEY_LOG_MESSAGE = "LOG_MESSAGE";
	public static String KEY_LAMBDA_FUNCTION_NAME = "LAMBDA_FUNCTION_NAME";
	public static String KEY_MIN_MAX_MEMORY = "MIN_MAX_MEMORY";
	public static String KEY_NUM_INVOCATION = "NUM_INVOCATION";
	public static String KEY_JSON_PAYLOAD = "JSON_PAYLOAD";
	public static String KEY_SYNCH_INVOCATION_TYPE = "SYNCH_INVOCATION_TYPE";
	public static String KEY_AWS_REGION = "AWS_REGION";
	public static String KEY_SUM = "SUM";
	public static String KEY_AVERAGE = "AVERAGE";
	
	/****Message Constants**/
	public static String AWS_ACCESS_KEY_MSG = "Do You Want To Provide AWS_ACCESS_KEY. Please Specify \"No\" Without \"\" If You Want AWS To Use " +
												"Default Credential Provider Chain (VM Aruguments, Credential File, Environment Variable). " + 
												"We Strongly Recommed Using Default Credential Provider Chain";
	public static String AWS_ACCESS_SECRET_KEY_MSG = "Please Enter AWS_ACCESS_SECRET_KEY";
	public static String PROXY_HOST_MSG = "If You Are Using Proxy, Please Enter Proxy Host, Else Enter \"NO\" Without \"\"";
	public static String PROXY_PORT_MSG = "Please Enter Proxy Port";
	public static String LOG_MESSAGE_MSG = "Please Enter true/false If You Want To Log All Messages (Logs Will Be Written In calc.log In The Current Directory)";
	public static String LAMBDA_FUNCTION_MSG = "Please Provide Lambda Function Name Only. Please DO NOT Provide Fully Qualified Lambda ARN";
	public static String CONFIRMATION_CONTINUUE_MSG = "Do You Want To Continue (yes/no)";
	public static String CONFIRMATION_SAVE_CONFIGURATION_MSG = "Do You Want To Save (yes/no) These Configuration In A File For Later. You Can Use Saved Configuration Using Option --file <<file_path>> Argument";
	public static String MIN_MAX_MEMORY_LIST_MSG = "Valid Values Are [128, 192, 256, 320, 384, 448, 512, 576, 640, 704, 768, 832, 896, " + 
													"960, 1024, 1088, 1152, 1216, 1280, 1344, 1408, 1472, 1536, 1600, 1664, 1728, 1792, " + 
													"1856, 1920, 1984, 2048, 2112, 2176, 2240, 2304, 2368, 2432, 2496, 2560, 2624, 2688, " + 
													"2752, 2816, 2880, 2944, 3008]";
	public static String MIN_MAX_MEMORY_MSG = "Do You Want To Provide Memory (mb) Range In The Increment Of 64mb Starting With 128 mb. Please Specify \"NO\" Without \"\" If " + 
													"You Want To Use Default Range Of 128-1280-128 (Min=128, Max=1280 And Increment=128)";
	public static String NUM_INVOCATION_MSG = "For A Single Memory Adjustmet, Please Provide Number Of Invocation (recommended to set it 50) To Your Lambda Function Across All Payloads";
	public static String JSON_PAYLOAD_MSG = "Do You Want To Provide Lambda Json Payload (.json file) Location. Please specify \"NO\" Without \"\" If " + 
			"You Do Not Want To Send Payload With Your Lambda Invocation";
	
	public static String INVALID_MIN_MAX_INCREMENT_MSG = "Invalid Min-Max-Increment Values Specified. Valid Values Are a) All Values Must Be Number, b) Min-Max Valid Values Listed Above, " + 
														"c) Min Must Less Than Or Equal To Max, Increment Must Of In Order 64 mb. Please Try Again";
	public static String INVOCATION_TYPE_MSG = "Please Confim (true/false) If  Lambda Invocation Type Is SYNCHRONOUS, Enter false ASYNCHRONOUS";
	public static String AWS_REGION_MSG = "Please Enter AWS Region (such as us-east-1, eu-west-1, etc) Of Your Lambda Function";
	public static String METRIC_COLLECTION_SLEEP_MSG = "Going On Sleep For (%s) Seconds To Ensure Lambda Publishes Metrics To CloudWatch";
	public static String LAMBDA_PERMISSION_MSG = "\n  Please Make Sure That Referred IAM User/Role Has Following Permissions.\n  lambda:InvokeFunction, lambda:UpdateFunctionConfiguration, lambda:GetFunctionConfiguration, cloudwatch:GetMetricData";

	//Help Keys
	public static String ARGUMENT_HELP = "--help";
	public static String ARGUMENT_HELP_JSON = "--json";
	public static String ARGUMENT_HELP_VALIDATE = "--validate";
	public static String ARGUMENT_FILE = "--file";
	public static String KEY_WEIGHT = "Weight";
	public static String KEY_BODY = "Body";
	
}
