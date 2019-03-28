package com.example.hdy.camera2;

import android.Manifest;
import android.app.assist.AssistStructure;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.*;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author laiyu
 */
public class MainActivity extends AppCompatActivity {
    public static final String TAG = "[lylog]";
    private static final int REQUESCODE =100;
    private static final String[] CAMERA_PERMISSION ={"Manifest.permission.CAMERA"};

    TextureView mTextureView;
    static Bitmap mBitmap;
    ImageButton mCapture;
    CameraManager mCameraManager;
    String mCameraId;
    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private Handler  mainHandler,childHandler;
    private CameraCaptureSession mCameraCaptureSession;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private CameraCharacteristics mCharacteristics;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CaptureRequest mCaptureRequest;
    private Size mCaptureSize;
    private Size mPreviewSize;
    private SurfaceView mSurfaceView;
    private SurfaceHolder surfaceHolder;
    private String mCameraID;
    private CaptureRequest.Builder previewRequestBuilder;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        mSurfaceView = findViewById(R.id.surfaceview);
        mCapture = findViewById(R.id.imageButton);
        mCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
                takePreview();
            }
        });
        mCapture.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Intent i = new Intent(getApplicationContext(), Main2Activity.class);
                startActivity(i);
                return true;
            }
        });
        surfaceHolder = mSurfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                initCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }

    private void initCamera() {
        Log.d(TAG, "initCamera: ");
        mCameraID = "" + CameraCharacteristics.LENS_FACING_BACK;
        HandlerThread handlerThread=new HandlerThread("Camera2");
        handlerThread.start();
        childHandler=new Handler(handlerThread.getLooper());
        mainHandler=new Handler(getMainLooper());
        mImageReader = ImageReader.newInstance(mSurfaceView.getWidth(), mSurfaceView.getHeight(), ImageFormat.JPEG, 1);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, "capture ok");
                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                mBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
        }, mainHandler);


        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "initCamera: permission deny");
                return;
            }
            //打开摄像头
            Log.d(TAG, "initCamera: open 0");
            mCameraManager.openCamera(mCameraID, stateCallback, mainHandler);
        } catch (CameraAccessException e) {
            Log.d(TAG, "CameraAccessException: ");
            e.printStackTrace();
        }
    }
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {//打开摄像头
            mCameraDevice = camera;
            //开启预览
            takePreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {//关闭摄像头
            if (null != mCameraDevice) {
                mCameraDevice.close();
                MainActivity.this.mCameraDevice = null;
            }
        }

        @Override
        public void onError(CameraDevice camera, int error) {//发生错误
            Toast.makeText(MainActivity.this, "摄像头开启失败", Toast.LENGTH_SHORT).show();
        }
    };

    private void takePreview() {
        try {
            // 创建预览需要的CaptureRequest.Builder
            previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 将SurfaceView的surface作为CaptureRequest.Builder的目标
            previewRequestBuilder.addTarget(surfaceHolder.getSurface());
            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求
            mCameraDevice.createCaptureSession(Arrays.asList(surfaceHolder.getSurface(), mImageReader.getSurface()), new CameraCaptureSession.StateCallback()
            {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    if (null == mCameraDevice) {
                        return;
                    }
                    // 当摄像头已经准备好时，开始显示预览
                    mCameraCaptureSession = cameraCaptureSession;
                    try {
                        // 自动对焦
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        // 打开闪光灯
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        // 显示预览
                        CaptureRequest previewRequest = previewRequestBuilder.build();
                        mCameraCaptureSession.setRepeatingRequest(previewRequest, null, childHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "配置失败", Toast.LENGTH_SHORT).show();
                }
            }, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 拍照
     */
    private void takePicture() {
        if (mCameraDevice == null) {
            return;
        }
        // 创建拍照需要的CaptureRequest.Builder
        final CaptureRequest.Builder captureRequestBuilder;
        try {
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            // 自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 自动曝光
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            // 获取手机方向
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            // 根据设备方向计算设置照片的方向
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            //拍照
            CaptureRequest mCaptureRequest = captureRequestBuilder.build();
            mCameraCaptureSession.capture(mCaptureRequest, null, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

//    private void init() {
//        mTextureView = findViewById(R.id.surfaceview);
//
//        mCapture = findViewById(R.id.imageButton);
//        mCapture.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                takePicture();
//            }
//        });
//        mCapture.setOnLongClickListener(new View.OnLongClickListener() {
//            @Override
//            public boolean onLongClick(View view) {
//                Intent i = new Intent(getApplicationContext(), Main2Activity.class);
//                startActivity(i);
//                return true;
//            }
//        });
//        HandlerThread handlerThread = new HandlerThread("Camera2");
//        handlerThread.start();
//        childHandler  = new Handler(handlerThread.getLooper());
//        mainHandler = new Handler(getMainLooper());
//        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//        mTextureView.setSurfaceTextureListener(textureListener);
//    }
//
//    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener(){
//        @Override
//        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
//            setupCamera(width,height);
//            initCameraAndPreview();
//        }
//
//        @Override
//        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
//
//        }
//
//        @Override
//        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
//            if (null != mCameraDevice) {
//                Log.d(TAG, "surfaceDestroyed: mCameraDevice close");
//                mCameraDevice.close();
//                mCameraDevice = null;
//            }
//            return false;
//        }
//
//        @Override
//        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
//
//        }
//    };
//
//    private void setupCamera(int width, int height) {
//        //获取摄像头的管理者CameraManager
//        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//        try {
//            //遍历所有摄像头
//            for (String cameraId : manager.getCameraIdList()) {
//                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
//                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
//                //此处默认打开后置摄像头
//                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
//                    continue;
//                }
//                //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
//                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//                assert map != null;
//                //根据TextureView的尺寸设置预览尺寸
//                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
//                //获取相机支持的最大拍照尺寸
//                mCaptureSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {
//                    @Override
//                    public int compare(Size lhs, Size rhs) {
//                        return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth());
//                    }
//                });
//                //此ImageReader用于拍照所需
//                setupImageReader();
//                mCameraId = cameraId;
//                break;
//            }
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
//        List<Size> sizeList = new ArrayList<>();
//        for (Size option : sizeMap) {
//            if (width > height) {
//                if (option.getWidth() > width && option.getHeight() > height) {
//                    sizeList.add(option);
//                }
//            } else {
//                if (option.getWidth() > height && option.getHeight() > width) {
//                    sizeList.add(option);
//                }
//            }
//        }
//        if (sizeList.size() > 0) {
//            return Collections.min(sizeList, new Comparator<Size>() {
//                @Override
//                public int compare(Size lhs, Size rhs) {
//                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
//                }
//            });
//        }
//        return sizeMap[0];
//    }
//
//    private void setupImageReader() {
//        //2代表ImageReader中最多可以获取两帧图像流
//        mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(), ImageFormat.YV12, 1);
//        Log.d(TAG, "mImageReader: "+mImageReader);
//        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
//            @Override
//            public void onImageAvailable(ImageReader reader) {
//                Log.d(TAG, "onImageAvailable: ");
//                childHandler.post(new imageSaver(reader.acquireNextImage()));
//
//                Image image = reader.acquireNextImage();
//                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
//                byte[] bytes = new byte[buffer.remaining()];
//                buffer.get(bytes);
//                mBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
//            }
//        }, mainHandler);
//    }
//
//    private void initCameraAndPreview() {
//        Log.d(TAG, "init camera and preview");
//        try {
//            mCameraId = "" + CameraCharacteristics.LENS_FACING_FRONT;
//            mCharacteristics =  mCameraManager.getCameraCharacteristics(mCameraId);
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(this,CAMERA_PERMISSION,REQUESCODE);
//                Log.d(TAG, "permission is request ");
//            }else{
//                Log.d(TAG, "opencamera main ");
//                mCameraManager.openCamera(mCameraId, DeviceStateCallback, childHandler);
//            }
//        } catch (CameraAccessException e) {
//            Log.e(TAG, "open camera failed." + e.getMessage());
//        }
//    }
//    @Override
//    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        Log.d(TAG, "onRequestPermissionsResult: requestCode ="+requestCode);
//        if (requestCode == REQUESCODE) {
//            Log.d(TAG, "grantResults[0] "+grantResults[0]);
//            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // Permission Granted
//                try {
//                    Log.d(TAG, "opencamera first ");
//                    mCameraManager.openCamera(mCameraId, DeviceStateCallback, childHandler);
//                } catch (CameraAccessException e) {
//                    e.printStackTrace();
//                } catch (SecurityException e) {
//                    e.printStackTrace();
//                }
//            } else {
//                Log.d(TAG, "permission is deny ");
//            }
//        }
//    }
//
//    private void takePreview() {
//        // 创建预览需要的CaptureRequest.Builder
//        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
//        //设置TextureView的缓冲区大小
//        mSurfaceTexture.setDefaultBufferSize(mTextureView.getWidth(), mTextureView.getHeight());
//        //获取Surface显示预览数据
//        Surface previewSurface = new Surface(mSurfaceTexture);
//        try {
//            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            mCaptureRequestBuilder.addTarget(previewSurface);
//            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback()
//            {
//                @Override
//                public void onConfigured(CameraCaptureSession session) {
//                    if (null == mCameraDevice) {
//                        return;
//                    }
//                    // 当摄像头已经准备好时，开始显示预览
//                    try {
//                        mCaptureRequest = mCaptureRequestBuilder.build();
//                        mCameraCaptureSession = session;
//                        mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, childHandler);
//                        Log.d(TAG, "preview is ok ");
//                    } catch (CameraAccessException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//                @Override
//                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
//                    Toast.makeText(getApplicationContext(), "配置失败", Toast.LENGTH_SHORT).show();
//                }
//            }, childHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * 拍照
//     */
//    private void takePicture() {
//        if (mCameraDevice == null) {
//            return;
//        }
//        try {
//            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
//            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), mCaptureCallback, childHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//
//
//    }
//
//
//    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
//        @Override
//        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
//        }
//
//        @Override
//        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
//            capture();
//            try {
//                mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, childHandler);
//            } catch (CameraAccessException e) {
//                e.printStackTrace();
//            }
//        }
//    };
//
//    private void capture() {
//        try {
//            final CaptureRequest.Builder mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
//            int rotation = getWindowManager().getDefaultDisplay().getRotation();
//            mCaptureBuilder.addTarget(mImageReader.getSurface());
//            mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
//            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
//                @Override
//                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
//                    Toast.makeText(getApplicationContext(), "Image Saved!", Toast.LENGTH_SHORT).show();
//                    unLockFocus();
//                }
//            };
//            mCameraCaptureSession.stopRepeating();
//            mCameraCaptureSession.capture(mCaptureBuilder.build(), CaptureCallback, null);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void unLockFocus() {
//        try {
//            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
//            mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, childHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected final CameraDevice.StateCallback DeviceStateCallback = new CameraDevice.StateCallback(){
//
//        @Override
//        public void onOpened(@NonNull CameraDevice camera) {
//            Log.d("lylog","DeviceStateCallback:camera was opend.");
//            mCameraDevice = camera;
//            takePreview();
//        }
//
//        @Override
//        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
//
//        }
//
//        @Override
//        public void onError(@NonNull CameraDevice cameraDevice, int i) {
//
//        }
//    };
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (mCameraCaptureSession != null) {
//            mCameraCaptureSession.close();
//            mCameraCaptureSession = null;
//        }
//
//        if (mCameraDevice != null) {
//            mCameraDevice.close();
//            mCameraDevice = null;
//        }
//
//        if (mImageReader != null) {
//            mImageReader.close();
//            mImageReader = null;
//        }
//    }
//
//    private class imageSaver implements Runnable {
//        private Image mImage;
//
//        public imageSaver(Image image) {
//            mImage = image;
//        }
//
//        @Override
//        public void run() {
//            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
//            byte[] data = new byte[buffer.remaining()];
//            buffer.get(data);
//            String path = Environment.getExternalStorageDirectory() + "/DCIM/CameraV2/";
//            File mImageFile = new File(path);
//            if (!mImageFile.exists()) {
//                mImageFile.mkdir();
//            }
//            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
//            String fileName = path + "IMG_" + timeStamp + ".jpg";
//            FileOutputStream fos = null;
//            try {
//                fos = new FileOutputStream(fileName);
//                fos.write(data, 0, data.length);
//            } catch (IOException e) {
//                e.printStackTrace();
//            } finally {
//                if (fos != null) {
//                    try {
//                        fos.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }
//    }
}
