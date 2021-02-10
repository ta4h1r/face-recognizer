package com.example.facialrecognition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.model.AgeRange;
import com.amazonaws.services.rekognition.model.Attribute;
import com.amazonaws.services.rekognition.model.DetectFacesRequest;
import com.amazonaws.services.rekognition.model.DetectFacesResult;
import com.amazonaws.services.rekognition.model.FaceDetail;
import com.amazonaws.services.rekognition.model.FaceMatch;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.SearchFacesByImageRequest;
import com.amazonaws.services.rekognition.model.SearchFacesByImageResult;
import com.amazonaws.util.IOUtils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.mongodb.lang.NonNull;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoDatabase;
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.ChangeEvent;
import com.sanbot.opensdk.base.TopBaseActivity;

import org.bson.BsonValue;
import org.bson.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

public class RecognizeActivity extends TopBaseActivity {
    Camera camera;
    FrameLayout frame;
    ShowCamera showCamera;
    Button recognizeBtn;
    File TempFile;
    String faceId;
    CognitoCachingCredentialsProvider Credentials;
    AmazonRekognition rekognition;

    StitchAppClient client;
    RemoteMongoCollection _remoteCollection;
    RemoteMongoClient remoteMongoClient;
    RemoteMongoDatabase db;
    RemoteMongoCollection<Document> remoteCollection;
    String name;

    TextView results;
    View loading_anim;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        register(RecognizeActivity.class);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rec);

        loading_anim = findViewById(R.id.loadingPanel);

        // Loading animation
        loading_anim.setVisibility(View.GONE);

        // Request Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        //Initialize Variables
        recognizeBtn = findViewById(R.id.recognitionbutton);
        frame = findViewById(R.id.framelayout);
        camera = Camera.open();
        showCamera = new ShowCamera(this, camera);
        frame.addView(showCamera);
        results = findViewById(R.id.results_view);

        // AWS login
        Credentials = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "<identity-pool-id>", // Identity pool ID
                Regions.US_EAST_1 // Region
        );
       rekognition = new AmazonRekognitionClient(Credentials);

       // Mongo login
        try {
            // Create the default Stitch Client
            client = Stitch.initializeDefaultAppClient("<stitch-app-id>");
            client.getAuth().loginWithCredential(new AnonymousCredential()).addOnCompleteListener(
                    new OnCompleteListener<StitchUser>() {
                        @Override
                        public void onComplete(@NonNull final Task<StitchUser> task) {
                            if (task.isSuccessful()) {
                                Log.d("INFO", String.format(
                                        "logged in as user %s with provider %s",
                                        task.getResult().getId(),
                                        task.getResult().getLoggedInProviderType()));
                            } else {
                                Log.e("INFO", "failed to log in", task.getException());
                            }

                            // Get a remote client
                            remoteMongoClient = client.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas");

                            db = remoteMongoClient.getDatabase("fr-module");
                            remoteCollection = db.getCollection("fr_faces");

                            // Configure automatic data sync between Atlas and local
                            // Conflicts are resolved by giving preference
                            // to remote changes.
//                        remoteCollection.sync().configure(
//                                DefaultSyncConflictResolvers.remoteWins(),
//                                new MyUpdateListener(),
//                                new MyErrorListener());
                            if (remoteCollection == null) {
                                Log.d("INFO", "Does this collection even exist?");
                            } else {
                                //...
                            }
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
    private File getOutputMediaFile() {

        String state = Environment.getExternalStorageState();

        if (!state.equals(Environment.MEDIA_MOUNTED)) {
            return null;
        } else {
            File folder_gui = new File(Environment.getExternalStorageDirectory() + File.separator + "GUI");
            if (!folder_gui.exists()) {
                folder_gui.mkdirs();
            }
            File outputFile = new File(folder_gui, "Temp.jpg");
            return outputFile;
        }
    }
    @SuppressLint("SetTextI18n")
    public void Recognize(View v) {

        if (camera != null) {
            Log.d("INFO", "Clicked");
            recognizeBtn.setEnabled(false);
            camera.takePicture(null, null, mPictureCallback);
            loading_anim.setVisibility(View.VISIBLE);
            results.setText("Processing...");
        }
    }
    Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            File picture_file = getOutputMediaFile();
            TempFile = getOutputMediaFile();

            if (picture_file == null) {
                return;
            } else {
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(picture_file);
                    fileOutputStream.write(data);
                    fileOutputStream.close();
                    camera.startPreview();
                    Solve();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    };
    private void Solve() {

        // capture the start time
        long startTime = System.nanoTime();

        if (TempFile!=null) {
            Detect(TempFile);
            if (faceId!=null) {
                Match();
            } else {
                // Print documents to the log.
                Log.i("INFO", "Face not enrolled");
                recognizeBtn.setEnabled(true);
                make_toast("No enrolled faces recognized");
                results.setText("");
                loading_anim.setVisibility(View.GONE);
            }
        }

        // capture the end time
        long endTime = System.nanoTime();
        // dump the execution time out
        Log.d("SOLVE_TIMER", String.valueOf((endTime -
                startTime)));

    }
    private void Detect(File file) {


        // capture the start time
        long startTime = System.nanoTime();


        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
            String collectionId = "collection0";
            ByteBuffer imageBytes = null;
            try {
               InputStream inputStream = new FileInputStream(file);
                imageBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputStream));
                //imageBytes.limit(50000);
            } catch (IOException e) {
                e.printStackTrace();
                recognizeBtn.setEnabled(true);
                results.setText("");
                loading_anim.setVisibility(View.GONE);
           }
            Image image = new Image()
                    .withBytes(imageBytes);
            try {
                SearchFacesByImageRequest searchFacesByImageRequest = new SearchFacesByImageRequest()
                        .withCollectionId(collectionId)
                        .withImage(image)
                        .withFaceMatchThreshold(85F)
                        .withMaxFaces(1);
                SearchFacesByImageResult searchFacesByImageResult =
                        rekognition.searchFacesByImage(searchFacesByImageRequest);
                List<FaceMatch> faceImageMatches = searchFacesByImageResult.getFaceMatches();


                DetectFacesRequest request = new DetectFacesRequest()
                        .withImage(image)
                        .withAttributes(String.valueOf(Attribute.ALL));
                // Replace Attribute.ALL with Attribute.DEFAULT to get default values.

                DetectFacesResult result = rekognition.detectFaces(request);
                List < FaceDetail > faceDetails = result.getFaceDetails();

                // Get the face inference
                for (FaceDetail face: faceDetails) {
                    if (request.getAttributes().contains("ALL")) {
                        AgeRange ageRange = face.getAgeRange();
                        Log.d("INFO", "The detected face is estimated to be between "
                                + ageRange.getLow().toString() + " and " + ageRange.getHigh().toString()
                                + " years old.");
                        Log.d("INFO","Here's the complete set of attributes:");
                    } else { // non-default attributes have null values.
                        Log.d("INFO","Here's the default set of attributes:");
                    }

                    Log.d("INFO", faceDetails.toString());
                }

                if (!faceImageMatches.isEmpty()) {
                    faceId = faceImageMatches.get(0).getFace().getFaceId();
                    results.setText("");
                    recognizeBtn.setEnabled(true);
                    loading_anim.setVisibility(View.GONE);
                } else {
                    faceId = null;
                    results.setText("");
                    recognizeBtn.setEnabled(true);
                    loading_anim.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("INFO", "There's no face in the image");
                recognizeBtn.setEnabled(true);
                results.setText("");
                loading_anim.setVisibility(View.GONE);
            }



            // capture the end time
            long endTime = System.nanoTime();
// dump the execution time out
            Log.d("DETECT_TIMER", String.valueOf((endTime -
                    startTime)));



        }
    }
    @SuppressLint("SetTextI18n")
    public void Match(){

        // capture the start time
        long startTime = System.nanoTime();

        Log.d("INFO", faceId);
        final Document doc = new Document("FaceID", faceId);
        final String jsonString = doc.toJson();
        final Document fltr = Document.parse(jsonString);
        try {
            remoteCollection.find()
                    .filter(fltr)
                    .limit(1)
                    .projection(new Document().append("Name", 1).append("id", 1).append("FaceID", 1))
                    .forEach(document -> {
                        // Print documents to the log.
                        name = document.getString("Name");
                        Log.d("INFO", name);
                    });
            make_toast(name);
            faceId = null;
        } catch (Exception e) {
            e.printStackTrace();
            results.setText("Could not connect to DB.");
        }

        // capture the end time
        long endTime = System.nanoTime();
        // dump the execution time out
        Log.d("MATCH_TIMER", String.valueOf((endTime -
                startTime)));

    }
    @Override
    protected void onMainServiceConnected() {

    }
    private void make_toast(String text) {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_LONG;

        Toast toast = Toast.makeText(context, text, duration);
        toast.setGravity(Gravity.CENTER, 0, 100);
        toast.show();
    }

}

