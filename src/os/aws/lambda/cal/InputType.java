/**
 * 
 */
package os.aws.lambda.cal;

/**
 * @author Brijesh Sharma
 *
 */
public enum InputType {
	
	String("S"), Int("I"), Float("F"), Boolean("B");
	
	private String text;

	private InputType(String text){ this.text = text; }
	public String getText() { return text; }
	public void setText(String text) { this.text = text; }

}
