/*
 * Copyright 2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 * 
 *  http://aws.amazon.com/apache2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.codedeploy;



import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.UUID;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.codedeploy.AmazonCodeDeployClient;
import com.amazonaws.services.codedeploy.model.GetApplicationRequest;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.GetUserResult;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProvider;
/**
 * @author gibbon
 */




public class AWSClients{
    /**
     * Index in the colon-separated ARN that contains the account id
     * Sample ARN: arn:aws:iam::123456789012:user/David
     **/

       AWSCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();

       /* if (credentialsProvider.getCredentials() == null) {
            File configFile = new File(System.getProperty("user.home"), ".aws/config");
            throw new RuntimeException("No AWS security credentials found:\n"
                    + "Make sure you've configured your credentials in: " + configFile.getAbsolutePath() + "\n"
                    + "For more information on configuring your credentials, see "
                    + "http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html");
        }
       )*/






    private static final int ARN_ACCOUNT_ID_INDEX = 4;

    public final AmazonCodeDeployClient codedeploy;
    public final AmazonS3Client         s3;
    //public final AWSCredentialsProvider provider;

    private final String region;
    private final String proxyHost;
    private final int proxyPort;
    private final String profile;

    public AWSClients(String region, AWSCredentials credentials, String proxyHost, int proxyPort , String profile) {
        this.region = region;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.profile = profile;
        
        //setup proxy connection:
        ClientConfiguration clientCfg = new ClientConfiguration();
        if (proxyHost != null && proxyPort > 0 ) {
            clientCfg.setProxyHost(proxyHost);
            clientCfg.setProxyPort(proxyPort);
        }
        if (profile != null){
          //  this.s3 = credentials != null ? new AmazonS3Client(provider, clientCfg) : new AmazonS3Client(clientCfg);
        //this.codedeploy = credentials != null ? new AmazonCodeDeployClient(provider, clientCfg) : new AmazonCodeDeployClient(clientCfg);
        //codedeploy.setRegion(Region.getRegion(Regions.fromName(this.region)));
        //s3.setRegion(Region.getRegion(Regions.fromName(this.region)));
        }
        else { 
            this.s3 = credentials != null ? new AmazonS3Client(credentials, clientCfg) : new AmazonS3Client(clientCfg);
        this.codedeploy = credentials != null ? new AmazonCodeDeployClient(credentials, clientCfg) : new AmazonCodeDeployClient(clientCfg);
        codedeploy.setRegion(Region.getRegion(Regions.fromName(this.region)));
        s3.setRegion(Region.getRegion(Regions.fromName(this.region)));
        }
    }
    
    public static AWSClients fromDefaultCredentialChain(String region, String proxyHost, int proxyPort , String profile) {
        return new AWSClients(region, null, proxyHost, proxyPort, profile);
    }
    
    public static AWSClients fromIAMRole(String region, String iamRole, String externalId, String proxyHost, int proxyPort) {
        return new AWSClients(region, getCredentials(iamRole, externalId), proxyHost, proxyPort, profile);
    }
    
    public static AWSClients fromBasicCredentials(String region, String awsAccessKey, String awsSecretKey, String proxyHost, int proxyPort) {
        return new AWSClients(region, new BasicAWSCredentials(awsAccessKey, awsSecretKey), proxyHost, proxyPort, profile);
    }

    /**
     * Via the default provider chain (i.e., global keys for this Jenkins instance),  return the account ID for the
     * currently authenticated user.
     * @param proxyHost hostname of the proxy to use (if any)
     * @param proxyPort port of the proxy to use (if any)
     * @return 12-digit account id
     */
    public static String getAccountId(String proxyHost, int proxyPort) {

        String arn = "";
        try {
            ClientConfiguration clientCfg = new ClientConfiguration();
            if (proxyHost != null && proxyPort > 0 ) {
                clientCfg.setProxyHost(proxyHost);
                clientCfg.setProxyPort(proxyPort);
            }
            AmazonIdentityManagementClient iam = new AmazonIdentityManagementClient(clientCfg);
            GetUserResult user = iam.getUser();
            arn = user.getUser().getArn();
        } catch (AmazonServiceException e) {
            if (e.getErrorCode().compareTo("AccessDenied") == 0) {
                String msg = e.getMessage();
                int arnIdx = msg.indexOf("arn:aws");
                if (arnIdx != -1) {
                    int arnSpace = msg.indexOf(" ", arnIdx);
                    arn = msg.substring(arnIdx, arnSpace);
                }
            }
        }

        String accountId = arn.split(":")[ARN_ACCOUNT_ID_INDEX];
        return accountId;
    }

    public void testConnection(String s3bucket, String codeDeployApplication) throws Exception {
        String testKey = "tmp-" + UUID.randomUUID() + ".txt";
        s3.putObject(s3bucket, testKey, createTestFile());

        codedeploy.getApplication(new GetApplicationRequest().withApplicationName(codeDeployApplication));
    }

    private File createTestFile() throws IOException {
        File file = File.createTempFile("codedeploy-jenkins-plugin", ".txt");
        file.deleteOnExit();

        Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        writer.write("");
        writer.close();

        return file;
    }

    private static AWSCredentials getCredentials(String iamRole, String externalId) {
        if (isEmpty(iamRole)) return null;

        AWSSecurityTokenServiceClient sts = new AWSSecurityTokenServiceClient();

        int credsDuration = (int) (AWSCodeDeployPublisher.DEFAULT_TIMEOUT_SECONDS
                        * AWSCodeDeployPublisher.DEFAULT_POLLING_FREQUENCY_SECONDS);

        if (credsDuration > 3600) {
            credsDuration = 3600;
        }

        AssumeRoleResult assumeRoleResult = sts.assumeRole(new AssumeRoleRequest()
                        .withRoleArn(iamRole)
                        .withExternalId(externalId)
                        .withDurationSeconds(credsDuration)
                        .withRoleSessionName(AWSCodeDeployPublisher.ROLE_SESSION_NAME)
        );

        Credentials stsCredentials = assumeRoleResult.getCredentials();
        BasicSessionCredentials credentials = new BasicSessionCredentials(
                stsCredentials.getAccessKeyId(),
                stsCredentials.getSecretAccessKey(),
                stsCredentials.getSessionToken()
        );

        return credentials;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public String getProxyHost() {
        return proxyHost;
    }
}
