/**
 * @author Brijesh Sharma
 * Copyright (c) 2020, Brijesh Sharma 
 * All rights reserved.
 * This source code is licensed under the MIT license found in the LICENSE file in the root directory of this source tree. 
 */


package os.aws.lambda.cal.util;

import static os.aws.lambda.cal.config.ConfigConstants.CODE_ERROR_FORMATTING_VALUE;
import static os.aws.lambda.cal.config.ConfigConstants.KEY_AVERAGE;
import static os.aws.lambda.cal.config.ConfigConstants.KEY_SUM;
import static os.aws.lambda.cal.config.ConfigConstants.TIMEZONE_DEFAULT;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.amazonaws.services.cloudwatch.model.MetricDataResult;

import os.aws.lambda.cal.service.Logger;

public class Util {
	
	private static Logger logger = Logger.getLogger();
	private static SimpleDateFormat  dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss z");
	
	static{
		//Set Date Format To UTC
		dateFormat.setTimeZone(TimeZone.getTimeZone(TIMEZONE_DEFAULT));
	}

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
	
	/*******************************************HANDLING AWS SDK OBJECTS******************************************************************************/
	public static double getMetricsData(List<MetricDataResult> listResults, String stat) {
		double returnValue = 0.0d;
		if(listResults == null || listResults.size() == 0) return returnValue;
		
		int totalResultSize = 0;
		for(int i=0; i<listResults.size(); i++) {
			MetricDataResult nextResult = listResults.get(i);
			if(nextResult == null) continue;
			
			List<Double> listValues = nextResult.getValues();
			logger.print("  For Stat [" + stat + "], List Of Metrics Received Are-->" + listValues);
			if(listValues ==null) continue;
			
			totalResultSize += listValues.size();
			for(Double doubleValue: listValues)
				returnValue += doubleValue;
		}
		
		if (KEY_SUM.equalsIgnoreCase(stat)) return returnValue;
		if (KEY_AVERAGE.equalsIgnoreCase(stat))	return returnValue/totalResultSize;
		return 0.0d;
	}
	public static String formatToUTCDate(long millisecond) { 
		return dateFormat.format(new Date(millisecond)); 
	}

	public static long getSecondNearestToMinute(long millisecond) { return  60- (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())%60); }
	public static long getSecondNearestToMinute() { return  getSecondNearestToMinute(System.currentTimeMillis()); }
	
	public static long getMilliNearestToMinute() { return getMilliNearestToMinute(System.currentTimeMillis()); }
	public static long getMilliNearestToMinute(long millisecond) { return  System.currentTimeMillis() + (1000 * getSecondNearestToMinute(millisecond)); }
	
	public static long convertSecondToMilli(long seconds) { return  1000*seconds; }
	
	public static long getLowestTimeSeries(Map<Integer, List<Long>> mapKeyValue) {
		long returnValue = Long.MAX_VALUE;
		for(Map.Entry<Integer, List<Long>> entry: mapKeyValue.entrySet()) {
			for(Long longValue: entry.getValue())
				returnValue = longValue < returnValue ? longValue : returnValue;
		}
		
		return returnValue;
	}
	public static long getHighestTimeSeries(Map<Integer, List<Long>> mapKeyValue) {
		long returnValue = Long.MIN_VALUE;
		for(Map.Entry<Integer, List<Long>> entry: mapKeyValue.entrySet()) {
			for(Long longValue: entry.getValue())
				returnValue = longValue > returnValue ? longValue : returnValue;
		}
		
		return returnValue;
	}
	public static Integer sumValue(Map<Integer, Integer> mapKeyValue) {
		int returnValue = 0;
		for(Map.Entry<Integer, Integer> entry: mapKeyValue.entrySet()) 
			returnValue += entry.getValue();
		
		return returnValue;
	}
	public static int nearestHunread(int num) { return num%100 > 0 ? ((num/100)*100)+100 : num;}
}
