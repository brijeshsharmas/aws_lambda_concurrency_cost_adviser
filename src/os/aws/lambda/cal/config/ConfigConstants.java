/**
 * 
 */
package os.aws.lambda.cal.config;

/**
 * @author Brijesh Sharma
 *
 */
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
	public static String MIN_MAX_MEMORY_DEFAULT_VALUE = "128-1028-128";
	
	//Keys
	public static String KEY_AWS_ACCESS_KEY = "AWS_ACCESS_KEY";
	public static String KEY_AWS_ACCESS_SECRET_KEY = "AWS_ACCESS_SECRET_KEY";
	public static String KEY_PROXY_HOST = "PROXY_HOST";
	public static String KEY_PROXY_PORT = "PROXY_PORT";
	public static String KEY_LOG_MESSAGE = "LOG_MESSAGE";
	public static String KEY_LAMBDA_ARN = "LAMBDA_ARN";
	public static String KEY_MIN_MAX_MEMORY = "MIN_MAX_MEMORY";
	public static String KEY_MIN_MEMORY = "MIN_MEMORY";
	public static String KEY_MAX_MEMORY = "MAX_MEMORY";
	public static String KEY_INCREMENT_MEMORY = "INCREMENT_MEMORY";
	public static String KEY_NUM_TOTAL_INVOCATION = "NUM_TOTAL_INVOCATION";
	public static String KEY_JSON_PAYLOAD = "JSON_PAYLOAD";
	
	/****Message Constants**/
	public static String AWS_ACCESS_KEY_MSG = "Do You Want To Provide AWS_ACCESS_KEY. Please Specify \"No\" Without \"\" If You Want AWS To Use " +
												"Default Credential Provider Chain (VM Aruguments, Credential File, Environment Variable)";
	public static String AWS_ACCESS_SECRET_KEY_MSG = "Please enter your AWS_ACCESS_SECRET_KEY";
	public static String PROXY_HOST_MSG = "If You Are Using Proxy, Please Enter Proxy Host, Else Enter \"NO\" Without \"\"";
	public static String PROXY_PORT_MSG = "Please Enter Proxy Port";
	public static String LOG_MESSAGE_MSG = "Please Enter true/false If You Want To Log All Messages (Logs Will Be Written In calc.log In The Current Directory)";
	public static String LAMBDA_ARN_MSG = "Please Provide Fully Qualified Lambda ARN";
	public static String CONFIRMATION_MSG = "Do You Want To Continue (yes/no)";
	public static String MIN_MAX_MEMORY_LIST_MSG = "Valid Values Are [128, 192, 256, 320, 384, 448, 512, 576, 640, 704, 768, 832, 896, " + 
													"960, 1024, 1088, 1152, 1216, 1280, 1344, 1408, 1472, 1536, 1600, 1664, 1728, 1792, " + 
													"1856, 1920, 1984, 2048, 2112, 2176, 2240, 2304, 2368, 2432, 2496, 2560, 2624, 2688, " + 
													"2752, 2816, 2880, 2944, 3008]";
	public static String MIN_MAX_MEMORY_MSG = "Do You Want To Provide Memory (mb) Range In The Increment Of 64mb Starting With 128 mb. Please Specify \"NO\" Without \"\" If " + 
													"You Want To Use Default Range Of 128-1280-128 (Min=128, Max=1280 And Increment=128)";
	public static String NUM_TOTAL_INVOCATION_MSG = "Please Provide Total Number Of Invocation (recommended to set it 100) To Your Lambda Function Across All Payloads";
	public static String JSON_PAYLOAD_MSG = "Do You Want To Provide Lambda Json Payload (.json file) Location. Please specify \"NO\" Without \"\" If " + 
			"You Do Not Want To Send Payload With Your Lambda Invocation";

	//Help Keys
	public static String KEY_HELP = "--help";
	public static String KEY_HELP_JSON = "--json";
	public static String KEY_HELP_VALIDATE = "--validate";
	public static String KEY_WEIGHT = "Weight";
	public static String KEY_BODY = "Body";
}