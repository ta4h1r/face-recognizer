package com.example.facialrecognition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
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
import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.HandMotionManager;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.HeadMotionManager;
import com.sanbot.opensdk.function.unit.WaistMotionManager;
import com.sanbot.opensdk.function.unit.interfaces.hardware.PIRListener;
import com.sanbot.opensdk.function.unit.interfaces.media.MediaStreamListener;

import org.bson.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * function: Human detection, face recognition, Q&A session.
 * create date: 2020/6/3 08:07
 * @author taahir
 */

public class RecognizeActivity2 extends BindBaseActivity {

    private final static String TAG = MainActivity.class.getSimpleName();

    TextView tvStatus;
    TextView tvStatus2;
    TextView tvName;
    TextView tvGender;
    TextView tvBeard;
    TextView tvSmile;
    TextView tvAgeRange;
    TextView tvEmotions;
    ImageView ivCapture;
    Button btnStart;

    private static HandMotionManager handMotionManager;
    private static HeadMotionManager headMotionManager;
    private static WaistMotionManager waistMotionManager;
    private HDCameraManager hdCameraManager;
    private HardWareManager hardwareManager;

    private VisionMediaDecoder mediaDecoder;

    CognitoCachingCredentialsProvider credentials;
    AmazonRekognition rekognition;
    String faceId;

    StitchAppClient client;
    RemoteMongoClient remoteMongoClient;
    RemoteMongoDatabase db;
    RemoteMongoCollection<Document> remoteCollection;

    String userGender;
    String userBeard;
    String userSmile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        register(MainActivity.class);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_recognize);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);         // Prevent screen sleep
        getSupportActionBar().hide();                                                 // Hide the action bar at the top

        // Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        // Layout variables
        tvStatus = findViewById(R.id.tv_status);
        tvStatus2 = findViewById(R.id.tv_status2);
        ivCapture = findViewById(R.id.iv_capture);
        tvName = findViewById(R.id.tv_name);
        tvGender = findViewById(R.id.tv_gender);
        tvBeard = findViewById(R.id.tv_beard);
        tvAgeRange = findViewById(R.id.tv_age_range);
        tvEmotions = findViewById(R.id.tv_emotions);
        tvSmile = findViewById(R.id.tv_smile);
        btnStart = findViewById(R.id.btn_start);

        // Sanbot managers
        hdCameraManager = (HDCameraManager) getUnitManager(FuncConstant.HDCAMERA_MANAGER);
        hardwareManager =  (HardWareManager) getUnitManager(FuncConstant.HARDWARE_MANAGER);
        handMotionManager = (HandMotionManager) getUnitManager(FuncConstant.HANDMOTION_MANAGER);
        headMotionManager = (HeadMotionManager) getUnitManager(FuncConstant.HEADMOTION_MANAGER);
        waistMotionManager = (WaistMotionManager) getUnitManager(FuncConstant.WAIST_MANAGER);

        // Services
        awsLogin();                               // Rekcognition
        mongoLogin();                             // MongoDB

        // Hardware
        mediaDecoder = new VisionMediaDecoder();
        initCamListener();                                               // HD camera
        hardwareManager.setOnHareWareListener(new PIRListener() {
            @Override
            public void onPIRCheckResult(boolean isChecked, int part) {
                if (part == 1) {
                    Log.i("Tai", isChecked ? "Front PIR triggered" : "Front PIR off");
                    tvStatus.setText(isChecked ? "Front PIR triggered" : "Ready");
                } else {
                    Log.i("Tai", isChecked ? "Back PIR triggered" : "Back PIR off");
                    tvStatus2.setText(isChecked ? "Back PIR triggered" : "Back PIR off");
                }
                if (isChecked && part == 1) {
//                    conciergeWelcome();
                }
            }
        });  // PIR

        // Buttons
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                conciergeWelcome();
                btnStart.setEnabled(false);
            }
        });
        findViewById(R.id.btn_enrol).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent enrolSession = new Intent(RecognizeActivity2.this, EnrolActivity2.class);
                startActivity(enrolSession);
            }
        });

    }

    private void mongoLogin() {
        /** Connects to MongoDB */
        try {
            // Create the default Stitch Client
            client = Stitch.initializeDefaultAppClient("<stitch-app-id>");
            // Log-in using an Anonymous authentication provider from Stitch
            client.getAuth().loginWithCredential(new AnonymousCredential()).addOnCompleteListener(
                    new OnCompleteListener<StitchUser>() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void onComplete(@NonNull final Task<StitchUser> task) {
                            if (task.isSuccessful()) {
                                Log.d("Tai", String.format(
                                        "logged in as user %s with provider %s",
                                        task.getResult().getId(),
                                        task.getResult().getLoggedInProviderType()));
                                tvStatus.setText("Logged in to DB");
                            } else {
                                Log.e("Tai", "failed to log in", task.getException());
                            }

                            // Get db collection from remote client
                            remoteMongoClient = client.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas");
                            db = remoteMongoClient.getDatabase("fr-module");
                            remoteCollection = db.getCollection("fr_faces");

                            if (remoteCollection == null) {
                                Log.d("Tai", "Does this collection even exist?");
                            } else {
                                //...
                            }
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void awsLogin() {
        credentials = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "<identity-pool-id>", // Identity pool ID
                Regions.US_EAST_1 // Region
        );
        rekognition = new AmazonRekognitionClient(credentials);
    }
    private int mWidth, mHeight;
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

//        hdCameraManager.setMediaListener(new FaceRecognizeListener() {
//            @Override
//            public void recognizeResult(List<FaceRecognizeBean> list) {
//                StringBuilder sb = new StringBuilder();
//                for (FaceRecognizeBean bean : list) {
//                    sb.append(new Gson().toJson(bean));
//                    sb.append("\n");
//                }
//                tvFace.setText(sb.toString());
//            }
//        });
    }

    public void conciergeWelcome() {
        Log.d("Tai", "Concierge initiated");
        // Running facial rec in a worker thread after delay for image stability
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                solveFace();
            }
        }, 500);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvStatus.setText("Processing face...");
            }
        });
//        AnimatronicsClass.doNodNo();
    }
    private void solveFace() {
        /** Passes a stored image to FR engine (via Detect() method) and matches the faceId with a name in a Mongo DB (via Match() method) */
        prepareNewImage();                     // Capture and store a new image
        File tempFile = getOutputMediaFile();  // Retrieve the captured image file
        detectFace(tempFile);                      // Run detection
//        if(!img.sameAs(img0)) {
//            File tempFile = getOutputMediaFile();  // Retrieve the captured image file
//            detectFace(tempFile);                      // Run detection
//            img0 = img;
//        } else {
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    tvStatus.setText("There are no faces in the requested image");
//                }
//            });
//        }
    }

    private void matchFace() {

        // capture the method execution start time
        long startTime = System.nanoTime();

        // Make a filter to query the DB with
        final Document doc = new Document("FaceID", faceId);
        final String jsonString = doc.toJson();
        final Document fltr = Document.parse(jsonString);

        // Query the DB to find the name of an enrolled face
        String[] name = new String[1];
        try {
            remoteCollection.find()
                    .filter(fltr)
                    .limit(1)
                    .projection(new Document().append("Name", 1).append("id", 1).append("FaceID", 1))
                    .forEach(document -> {
                        // Print documents to the log.
                        name[0] = document.getString("Name");
                        Log.d("Tai", "faceId matched: " + name[0]);
                        runOnUiThread(new Runnable() {
                            @RequiresApi(api = Build.VERSION_CODES.O)
                            @Override
                            public void run() {
                                tvName.setText(name[0]);
                                btnStart.setEnabled(true);
                            }
                        });
                        String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
//                        qnaSession(name[0], currentTime, userSmile, userBeard);
                        Log.d("Tai", "name: " + name[0]);
                    });
            faceId = null;
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("Tai", "Caught exception in DB query.");
            btnStart.setEnabled(true);
        }

        // capture the end time
        long endTime = System.nanoTime();
        // dump the execution time out
        Log.i("MATCH_TIMER", String.valueOf((endTime -
                startTime)/1000000000));

    }
    private void detectFace(File tempFile) {
        // capture the start time
        long startTime = System.nanoTime();

        int SDK_INT = Build.VERSION.SDK_INT;
        if (SDK_INT > 8) {
//            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
//                    .permitAll().build();
//            StrictMode.setThreadPolicy(policy);
            String collectionId = "collection0";
            ByteBuffer imageBytes = null;
            try {
                InputStream inputStream = new FileInputStream(tempFile);
                imageBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputStream));
                //imageBytes.limit(50000);
            } catch (IOException e) {
                e.printStackTrace();
            }

            Image image = new Image()
                    .withBytes(imageBytes);
            try {
                SearchFacesByImageRequest searchFacesByImageRequest = new SearchFacesByImageRequest()
                        .withCollectionId(collectionId)
                        .withImage(image)
                        .withFaceMatchThreshold(90F)
                        .withMaxFaces(1);
                SearchFacesByImageResult searchFacesByImageResult =
                        rekognition.searchFacesByImage(searchFacesByImageRequest);
                List<FaceMatch> faceImageMatches = searchFacesByImageResult.getFaceMatches();

                // Worker thread gets facial inference data
                Thread inferenceWorker = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        DetectFacesRequest request = new DetectFacesRequest()
                                .withImage(image)
                                .withAttributes(String.valueOf(Attribute.ALL));
                        DetectFacesResult result = rekognition.detectFaces(request);
                        List<FaceDetail> faceDetails = result.getFaceDetails();
                        for (FaceDetail face : faceDetails) {
                            userGender = face.getGender().getValue();
                            userBeard = face.getBeard().getValue().toString();
                            userSmile = face.getSmile().getValue().toString();
                            int userAgeH = face.getAgeRange().getHigh();
                            int userAgeL = face.getAgeRange().getLow();
                            String userAgeRange = String.valueOf(userAgeL - userAgeH);

                            runOnUiThread(new Runnable() {
                                @SuppressLint("SetTextI18n")
                                @Override
                                public void run() {
                                    tvGender.setText("Gender: " + userGender);
                                    tvBeard.setText("Beard: " + userBeard);
                                    tvSmile.setText("Smile: " + userSmile);
                                    tvAgeRange.setText("Age Range: " + userAgeL + "-" + userAgeH);
                                    tvEmotions.setText(face.getEmotions().toString());
                                }
                            });

                            matchFace();
//                    // Run match after a delay for stability (i.e., allow faceId to update)
//                    new Timer().schedule(new TimerTask() {
//                        @Override
//                        public void run() {
//                            matchFace();                               // Match faceId with name in DB
//                        }
//                    }, 100);
                        }
                    }
                });
                inferenceWorker.start();

//                DetectFacesRequest request = new DetectFacesRequest()
//                        .withImage(image)
//                        .withAttributes(String.valueOf(Attribute.ALL));
//                DetectFacesResult result = rekognition.detectFaces(request);
//                List<FaceDetail> faceDetails = result.getFaceDetails();
//                for (FaceDetail face : faceDetails) {
////                    Log.i("Tai", face.getEmotions().toString());
//                    userGender = face.getGender().getValue();
//                    userBeard = face.getBeard().getValue().toString();
//                    userSmile = face.getSmile().getValue().toString();
//
//                    runOnUiThread(new Runnable() {
//                        @SuppressLint("SetTextI18n")
//                        @Override
//                        public void run() {
//                            tvGender.setText("Gender: " + userGender);
//                            tvBeard.setText("Beard: " + userBeard);
//                            tvSmile.setText("Smile: " + userSmile);
//                        }
//                    });
//
//                    // Run match after a delay for stability (i.e., allow faceId to update)
//                    new Timer().schedule(new TimerTask() {
//                        @Override
//                        public void run() {
//                            matchFace();                               // Match faceId with name in DB
//                        }
//                    }, 50);
//                }

                if (!faceImageMatches.isEmpty()) {
                    faceId = faceImageMatches.get(0).getFace().getFaceId();
                    Log.i("Tai", "Found faceId: " + faceId);
//                    // Run match after a delay for stability
//                    new Timer().schedule(new TimerTask() {
//                        @Override
//                        public void run() {
//                            matchFace();                               // Match faceId with name in DB
//                        }
//                    }, 200);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText(faceId);
                        }
                    });
                } else {
                    faceId = null;
                    Log.i("Tai", "Did not recognize face.");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("No matched faces");
                            tvName.setText("Unknown face");
                        }
                    });
                    String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                    Log.d("Tai", userGender);
//                    if (userGender.equals("Male")) {
//                        qnaSession("sir", currentTime, userSmile, userBeard);
//                    }
//                    else if (userGender.equals("Female")) {
//                        qnaSession("ma'am", currentTime, userSmile, userBeard);
//                    } else {
//                        qnaSession("friend", currentTime, userSmile, userBeard);
//                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Tai", "Rekognition caught exception: " + e);
                runOnUiThread(new Runnable() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void run() {
                        tvStatus.setText("Rekognition caught exception. Please make sure that there is an internet connection, and that there is at least one face in front of the robot");
                        btnStart.setEnabled(true);
                    }
                });
            }

            // capture the method execution end time
            long endTime = System.nanoTime();
            // dump the execution time out
            Log.i("DETECT_TIMER", String.valueOf((endTime -
                    startTime)/1000000000));
        }
    }

    private File getOutputMediaFile() {
        String state = Environment.getExternalStorageState();
        if (!state.equals(Environment.MEDIA_MOUNTED)) {
            return null;
        } else {
            File folder_gui = new File(Environment.getExternalStorageDirectory() + "/FACE_REG/IMG/" + "DCIM/");
//            if (!folder_gui.exists()) {
//                folder_gui.mkdirs();
//            }
            File outputFile = new File(folder_gui, "temp.jpg");
            return outputFile;
        }
    }
    private void prepareNewImage() {
        // Capture an image, store it, and display it
//        img = hdCameraManager.getVideoImage();
        storeImage(hdCameraManager.getVideoImage());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ivCapture.setImageBitmap(hdCameraManager.getVideoImage());
                Toast.makeText(getApplicationContext(), "Image captured", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void storeImage(Bitmap videoImage) {
        String dir = Environment.getExternalStorageDirectory() + "/FACE_REG/IMG/" + "DCIM/";
        final File f = new File(dir);
        if (!f.exists()) {
            f.mkdirs();
        }
        String fileName = "temp" + ".jpg";
        File file = new File(f, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            videoImage.compress(Bitmap.CompressFormat.JPEG, 10, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i("Tai", fileName + " stored");
    }

    // App methods
    @Override
    protected void onResume() {
        super.onResume();
        Log.i("Tai", "Resume");
        // Login to MongoDB again
        try {
            // Get the default mongo client
            client = Stitch.getDefaultAppClient();
            // Log-in using an Anonymous authentication provider from Stitch
            client.getAuth().loginWithCredential(new AnonymousCredential()).addOnCompleteListener(
                    new OnCompleteListener<StitchUser>() {
                        @Override
                        public void onComplete(@NonNull final Task<StitchUser> task) {
                            if (task.isSuccessful()) {
                                Log.d("Tai", String.format(
                                        "logged in as user %s with provider %s",
                                        task.getResult().getId(),
                                        task.getResult().getLoggedInProviderType()));
                            } else {
                                Log.e("Tai", "failed to log in", task.getException());
                            }

                            // Get db collection from remote client
                            remoteMongoClient = client.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas");
                            db = remoteMongoClient.getDatabase("fr-module");
                            remoteCollection = db.getCollection("fr_faces");

                            if (remoteCollection == null) {
                                Log.d("Tai", "Does this collection even exist?");
                            } else {
                                //...
                            }
                        }
                    });

        } catch (Exception e) {
            e.printStackTrace();
        }
        btnStart.setEnabled(true);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i("Tai", "Destroy");
    }
    @Override
    protected void onMainServiceConnected() {
        Log.i("Tai", "Main service connected");
        headMotionManager.doResetMotion();
        waistMotionManager.doResetMotion();
        handMotionManager.doResetMotion();
    }

}
