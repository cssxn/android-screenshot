package screen.com.myapplication;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;


public class SystemUtils {

    // 判断Wifi网络是否可用
    public static boolean isWifiConnected(Context context) {
        if (context != null) {
            // 获取手机所有连接管理对象(包括对wi-fi,net等连接的管理)
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            // 获取NetworkInfo对象
            NetworkInfo networkInfo = manager.getActiveNetworkInfo();
            //判断NetworkInfo对象是否为空 并且类型是否为WIFI
            if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI)
                return networkInfo.isAvailable();
        }

        return false;
    }

    // 获取设备ID
    public static String getDeivceId(Context context){
        return Settings.System.getString(context.getContentResolver(), Settings.System.ANDROID_ID);
    }

    // 设备厂商
    public static String getDeivceBrand(){
        return Build.BRAND;
    }

    // 设备型号
    public static String getSystemModel(){
        return Build.MODEL;
    }

    // android版本
    public static String getSystemVersion(){
        return android.os.Build.VERSION.RELEASE;
    }


}
