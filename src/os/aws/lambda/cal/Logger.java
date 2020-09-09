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
	
	/*******************************************************SINGLETOM IMPLEMENTATION**********************************************************************/
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
	public void printStarLine() {
		System.out.println("**************************************************************************************************************");
	}
	public void printStarLine(String msg) {
		System.out.println("********************************" + msg + "***************************************");
	}
	public void printUnderscoreLine() {
		System.out.println("______________________________________________________________________________________________________________");
	}
	public void printMinusLine() {
		System.out.println("--------------------------------------------------------------------------------------------------------------");
	}
	public void printBlankLine() {
		System.out.println("");
	}

}
