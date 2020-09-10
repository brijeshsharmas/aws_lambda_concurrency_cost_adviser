/**
 * 
 */
package os.aws.lambda.cal.util;

import static os.aws.lambda.cal.config.ConfigConstants.CODE_ERROR_FORMATTING_VALUE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.NumberFormat;
import java.text.ParsePosition;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import os.aws.lambda.cal.Logger;
import os.aws.lambda.cal.config.ConfigConstants;
import os.aws.lambda.cal.modal.JsonPayload;

/**
 * @author Brijesh Sharma
 *
 */
public class Util {
	
	private static Logger logger = Logger.getLogger();

	/*Position Start From Zero**/
	public static int getIntValueByPosition(String text, int position, String delimiter) {
		String value = getValueByPosition(text, position, delimiter);
		if(isNumeric(value))
			return Integer.parseInt(value);
		
		return CODE_ERROR_FORMATTING_VALUE;
		
	}
	public static String getValueByPosition(String text, int position, String delimiter) {
		if(text == null || text.trim().length() == 0 || position < 0) return ""+CODE_ERROR_FORMATTING_VALUE;
		
		String [] tokens = text.split(delimiter);
		if((tokens.length-1) < position) return ""+CODE_ERROR_FORMATTING_VALUE;
		
		return tokens[position];
		
	}
	public static boolean isNumeric(String str) {
		  NumberFormat formatter = NumberFormat.getInstance();
		  ParsePosition pos = new ParsePosition(0);
		  formatter.parse(str, pos);
		  return str.length() == pos.getIndex();
	}
	public static boolean isBoolean(String str) { return Boolean.getBoolean(str); }
	public static boolean isFileExists(String path) {
		if(path == null || path.trim().length() == 0) return false;
		return new File(path).exists();
	}
	
	public static boolean writeToFile(String path, String content) { return writeToFile(path, content.getBytes()); }
	public static boolean writeToFile(String path, byte[] bytes) {
		File file = new File(path);
		FileOutputStream stream = null;
		try {
			stream = new FileOutputStream(file);
			stream.write(bytes);
		}catch(IOException e) {
			logger.print("Error Saving File [" + file.getAbsolutePath() + "]. Error [" + e.getMessage() + "]");
			return false;
		} finally { if(stream != null) try {stream.close();}catch(Exception e) {} }
		
		return true;
	}
	public static String[] toLowerCase(String [] args) {
		if(args == null) return null;
		String [] newArgs = new String[args.length];
		for(int i=0; i<args.length; i++) 
			newArgs[i] = args[i].toLowerCase();
		
		return newArgs;
	}
	public static JSONObject loadJsonObject(String path) {
		if(!Util.isFileExists(path)) {
			logger.print("Invalid File Or File-Path [" + path + "]");
			return null;
		}
		Reader reader = null;
		try { 
			reader = new FileReader(path); 
			JSONParser parser = new JSONParser();
			return (JSONObject)parser.parse(reader);
		} catch(IOException e) { logger.print("Error Loading Json File [" + path + "]. Error [" + e.getMessage() + "]"); 			
		} catch(ParseException e) { logger.print("Error Parsing Json File [" + path + "]. Error [" + e.getMessage() + "]");
		} finally {close(reader);}
		return null;
	}
	public static void close(Reader reader) {try { if (reader != null) reader.close();}catch(Exception e) {}}

}