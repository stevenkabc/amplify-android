package com.example.demo.mypetapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.amazonaws.amplify.generated.graphql.CreatePetMutation;
import com.amazonaws.amplify.generated.graphql.ListPetsQuery;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient;
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.apollographql.apollo.GraphQLCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import type.CreatePetInput;
import type.S3ObjectInput;

public class AddPetActivity extends AppCompatActivity {

    private static final String TAG = AddPetActivity.class.getSimpleName();
    private static final String JPG_MIME = "image/jpg";
    private String storageBucketName;
    private String region;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_pet);

        Button btnAddItem = findViewById(R.id.btn_save);
        btnAddItem.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                uploadAndSave();
            }
        });

        Button btnAddPhoto = findViewById(R.id.btn_add_photo);
        btnAddPhoto.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                choosePhoto();
            }
        });

        setStorageInfo();
    }

    private void setStorageInfo() {
        JSONObject s3Config = new AWSConfiguration(this)
                .optJsonObject("S3TransferUtility");
        try {
            storageBucketName = s3Config.getString("Bucket");
            region = s3Config.getString("Region");
        } catch (JSONException e) {
            Log.e(TAG, "Can't find S3 bucket or region", e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(AddPetActivity.this, "Error: Can't find S3 bucket or region. \nHave you run 'amplify add storage'? ", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    // Photo selector application code.
    private static int RESULT_LOAD_IMAGE = 1;
    private String photoPath;

    public void choosePhoto() {
        Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, RESULT_LOAD_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();
            // String picturePath contains the path of selected Image
            photoPath = picturePath;
        }
    }

    private void uploadAndSave(){

        // For higher Android levels, we need to check permission at runtime
        if (photoPath != null) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted
                Log.d(TAG, "READ_EXTERNAL_STORAGE permission not granted! Requesting...");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        1);
            }

            // Upload a photo first. We will only call save on its successful callback.
            uploadWithTransferUtility(photoPath);
        } else {
            save();
        }
    }


    private CreatePetInput getCreatePetInput() {
        final String name = ((EditText) findViewById(R.id.editTxt_name)).getText().toString();
        final String description = ((EditText) findViewById(R.id.editText_description)).getText().toString();

        if (photoPath != null && !photoPath.isEmpty()){
            final S3ObjectInput s3ObjectInput = S3ObjectInput.builder()
                    .bucket(storageBucketName)
                    .key(getS3Key(photoPath))
                    .region(region)
                    .mimeType(JPG_MIME)
                    .build();

            return CreatePetInput.builder()
                    .name(name)
                    .description(description)
                    .photo(s3ObjectInput).build();
        } else {
            return CreatePetInput.builder()
                    .name(name)
                    .description(description)
                    .build();
        }
    }


    private void save() {

        CreatePetInput input = getCreatePetInput();

        CreatePetMutation addPetMutation = CreatePetMutation.builder()
                .input(input)
                .build();

        ClientFactory.getInstance(this).mutate(addPetMutation).
                refetchQueries(ListPetsQuery.builder().build()).
                enqueue(mutateCallback);

        // Enables offline support via an optimistic update
        // Add to event list while offline or before request returns
        addPetOffline(input);
    }

    // Mutation callback code
    private GraphQLCall.Callback<CreatePetMutation.Data> mutateCallback = new GraphQLCall.Callback<CreatePetMutation.Data>() {
        @Override
        public void onResponse(@Nonnull final Response<CreatePetMutation.Data> response) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(AddPetActivity.this, "Added pet", Toast.LENGTH_SHORT).show();
                    AddPetActivity.this.finish();
                }
            });
        }

        @Override
        public void onFailure(@Nonnull final ApolloException e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e("", "Failed to perform AddPetMutation", e);
                    Toast.makeText(AddPetActivity.this, "Failed to add pet", Toast.LENGTH_SHORT).show();
                    AddPetActivity.this.finish();
                }
            });
        }
    };

    private void addPetOffline(final CreatePetInput input) {

        final CreatePetMutation.CreatePet expected =
                new CreatePetMutation.CreatePet(
                        "Pet",
                        UUID.randomUUID().toString(),
                        input.name(),
                        input.description(),
                        input.photo() != null?
                                new CreatePetMutation.Photo(
                                        "Photo",
                                        input.photo().bucket(),
                                        input.photo().key(),
                                        input.photo().region(),
                                        photoPath,
                                        input.photo().mimeType())
                                : null);


        final AWSAppSyncClient awsAppSyncClient = ClientFactory.getInstance(this);
        final ListPetsQuery listEventsQuery = ListPetsQuery.builder().build();

        awsAppSyncClient.query(listEventsQuery)
                .responseFetcher(AppSyncResponseFetchers.CACHE_ONLY)
                .enqueue(new GraphQLCall.Callback<ListPetsQuery.Data>() {
                    @Override
                    public void onResponse(@Nonnull Response<ListPetsQuery.Data> response) {
                        List<ListPetsQuery.Item> items = new ArrayList<>();
                        if (response.data() != null) {
                            items.addAll(response.data().listPets().items());
                        }

                        items.add(new ListPetsQuery.Item(expected.__typename(),
                                expected.id(),
                                expected.name(),
                                expected.description(),
                                expected.photo() != null ? new ListPetsQuery.Photo(
                                        "Photo",
                                        input.photo().bucket(),
                                        input.photo().key(),
                                        input.photo().region(),
                                        photoPath,
                                        input.photo().mimeType()) : null
                                ));
                        ListPetsQuery.Data data = new ListPetsQuery.Data(
                                new ListPetsQuery.ListPets("ListPets", items, null));
                        awsAppSyncClient.getStore().write(listEventsQuery, data).enqueue(null);
                        Log.d(TAG, "Successfully wrote item to local store while being offline.");

                        finishIfOffline();
                    }

                    @Override
                    public void onFailure(@Nonnull ApolloException e) {
                        Log.e(TAG, "Failed to update event query list.", e);
                    }
                });
    }

    private void finishIfOffline(){
        // Close the add activity when offline otherwise allow callback to close
        ConnectivityManager cm =
                (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        if (!isConnected) {
            Log.d(TAG, "App is offline. Returning to MainActivity .");
            finish();
        }
    }

    private String getS3Key(String localPath) {
        //We have read and write ability under the public folder
        return "public/" + new File(localPath).getName();
    }

    public void uploadWithTransferUtility(String localPath) {
        String key = getS3Key(localPath);

        Log.d(TAG, "Uploading file from " + localPath + " to " + key);

        TransferObserver uploadObserver =
                ClientFactory.getTransferUtility(this).upload(
                        key,
                        new File(localPath));

        // Attach a listener to the observer to get state update and progress notifications
        uploadObserver.setTransferListener(new TransferListener() {

            @Override
            public void onStateChanged(int id, TransferState state) {
                if (TransferState.COMPLETED == state) {
                    // Handle a completed upload.
                    Log.d(TAG, "Upload is completed. ");

                    // Upload is successful. Save the rest and send the mutation to server.
                    save();
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                int percentDone = (int)percentDonef;

                Log.d(TAG, "ID:" + id + " bytesCurrent: " + bytesCurrent
                        + " bytesTotal: " + bytesTotal + " " + percentDone + "%");
            }

            @Override
            public void onError(int id, Exception ex) {
                // Handle errors
                Log.e(TAG, "Failed to upload photo. ", ex);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(AddPetActivity.this, "Failed to upload photo", Toast.LENGTH_LONG).show();
                    }
                });
            }

        });
    }
}
