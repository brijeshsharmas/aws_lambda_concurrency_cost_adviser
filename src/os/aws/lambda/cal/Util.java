/**
 * 
 */
package os.aws.lambda.cal;

import static os.aws.lambda.cal.config.ConfigConstants.CODE_ERROR_FORMATTING_VALUE;

import java.io.File;
import java.text.NumberFormat;
import java.text.ParsePosition;

import os.aws.lambda.cal.config.ConfigConstants;

/**
 * @author Brijesh Sharma
 *
 */
public class Util {

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
	public static boolean isFileExists(String path) {
		if(path == null || path.trim().length() == 0) return false;
		return new File(path).exists();
	}

}
