package com.example.facialrecognition;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferService;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.model.FaceRecord;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.IndexFacesRequest;
import com.amazonaws.services.rekognition.model.IndexFacesResult;
import com.amazonaws.services.rekognition.model.QualityFilter;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.facialrecognition.ShowCamera;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class EnrolActivity extends AppCompatActivity {

    //Variables
    private RequestQueue mQueue;
    ///Naming
    EditText NameEnter;
    TextView NameText;
    String alias;
    ///Camera
    Button Capture;
    Button OK;
    Button Reject;
    Camera camera;
    FrameLayout frame;
    ShowCamera showCamera;
    String ID;
    int counter;
    String Name;
    ///
    File TempFile;
    CognitoCachingCredentialsProvider Credentials;
    TextView photocounter;
    String FaceIdentity;
    //static final int REQUEST_TAKE_PHOTO = 1;
    //private String mCurrentPhotoPath;
    TextToSpeech t1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enrol);

        //Request Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        //Initializing Variables
        ///Naming
        NameEnter = findViewById(R.id.editText);
        NameText = findViewById(R.id.nametextView);
        camera = Camera.open(1);

        ///Camera
        Capture = findViewById(R.id.button);
        OK = findViewById(R.id.buttonok);
        OK.setVisibility(View.GONE);
        Reject = findViewById(R.id.buttonreject);
        Reject.setVisibility(View.GONE);

        photocounter = findViewById(R.id.counter);
        photocounter.setVisibility(View.GONE);

        frame = findViewById(R.id.frame);
        showCamera = new ShowCamera(this, camera);
        frame.addView(showCamera);
        //ame.setBackgroundColor(16777215);
        //frame.setVisibility(View.GONE);
        mQueue = Volley.newRequestQueue(this);
        //Credentials
        Credentials = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "<identity-pool-id>", // Identity pool ID
                Regions.US_EAST_1 // Region
        );

        // Need this for Transfer Service
        getApplicationContext().startService(new Intent(getApplicationContext(), TransferService.class));

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
                    Capture.setVisibility(View.GONE);
                    Reject.setVisibility(View.VISIBLE);
                    OK.setVisibility(View.VISIBLE);
                    //camera.startPreview();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    };

    private File getOutputMediaFile() {

        String state = Environment.getExternalStorageState();

        if (!state.equals(Environment.MEDIA_MOUNTED)) {
            return null;
        } else {
            File folder_gui = new File(Environment.getExternalStorageDirectory() + File.separator + "GUI");
            if (!folder_gui.exists()) {
                folder_gui.mkdirs();
            }
            File outputFile = new File(folder_gui, alias + ".jpg");
            return outputFile;
        }
    }

    //Entering a name for rekognition
    public void EnterName(View v) {
        alias = NameEnter.getText().toString();
        String display = "Hi " + alias;
        talk(display);
        NameText.setText(display);
        ID = random();
        try {
            postFaceId("createNewId");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void Capture(View v) {
        if (alias != null) {
            if (camera != null) {
                camera.takePicture(null, null, mPictureCallback);
            }
        } else {
            Toast toast = Toast.makeText(getApplicationContext(), "Please enter name", Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    public void OK(View v) {

        counter = counter + 1;

        Name = alias + counter;

        upload(TempFile);

        Reject.setVisibility(View.GONE);
        OK.setVisibility(View.GONE);
        Capture.setVisibility(View.VISIBLE);
        camera.startPreview();

    }

    public void Reject(View v) {

        TempFile.delete();

        Toast toast = Toast.makeText(getApplicationContext(), "Image Deleted", Toast.LENGTH_SHORT);
        toast.show();

        Reject.setVisibility(View.GONE);
        OK.setVisibility(View.GONE);
        Capture.setVisibility(View.VISIBLE);
        camera.startPreview();
    }

    public void upload(File FiletoUpload) {

        AmazonS3Client s3Client = new AmazonS3Client(Credentials);
        TransferUtility transferUtility = new TransferUtility(s3Client, this);
        TransferObserver observer = transferUtility.upload(
                "fr-module-faces/" + alias + "/" + ID,           // The S3 bucket to download from
                Name,          // The key for the object to download
                FiletoUpload,
                CannedAccessControlList.PublicRead //to make the file public// The name of the file to download
        );

        observer.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                if (state == TransferState.COMPLETED) {
                    Toast toast = Toast.makeText(getApplicationContext(), "Image sent", Toast.LENGTH_SHORT);
                    toast.show();
                    photocounter.setText("Photos taken:" + counter);
                    photocounter.setVisibility(View.VISIBLE);
                    try {
                        FaceIdentity = FaceIndex();
                        Log.d("Face",FaceIdentity);
                    }catch (IndexOutOfBoundsException e){
                        e.printStackTrace();
                    }
                    try {
                        postFaceId("addFace");
                    }catch(Exception e){
                        e.printStackTrace();
                    }



                }

            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {

            }

            @Override
            public void onError(int id, Exception ex) {

            }
        });

    }

    // Generates a random string
    protected String random() {
        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 18) { // length of the random string.
            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
            salt.append(SALTCHARS.charAt(index));
        }
        String saltStr = salt.toString();
        return saltStr;

    }

    String FaceID = null;
    public String FaceIndex() {
        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);

            String collectionId = "collection0";

            AmazonRekognition rekognitionClient = new AmazonRekognitionClient(Credentials);
            Image image = new Image()
                    .withS3Object(new S3Object()
                            .withBucket("fr-module-faces")
                            .withName(alias +"/"+ ID + "/"+ Name)
                    );

            IndexFacesRequest indexFacesRequest = new IndexFacesRequest()
                    .withImage(image)
                    .withQualityFilter(QualityFilter.AUTO)
                    .withMaxFaces(1)
                    .withCollectionId(collectionId)
                    .withExternalImageId(Name)
                    .withDetectionAttributes("DEFAULT");

            IndexFacesResult indexFacesResult = rekognitionClient.indexFaces(indexFacesRequest);
            List<FaceRecord> faceRecords = indexFacesResult.getFaceRecords();
            FaceID = faceRecords.get(0).getFace().getFaceId();
        }
        return FaceID;
    }

    private void postFaceId(String command) throws JSONException {

        String workingId = ID;
        String name = alias;
        String faceId = FaceIdentity;

        String url = "https://qye6l1tp50.execute-api.us-east-1.amazonaws.com/prod";
        JSONObject data = new JSONObject();
        data.put("workingId", workingId);
        data.put("Name", name);
        data.put("FaceID", faceId);
        data.put("command", command);
        //Posting data to url
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, data,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Context context = getApplicationContext();
                        Toast.makeText(context, "Server reached", Toast.LENGTH_LONG).show();
                        Log.d("RESPONSE", response.toString());
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("INFO", "Unable to reach server");
                error.printStackTrace();
            }

        });

        //This ensures that the request does not timeout before a response is received
        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                29000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        mQueue.add(jsonObjectRequest);

    }

    private void talk(String c) {
        Log.d("status", "tts called");
        final String toSpeak = c;

        t1 = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.UK);
                }
                t1.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
            }
        });

    }

    public void onPause(){
        if(t1 !=null){
            t1.stop();
            t1.shutdown();
        }
        super.onPause();
    }

}