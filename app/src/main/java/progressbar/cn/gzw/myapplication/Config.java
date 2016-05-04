package progressbar.cn.gzw.myapplication;

import android.content.Context;

/**
 * Created by gzw on 16-5-2.
 */
public class Config {
    public String mPrefix;
    public long mInterval;
    public Context context;
    public Config(Context context){
        this.context = context;
    }
    public Config setPrefix(String prefix){
        mPrefix = prefix;
        return this;
    }
    public Config setInterval(long interval){
        mInterval = interval;
        return this;
    }
}
