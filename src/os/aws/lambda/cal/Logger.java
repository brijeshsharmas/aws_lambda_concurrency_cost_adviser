/**
 * 
 */
package os.aws.lambda.cal;

/**
 * @author Brijesh Sharma
 *
 */
public class Logger {
	
	private boolean writeToFile = false;
	private static Logger logger = null;
	
	protected Logger() {}
	
	public static Logger getLogger() {
		if(logger != null) return logger;
		
		synchronized (Logger.class) {
			if(logger != null) return logger;
			
			return logger = new Logger();
		}
		
	}

	public void enableWriteToFile() {
		writeToFile = true;
	}
	
	public void print(String msg) {
		System.out.println(msg);
	}

}
