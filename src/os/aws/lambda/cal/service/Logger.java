/**
 * @author Brijesh Sharma
 * Copyright (c) <year>, <copyright holder> 
 * All rights reserved.
 * This source code is licensed under the MIT license found in the LICENSE file in the root directory of this source tree. 
 */
package os.aws.lambda.cal.service;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class Logger {
	
	private boolean writeToFile = false;
	private static Logger logger = null;
	private List<String> listTabs = new ArrayList<String>();
	private File file = new File("calc.log");
	private PrintWriter writer = null;
	
	/*******************************************************SINGLETOM IMPLEMENTATION**********************************************************************/
	protected Logger() {}
	
	public static Logger getLogger() {
		if(logger != null) return logger;
		
		synchronized (Logger.class) {
			if(logger != null) return logger;
			
			return logger = new Logger();
		}
		
	}

	/*******************************************************FILE HANDLING**********************************************************************/
	public void enableWriteToFile() {
		if(writer == null) {
			try {writer = new PrintWriter(file);}catch(Exception e) {
				printAbortMessage("Could Not Create File [" + file.getAbsolutePath() + "]. Error [" + e.getMessage() + "]. None Of The Logs Will Be Written To Log File");
				return;
			}
		}
		writeToFile = true;
	}
	public void closeFile() {
		if(writer != null) 
			try {writer.close();}catch(Exception e) {}
	}
	private void writeToFile(String msg) { if (writeToFile && writer != null) writer.println(msg); }
	
	/*******************************************************PUBLIC PRINT HANDLING**********************************************************************/
	public void beginNewSection( ) { beginNewSection("");}
	public void beginNewSection(String msg) {
		printUnderscoreLine();printBlankLine();printStarLine(msg);
		listTabs.add("  ");
	}
	public void endNewSection( ) { endNewSection("");}
	public void endNewSection(String msg) {
		listTabs.clear();
		printStarLine(msg);printUnderscoreLine();printBlankLine();
	}

	public void beginNewSubSection( ) { beginNewSubSection("");}
	public void beginNewSubSection(String msg) {
		printBlankLine();
		print(msg);
		listTabs.add("  ");
	}
	public void endNewSubSection( ) { endNewSubSection("");}
	public void endNewSubSection(String msg) {
		if(listTabs.size() > 0) listTabs.remove(listTabs.size()-1);
		print(msg);
		printBlankLine();
	}
	public void print(String msg) {
		printAndWrite(msg);
	}
	
	/*******************************************************SPECIAL RENDERING METHOD**********************************************************************/
	public void printStarLine() {
		printAndWrite("**************************************************************************************************************");
	}
	public void printStarLine(String msg) {
		printAndWrite("********************************" + msg + "***************************************");
	}
	public void printUnderscoreLine() {
		printAndWrite("______________________________________________________________________________________________________________");
	}
	public void printInSameLine(String msg) {
		System.out.print(msg);
	}
	public void printMinusLine() {
		printAndWrite("--------------------------------------------------------------------------------------------------------------");
	}
	public void printForwardSlashLine() {
		printAndWrite("//////////////////////////////////////////////////////////////////////////////////////////////////////////////");
	}
	public void printBlankLine() {
		System.out.println("");
		writeToFile("");
	}
	public void printAbortMessage(String msg) {
		printBlankLine();
		printForwardSlashLine();
		printStarLine(msg);
		printForwardSlashLine();
		printBlankLine();
	}
	private void printAndWrite(String msg) {
		for(String str: listTabs) { 
			System.out.print(str);
			writeToFile(str);
		}
		System.out.println(msg);
		writeToFile(msg);
		
	}
	
	
	/*******************************************************PRIVATE PRINT HANDLING**********************************************************************/
}
