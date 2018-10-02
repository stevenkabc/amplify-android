package com.example.demo.mypetapp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.amazonaws.amplify.generated.graphql.CreatePetMutation;
import com.amazonaws.amplify.generated.graphql.ListPetsQuery;
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient;
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers;
import com.apollographql.apollo.GraphQLCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import type.CreatePetInput;

public class AddPetActivity extends AppCompatActivity {

    private static final String TAG = AddPetActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_pet);

        Button btnAddItem = findViewById(R.id.btn_save);
        btnAddItem.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                save();
            }
        });
    }

    private void save() {
        final String name = ((EditText) findViewById(R.id.editTxt_name)).getText().toString();
        final String description = ((EditText) findViewById(R.id.editText_description)).getText().toString();

        CreatePetInput input = CreatePetInput.builder()
                .name(name)
                .description(description)
                .build();

        CreatePetMutation addPetMutation = CreatePetMutation.builder()
                .input(input)
                .build();

        ClientFactory.getInstance(this).mutate(addPetMutation).
                refetchQueries(ListPetsQuery.builder().build()).
                enqueue(mutateCallback);

        // The following 2 lines enables offline support via an optimistic update
        CreatePetMutation.Data expected = new CreatePetMutation.Data(new CreatePetMutation.CreatePet(
                "Pet", UUID.randomUUID().toString(), name, description));

        // Add to event list while offline or before request returns
        addPetOffline(expected);
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

    private void addPetOffline(final CreatePetMutation.Data pendingItem) {
        final AWSAppSyncClient awsAppSyncClient = ClientFactory.getInstance(this);
        final ListPetsQuery listEventsQuery = ListPetsQuery.builder().build();

        final CreatePetMutation.CreatePet createPost = pendingItem.createPet();

        awsAppSyncClient.query(listEventsQuery)
                .responseFetcher(AppSyncResponseFetchers.CACHE_ONLY)
                .enqueue(new GraphQLCall.Callback<ListPetsQuery.Data>() {
                    @Override
                    public void onResponse(@Nonnull Response<ListPetsQuery.Data> response) {
                        List<ListPetsQuery.Item> items = new ArrayList<>();
                        if (response.data() != null) {
                            items.addAll(response.data().listPets().items());
                        }

                        items.add(new ListPetsQuery.Item(createPost.__typename(),
                                createPost.id(),
                                createPost.name(),
                                createPost.description()));
                        ListPetsQuery.Data data = new ListPetsQuery.Data(new ListPetsQuery.ListPets("ListPets", items, null));
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
}
