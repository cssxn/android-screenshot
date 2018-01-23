package screen.com.myapplication;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;

public class RecordService extends Service {


    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader mImageReader;

    private boolean running;
    private int width = 720;
    private int height = 1080;
    private int dpi;

    final String TAG = "test";

    @Override
    public IBinder onBind(Intent intent) {
        return new RecordBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread serviceThread = new HandlerThread("service_thread", Process.THREAD_PRIORITY_BACKGROUND);
        serviceThread.start();
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
                }
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
        String pathImage = Environment.getExternalStorageDirectory().getPath()+"/Pictures/";

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
}
