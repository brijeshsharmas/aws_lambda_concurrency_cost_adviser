/**
 * 
 */
package os.aws.lambda.cal.util;

import os.aws.lambda.cal.Logger;
import os.aws.lambda.cal.config.Config;

/**
 * @author Brijesh Sharma
 *
 */
public class Renderer {
	
	public static final int INT_UNDERSCORE_LINE = 0;
	public static final int INT_STAR_LINE = 1;
	public static final int INT_MINUS_LINE = 2;
	public static final int INT_STAR_LINE_WITH_MESSAGE = 3;
	public static final int INT_BLANK_LINE = 4;
	public static final int INT_DO_NOTHING = 99;
	
	private Logger logger = Logger.getLogger(); 
	private static Renderer renderer = null;
	
	protected Renderer() {}
	
	/*******************************************************SINGLETOM IMPLEMENTATION**********************************************************************/
	public static Renderer getRenderer() {
		if(renderer != null) return renderer;
		
		synchronized (Config.class) {
			if(renderer != null) return renderer;
			
			return renderer = new Renderer();
		}
		
	}

	/*******************************************************RENDERED METHODS*****************************************************************/
	public void printLine(int args[]) {  printLine(args, "");}
	public void printLine(int args[], String starMessage) {
		
		if(args == null) return;
		for(int i=0; i<args.length; i++) {
			switch(args[i]) {
				case INT_UNDERSCORE_LINE:
					logger.printUnderscoreLine();
					break;
				case INT_STAR_LINE:
					logger.printStarLine();
					break;
				case INT_MINUS_LINE:
					logger.printMinusLine();
					break;
				case INT_STAR_LINE_WITH_MESSAGE:
					logger.printStarLine(starMessage);
					break;
				case INT_BLANK_LINE:
					logger.printBlankLine();
					break;
				case INT_DO_NOTHING:
					break;
			}
		}
		
	}
}
