/**
 * 
 */
package os.aws.lambda.cal.config;

/**
 * @author Brijesh Sharma
 *
 */
public interface ConfigConstants {
	
	public static int CODE_ERROR_FORMATTING = 1212121;
	
	public static String AWS_ACCESS_KEY = "AWS_ACCESS_KEY";
	public static String AWS_ACCESS_SECRET_KEY = "AWS_ACCESS_SECRET_KEY";
	public static String PROXY_HOST = "PROXY_HOST";
	public static String PROXY_PORT = "PROXY_PORT";
	public static String LOG_MESSAGE = "LOG_MESSAGE";
	public static String LAMBDA_ARN = "LAMBDA_ARN";
	public static String MIN_MAX_MEMORY = "MIN_MAX_MEMORY";
	
	/****Message Constants**/
	public static String AWS_ACCESS_KEY_MSG = "Please enter your AWS_ACCESS_KEY:";
	public static String AWS_ACCESS_SECRET_KEY_MSG = "Please enter your AWS_ACCESS_SECRET_KEY:";
	public static String PROXY_HOST_MSG = "If you are using proxy, please enter proxy host, else enter NO:";
	public static String PROXY_PORT_MSG = "Please enter proxy port:";
	public static String LOG_MESSAGE_MSG = "Please enter true/false if you want to log all messages:";
	public static String LAMBDA_ARN_MSG = "Please provide fully qualified lambda ARN:";
	public static String MIN_MAX_MEMORY_MSG = "Please provide memory (mb) range must be increment of 64mb starting 128 mb. Please specify NO if you want to use default range of 128-1280 incremetal of 128\n" + 
			"Valid Values Are [128, 192, 256, 320, 384, 448, 512, 576, 640, 704, 768, 832, 896, 960, 1024, 1088, 1152, 1216, 1280, 1344, 1408, 1472, 1536, 1600, 1664, 1728, 1792, 1856, 1920, 1984, 2048, 2112," + 
				"2176, 2240, 2304, 2368, 2432, 2496, 2560, 2624, 2688, 2752, 2816, 2880, 2944, 3008]:";

}
