package os.aws.lambda.cal;


import os.aws.lambda.cal.config.Config;



public class AwsLdConCostCal {
	
	private Logger logger = Logger.getLogger();
	
	public void kickOff() {
		
		//Capture and Validate Input
		Config config = new Config();
		if(!config.captureAndValidateInput()) {
			config.printAllConfiguration();
			return;
		}
		config.printAllConfiguration();
	
		//Execute Calculator
		execute(config);
		
	}
	
	private void execute(Config config ) {
		
	}

	public static void main(String[] args) { new AwsLdConCostCal().kickOff(); }

/*******************************************************Helper Methods***************************************/	
	
}
