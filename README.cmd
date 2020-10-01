# AWS Lambda Size, Concurrency & Cost Adviser 

It is CLI based tool written in Java (JRE 1.8), which developers can use to find out average response time of their Lambda function across varied memory configurations and weighted json payloads representing scenarios. The CLI tool takes various configurations such as Lambda function name, IAM keys, Min-Max memory, json payload files, etc. as command line input or configuration file

## Installation

Unzip LambdaCal.zip file, no separate installation request


## Usage

Go to directory where you unzip this file, open Window command prompt or git bash terminal and execute following command
java -jar LambdaCalc.jar
Read through the instructions carefully for each prompt and accordingly provide configuration information
Tool will ask if you want to save your configuration for later use. If yes, then tool save your configuration in file config.json. You can start the tool using this config file using below command
java -jar LambdaCalc.jar --file config.json

for help use --help options

## Sample IAM Policy Document
A sample policy "policy.json" document containng list of permission required to run this tool is included in zip file

## Sample Json Payload Document
A sample json payload document "payload.json" is included in zip file


## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

## License
(https://github.com/brijeshsharmas/aws_lambda_concurrency_cost_adviser/blob/master/LICENSE)