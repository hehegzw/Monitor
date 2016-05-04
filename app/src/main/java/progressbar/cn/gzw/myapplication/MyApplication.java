package progressbar.cn.gzw.myapplication;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by gzw on 16-5-2.
 */
public class MyApplication extends Application{
    private static Context context;
    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        final Monitor track = Monitor.getInstance();
        Config config = new Config(context).setInterval(1000).setPrefix("log");
        track.init(config);
        registerActivityLifecycleCallbacks(new ActivityLifecycle(){
            @Override
            public void onActivityCreated(Activity activity, Bundle bundle) {
                super.onActivityCreated(activity, bundle);
                String name = getgAtyname(activity);
                track.record("activity",name);
            }
        });

    }
    private String getgAtyname(Activity activity){
        String fullName = activity.getClass().getName();
        String[] names = fullName.split("\\.");
        String name = names[names.length-1];
        return name;
    }
    public static Context getAppContext(){
        return context;
    }
}
