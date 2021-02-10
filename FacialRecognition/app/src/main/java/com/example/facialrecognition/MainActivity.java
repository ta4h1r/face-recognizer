package com.example.facialrecognition;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoDatabase;

public class MainActivity extends AppCompatActivity {

    Button recognizeBtn;
    Button enrolBtn;

    StitchAppClient client;
    RemoteMongoCollection remoteCollection;
    RemoteMongoClient remoteMongoClient;
    RemoteMongoDatabase db;

    CognitoCachingCredentialsProvider Credentials;
    AmazonRekognition rekognition;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recognizeBtn = findViewById(R.id.recognizeBtn);
        enrolBtn = findViewById(R.id.enrolBtn);

        recognizeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, RecognizeActivity.class);
                startActivity(intent);
            }
        });

        enrolBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, EnrolActivity.class);
                startActivity(intent);
            }
        });


    }

}
