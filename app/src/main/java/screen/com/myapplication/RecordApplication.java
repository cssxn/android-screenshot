package screen.com.myapplication;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Daniel on 1/23/18.
 */

public class RecordApplication extends Application {
    private static RecordApplication application;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        application = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Start service
        startService(new Intent(this, RecordService.class));
    }

    public static RecordApplication getInstance() {
        return application;
    }
}
