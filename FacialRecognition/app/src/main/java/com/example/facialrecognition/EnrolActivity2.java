package com.example.facialrecognition;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferService;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.model.FaceRecord;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.IndexFacesRequest;
import com.amazonaws.services.rekognition.model.IndexFacesResult;
import com.amazonaws.services.rekognition.model.QualityFilter;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.sanbot.opensdk.base.TopBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.beans.OperationResult;
import com.sanbot.opensdk.function.beans.StreamOption;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.interfaces.media.MediaStreamListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EnrolActivity2 extends TopBaseActivity implements SurfaceHolder.Callback {

    private final static String TAG = EnrolActivity.class.getSimpleName();

    private HDCameraManager hdCameraManager;

    EditText etName;
    TextView tvName;
    Button btnCapture;
    Button btnOk;
    Button btnReject;
    Button btnName;
    SurfaceView svMedia;
    ImageView ivCapture;
    TextView tvPhotocounter;

    private VisionMediaDecoder mediaDecoder;
    private List<Integer> handleList = new ArrayList<>();

    private int type = 1;

    String alias;
    String ID;
    String Name;
    int counter;
    File tempFile;
    String FaceIdentity;

    CognitoCachingCredentialsProvider credentials;

    private RequestQueue mQueue;
    private ProgressBar spinner;
    private ProgressBar spinner2;

    private AmazonS3 s3Client;
    private TransferUtility transferUtility;
    private TransferObserver observer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        register(EnrolActivity.class);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_enrol2);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   // The screen is always on
        getSupportActionBar().hide();

        // Request hardware permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        // Define layout variables
        etName = findViewById(R.id.et_name);
        tvName = findViewById(R.id.tv_name);
        btnCapture = findViewById(R.id.btn_capture);
        btnOk = findViewById(R.id.btn_ok);
        btnOk.setVisibility(View.GONE);
        btnReject = findViewById(R.id.btn_rej);
        btnReject.setVisibility(View.GONE);
        btnName = findViewById(R.id.namebutton);
        svMedia = findViewById(R.id.sv_media);
        ivCapture = findViewById(R.id.iv_capture);
        tvPhotocounter = findViewById(R.id.tv_photoCounter);
        spinner = (ProgressBar)findViewById(R.id.progressBar1);
        spinner.setVisibility(View.GONE);
        spinner2 = (ProgressBar)findViewById(R.id.progressBar2);
        spinner2.setVisibility(View.GONE);

        // Initialize Sanbot's managers
        hdCameraManager = (HDCameraManager) getUnitManager(FuncConstant.HDCAMERA_MANAGER);

        // AWS login
        credentials = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "<identity-pool-id>", // Identity pool ID
                Regions.US_EAST_1 // Region
        );
        initS3Handler();

        // AWS Transfer Service listener (for s3 interactions)
        getApplicationContext().startService(new Intent(getApplicationContext(), TransferService.class));

        // API requests queue
        mQueue = Volley.newRequestQueue(this);

        // Bind the camera feed to surface
        svMedia.getHolder().addCallback(this);

        // Listen in on that button
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ivCapture.setImageBitmap(hdCameraManager.getVideoImage());
                storeImage(hdCameraManager.getVideoImage());
                if (alias!= null) {
                    File picture_file = getOutputMediaFile();
                    tempFile = getOutputMediaFile();
                    if(picture_file != null) {
                        btnCapture.setVisibility(View.GONE);
                        btnReject.setVisibility(View.VISIBLE);
                        btnOk.setVisibility(View.VISIBLE);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Please enter name", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mediaDecoder = new VisionMediaDecoder();
        initCamListener();

    }
    private void initS3Handler() {
        s3Client = new AmazonS3Client(credentials, Region.getRegion(Regions.US_EAST_1));
        transferUtility = TransferUtility.builder()
                .context(getApplicationContext())
                .s3Client(s3Client)
                .build();
    }

    private File getOutputMediaFile() {
        String state = Environment.getExternalStorageState();
        if (!state.equals(Environment.MEDIA_MOUNTED)) {
            return null;
        } else {
            File dir = new File(Environment.getExternalStorageDirectory() + "/FACE_REG/IMG/" + "DCIM/");
            File outputFile = new File(dir, alias + ".jpg");
            return outputFile;
        }
    }
    private void storeImage(Bitmap bitmap) {
        String dir = Environment.getExternalStorageDirectory() + "/FACE_REG/IMG/" + "DCIM/";
        final File f = new File(dir);
        if (!f.exists()) {
            f.mkdirs();
        }
        String fileName = alias + ".jpg";
        File file = new File(f, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 10, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("Tai", "Failed file storage");
        }
    }
    private int mWidth, mHeight;
    /**
     * Initialize the camera listener
     */
    private void initCamListener() {
        hdCameraManager.setMediaListener(new MediaStreamListener() {
            @Override
            public void getVideoStream(int handle, byte[] bytes, int width, int height) {
                if (mediaDecoder != null) {
                    if (width != mWidth || height != mHeight) {
                        mediaDecoder.onCreateCodec(width, height);
                        mWidth = width;
                        mHeight = height;
                    }
                    mediaDecoder.drawVideoSample(ByteBuffer.wrap(bytes));
                    Log.i(TAG, "getVideoStream: Video data:" + bytes.length);
                }
            }

            @Override
            public void getAudioStream(int i, byte[] bytes) {

            }
        });
    }

    @Override
    protected void onMainServiceConnected() {

    }

    // Button click methods called from layout.xml
    public void onNameEnterClick(View view) {
        counter = 0;
        alias = etName.getText().toString();
        ID = randomStringGenerator();
        try {
            postFaceId("createNewId");
            btnName.setVisibility(View.GONE);
            spinner.setVisibility(View.VISIBLE);
        } catch (JSONException e) {
            e.printStackTrace();
            btnName.setVisibility(View.VISIBLE);
            spinner.setVisibility(View.GONE);
        }
    }
    public void onOkClick(View view) {
        counter = counter + 1;
        Name = alias + "_" + counter;
        upload(tempFile);
        btnReject.setVisibility(View.GONE);
        btnOk.setVisibility(View.GONE);
        spinner2.setVisibility(View.VISIBLE);
    }
    public void onRejectClick(View view) {
        tempFile.delete();
        Toast toast = Toast.makeText(getApplicationContext(), "Image discarded", Toast.LENGTH_SHORT);
        toast.show();
        btnReject.setVisibility(View.GONE);
        btnOk.setVisibility(View.GONE);
        btnCapture.setVisibility(View.VISIBLE);
        btnCapture.setEnabled(true);
    }

    private String randomStringGenerator() {
        String SALTCHARS = "abcdefghijklmnopqrstuvwxyz1234567890";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 18) { // length of the random string.
            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
            salt.append(SALTCHARS.charAt(index));
        }
        String saltStr = salt.toString();
        return saltStr;
    }
    private void upload(File tempFile) {
        Log.d("Tai", tempFile.getAbsolutePath());
        observer = transferUtility.upload(
                "fr-module-faces/" + alias + "/" + ID,           // The S3 bucket to upload to
                Name,                                                   // The key for the object to upload
                tempFile,                                               // The local file to upload
                CannedAccessControlList.PublicRead                      // To make the file public
        );
        observer.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                if (state == TransferState.COMPLETED) {
                    Toast toast = Toast.makeText(getApplicationContext(), "Image uploaded", Toast.LENGTH_LONG);
                    toast.show();
                    tvPhotocounter.setText("Photos taken:" + counter);
                    tvPhotocounter.setVisibility(View.VISIBLE);
                    try {
                        FaceIdentity = FaceIndex();
                        Log.d("Face",FaceIdentity);
                    } catch (IndexOutOfBoundsException e){
                        e.printStackTrace();
                    }
                    try {
                        postFaceId("addFace");
                    } catch(Exception e){
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
                        try {
                            String serverMsg = response.getString("message");
                            Toast.makeText(context, "serverMsg: " + serverMsg, Toast.LENGTH_LONG).show();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvName.setText("Alias added: \"" + alias + "\".");
                                    spinner.setVisibility(View.GONE);
                                    spinner2.setVisibility(View.GONE);
                                    btnName.setVisibility(View.VISIBLE);
                                    btnCapture.setVisibility(View.VISIBLE);
                                    btnCapture.setEnabled(true);
                                }
                            });
                            Log.d("Tai", "postFaceId: response message: " + response.toString());
                        } catch (JSONException e) {
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    spinner.setVisibility(View.GONE);
                                    spinner2.setVisibility(View.GONE);
                                    btnName.setVisibility(View.VISIBLE);
                                    btnCapture.setVisibility(View.VISIBLE);
                                    btnCapture.setEnabled(true);
                                }
                            });
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("Tai", "Request timeout");
                Toast.makeText(getApplicationContext(), "Server unreachable", Toast.LENGTH_LONG).show();
                error.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        spinner.setVisibility(View.GONE);
                        spinner2.setVisibility(View.GONE);
                        btnCapture.setVisibility(View.VISIBLE);
                        btnCapture.setEnabled(true);
                    }
                });
            }

        });

        //This ensures that the request does not timeout before a response is received
        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                10000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        mQueue.add(jsonObjectRequest);
    }

    String FaceID = null;
    private String FaceIndex() {
        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);

            String collectionId = "collection0";

            AmazonRekognition rekognitionClient = new AmazonRekognitionClient(credentials);
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
                    .withExternalImageId(Name.replace(" ", ""))   // Using .replace() to remove whitespaces
                    .withDetectionAttributes("DEFAULT");

            IndexFacesResult indexFacesResult = rekognitionClient.indexFaces(indexFacesRequest);
            List<FaceRecord> faceRecords = indexFacesResult.getFaceRecords();
            FaceID = faceRecords.get(0).getFace().getFaceId();
        }
        return FaceID;
    }

    // SurfaceHolder.Callback
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        //Set parameters and turn on media streaming
        StreamOption streamOption = new StreamOption();
        if (type == 1) {
            streamOption.setChannel(StreamOption.MAIN_STREAM);
        } else {
            streamOption.setChannel(StreamOption.SUB_STREAM);
        }
        streamOption.setDecodType(StreamOption.HARDWARE_DECODE);
        streamOption.setJustIframe(false);
        OperationResult operationResult = hdCameraManager.openStream(streamOption);
        Log.i(TAG, "surfaceCreated: operationResult=" + operationResult.getResult());
        int result = Integer.valueOf(operationResult.getResult());
        if (result != -1) {
            handleList.add(result);
        }
        mediaDecoder.setSurface(holder.getSurface());
    }
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed: ");
        //Turn off media streaming
        if (handleList.size() > 0) {
            for (int handle : handleList) {
                Log.i(TAG, "surfaceDestroyed: " + hdCameraManager.closeStream(handle));
            }
        }
        handleList = null;
        mediaDecoder.stopDecoding();
    }
}
