package com.example.facialrecognition;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.List;

public class ShowCamera extends SurfaceView implements SurfaceHolder.Callback {
    Camera camera;
    SurfaceHolder holder;
    SurfaceTexture surfaceTexture =new SurfaceTexture(10);
    public ShowCamera(Context context, Camera camera) {
        super(context);
        this.camera = camera;
        holder = getHolder();
        holder.addCallback(this);

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        Camera.Parameters parameters = camera.getParameters();

        List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
        Camera.Size size = sizes.get(0);
        for(int i=0;i<sizes.size();i++)
        {
            if(sizes.get(i).width > size.width)
                size = sizes.get(i);
        }
        parameters.setPictureSize(size.width, size.height);
        parameters.setJpegQuality(100);


        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
        parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        parameters.setExposureCompensation(0);


        //Change orientation
        if (this.getResources().getConfiguration().orientation!= Configuration.ORIENTATION_LANDSCAPE){
            parameters.set("orientation","portrait");
            camera.setDisplayOrientation(90);
            parameters.setRotation(90);
        }
        else{
            parameters.set("orientation","landscape");
            camera.setDisplayOrientation(0);
            parameters.setRotation(0);

        }

        //camera.setParameters(parameters);
        try {
            camera.setPreviewDisplay(holder);
            //camera.setPreviewTexture(surfaceTexture);

            //camera.setPreviewTexture(surfaceTexture);
            camera.startPreview();

        }catch (Exception e){

        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        camera.stopPreview();
        camera.release();
    }


}

