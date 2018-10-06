package com.example.demo.mypetapp;

import android.content.Context;

import com.amazonaws.mobile.auth.core.IdentityManager;
import com.amazonaws.mobile.auth.userpools.CognitoUserPoolsSignInProvider;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient;
import com.amazonaws.mobileconnectors.appsync.sigv4.BasicCognitoUserPoolsAuthProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;

public class ClientFactory {
    private static volatile AWSAppSyncClient client;
    private static volatile TransferUtility transferUtility;

    public static synchronized void init(final Context context) {
        if (client == null) {
            final AWSConfiguration awsConfiguration = new AWSConfiguration(context);

            CognitoUserPoolsSignInProvider cognitoUserPoolsSignInProvider =
                    (CognitoUserPoolsSignInProvider) IdentityManager.getDefaultIdentityManager().getCurrentIdentityProvider();
            BasicCognitoUserPoolsAuthProvider basicCognitoUserPoolsAuthProvider =
                    new BasicCognitoUserPoolsAuthProvider(cognitoUserPoolsSignInProvider.getCognitoUserPool());

            client = AWSAppSyncClient.builder()
                    .context(context)
                    .awsConfiguration(awsConfiguration)
                    .cognitoUserPoolsAuthProvider(basicCognitoUserPoolsAuthProvider)
                    .build();
        }

        if (transferUtility == null) {
            if (transferUtility == null) {
                transferUtility = TransferUtility.builder()
                        .context(context)
                        .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                        .s3Client(new AmazonS3Client(AWSMobileClient.getInstance().getCredentialsProvider()))
                        .build();
            }
        }
    }

    public static synchronized AWSAppSyncClient appSyncClient() {
        return client;
    }

    public static synchronized TransferUtility transferUtility() {
        return transferUtility;
    }
}