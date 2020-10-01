/**
 * @author Brijesh Sharma
 * Copyright (c) <year>, <copyright holder> 
 * All rights reserved.
 * This source code is licensed under the MIT license found in the LICENSE file in the root directory of this source tree. 
 */
package os.aws.lambda.cal.service;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;

import os.aws.lambda.cal.config.Config;

public class AWSServiceFactory {
	
	static {
	      System.setProperty("org.apache.commons.logging.Log",
	                         "org.apache.commons.logging.impl.NoOpLog");
	   }

	private static AWSServiceFactory factory = null;
	private AWSLambda lambdaClient = null;
	AmazonCloudWatch cloudWatchClient = null;
	private Config config = Config.getConfig();
	private ClientConfiguration clientConfig = null;
	private AWSCredentialsProvider credentialProvider = null;
	
	/*******************************************************SINGLETOM IMPLEMENTATION**********************************************************************/
	protected AWSServiceFactory() {}
	
	public static AWSServiceFactory getFactory() {
		if(factory != null) return factory;
		
		synchronized (AWSServiceFactory.class) {
			if(factory != null) return factory;
			
			return factory = new AWSServiceFactory();
		}
		
	}
	public AWSLambda getAWSLambdaClient() {
		if(lambdaClient != null) return lambdaClient;
		
		synchronized (this) {
			if(lambdaClient != null) return lambdaClient;
			return lambdaClient = buildAWSLambdaClient();
		}
	}
	public AmazonCloudWatch getAWSCloudWatchClient() {
		if(cloudWatchClient != null) return cloudWatchClient;
		
		synchronized (this) {
			if(cloudWatchClient != null) return cloudWatchClient;
			return cloudWatchClient = buildAWSCloudWatchClient();
		}
	}
	private ClientConfiguration getClientConfiguration() {
		if(!config.hasProxy()) return null;
		if(clientConfig == null) {
			synchronized (this) {
				if(clientConfig != null) return clientConfig;
				return clientConfig = buildClientConfigurationWithProxy();
			}
		}
		return clientConfig;
	}
	private AWSCredentialsProvider getCredentials() {
		if(credentialProvider != null) return credentialProvider;
		synchronized (this) {
			if(credentialProvider != null) return credentialProvider;
			if(config.useDefaultCredentialsProvider())
				credentialProvider = new ProfileCredentialsProvider();
			else 
				return credentialProvider = buildCredentialProviderWithCustomKeys();
		}
		return credentialProvider;
	}
	/*******************************************************BUILDER METHODS**********************************************************************/
	private AWSLambda buildAWSLambdaClient() {
		return AWSLambdaClientBuilder.standard()
				.withClientConfiguration(getClientConfiguration())
				.withCredentials(getCredentials())
				.withRegion(config.getAWSRegion())
				.build();
	}
	private AmazonCloudWatch buildAWSCloudWatchClient() {
		return AmazonCloudWatchClientBuilder.standard()
				.withClientConfiguration(getClientConfiguration())
				.withCredentials(getCredentials())
				.withRegion(config.getAWSRegion())
				.build();
	}
	private ClientConfiguration buildClientConfigurationWithProxy() {
		return new ClientConfiguration()
				.withProxyHost(config.getProxyHost())
				.withProxyPort(config.getProxyPort());
	}
	private AWSCredentialsProvider buildCredentialProviderWithCustomKeys() {
		return new AWSStaticCredentialsProvider(new BasicAWSCredentials(config.getAcessKey(), config.getSecretAccessKey()));
	}
	
}
