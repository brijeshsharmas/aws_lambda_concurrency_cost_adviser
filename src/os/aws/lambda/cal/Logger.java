/**
 * 
 */
package os.aws.lambda.cal;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Brijesh Sharma
 *
 */
public class Logger {
	
	private boolean writeToFile = false;
	private static Logger logger = null;
	private List<String> listTabs = new ArrayList<String>();
	
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
		printAndWrite(msg);
	}
	public void printStarLine() {
		printAndWrite("**************************************************************************************************************");
	}
	public void printStarLine(String msg) {
		printAndWrite("********************************" + msg + "***************************************");
	}
	public void printUnderscoreLine() {
		printAndWrite("______________________________________________________________________________________________________________");
	}
	public void printMinusLine() {
		printAndWrite("--------------------------------------------------------------------------------------------------------------");
	}
	public void printForwardSlashLine() {
		printAndWrite("//////////////////////////////////////////////////////////////////////////////////////////////////////////////");
	}
	public void printBlankLine() {
		printAndWrite("");
	}
	public void printAbortMessage(String msg) {
		printBlankLine();
		printForwardSlashLine();
		printStarLine(msg);
		printForwardSlashLine();
		printBlankLine();
	}
	public void startSectionWithNewSection(String msg) {
		listTabs.add("  ");
		printAndWrite(msg);
		
	}
	public void endSection(String msg) {
		listTabs.clear();
		printAndWrite(msg);
		
	}
	private void printAndWrite(String msg) {
		for(String str: listTabs) System.out.print(str);
		System.out.println(msg);
		
	}
	

}
