package progressbar.cn.gzw.myapplication;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import progressbar.cn.gzw.myapplication.net.HttpUtil;

/**
 * Created by gzw on 16-5-3.
 */
public class Monitor {
    private static Monitor monitor;
    private static String deviceId;
    public static int WRITE_FILE = 1;
    public static int READ_FILE = 2;
    public static int LOG = 3;
    public static int MANIFEST = 4;
    private static String mRootPath;
    private static File mManifestFile;
    private String mPrefix;
    private static File currentFile;
    private long duration = 604800000;
    private HandThread handThread;
    private List<FileData> files;
    private List<Integer> deleteFile;
    private int index;
    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            sendToHandler((String) msg.obj,msg.arg1);
        }
    };

    private Monitor() {
    }

    public static Monitor getInstance() {
        if (monitor == null){
            monitor = new Monitor();
        }else{
            String lastFile = getLastFile();
            if(lastFile!=null){
                currentFile = new File(lastFile);
            }
        }

        return monitor;
    }

    public void startCheck() {
        Message msg = handThread.handler.obtainMessage();
        msg.what = READ_FILE;
        handThread.handler.sendMessageDelayed(msg, 5 * 1000);
    }

    public void init(Config config) {
        handThread = new HandThread();
        files = new ArrayList<>();
        deleteFile = new ArrayList<>();
        handThread.start();
        mPrefix = config.mPrefix;
        mRootPath = getmRootPath(config.context) + "/track/";
        String lastFile = getLastFile();
        if(lastFile!=null){
            currentFile = new File(lastFile);
        }
        //get devicedId
        StringBuilder builder = new StringBuilder();
        TelephonyManager tm = (TelephonyManager) config.context.getSystemService(Context.TELEPHONY_SERVICE);
        String imei = tm.getDeviceId();
        String imsi = tm.getSubscriberId();
        WifiManager wifi = (WifiManager) config.context.getSystemService(Context.WIFI_SERVICE);
        String macAddress = wifi.getConnectionInfo().getMacAddress();
        boolean hasNull = false;
        if(!StringUtil.isEmpty(imei)){
            builder.append(imei);
        }else{
            hasNull = true;
        }
        if(!StringUtil.isEmpty(imsi)){
            builder.append(imsi);
        }else{
            hasNull = true;
        }
        if(!StringUtil.isEmpty(macAddress)){
            macAddress = macAddress.replace(":","");
            builder.append(macAddress);
        }else{
            hasNull = true;
        }
        deviceId = builder.toString();
        if (hasNull){
            String uuid = UUID.randomUUID().toString();
            uuid = uuid.replace("-","");
            deviceId+=uuid;
            Log.d("deviceId",deviceId+deviceId.length());
        }
        deviceId = getMD5(deviceId);
        deviceId = deviceId.substring(0,deviceId.length()/2);
        deviceId = Base64.encodeToString(deviceId.getBytes(),Base64.DEFAULT);
        deviceId = deviceId.replace("-","");
        Log.d("deviceId",deviceId);
    }

    class HandThread extends Thread {
        public Handler handler;

        @Override
        public void run() {
            super.run();
            Looper.prepare();
            handler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);

                    if (msg.what == WRITE_FILE) {
                        int type = msg.arg1;
                        if (type == LOG) {
                            writeToLog((String) msg.obj);
                        } else {
                            writeToManifest((FileData) msg.obj);
                        }
                    } else if (msg.what == READ_FILE) {
                        if (!isNetworkAvailable()) return;
                        getAllFile();
                        uploadToServer("all");
                        Message msgs = handThread.handler.obtainMessage();
                        msgs.what = READ_FILE;
                        //Log.d("track","check");
                        handThread.handler.sendMessageDelayed(msgs, 1000 * 60*3);
                    }
                }
            };
            startCheck();
            Looper.loop();
        }

    }
    //---------------logic---------------------------------------

    public void record(String tag, String arg0) {
        String buffer = getJson(tag, arg0, null).toString() + "\n";
        sendToHandler(buffer, LOG);
    }

    public void record(String tag, String arg0, String arg1) {
        String buffer = getJson(tag, arg0, arg1).toString() + "\n";
        sendToHandler(buffer, LOG);
    }

    private void writeToLog(String Content) {
        BufferedWriter out = null;
        try {
            if (currentFile == null || !currentFile.exists()) {
                currentFile = new File(createFile().filePath);
                Log.d("detest", "chuangjianwenjian");
            }
            FileWriter fw = new FileWriter(currentFile, true);
            out = new BufferedWriter(fw);
            out.write(Content);
            out.flush();
            if (isBig(currentFile)) {
                uploadToServer("signl");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void writeToManifest(FileData fileData) {
        BufferedWriter out = null;
        Log.d("writeToManifest","writeToManifest");
        try {
            if (mManifestFile == null || !mManifestFile.exists()) {
                createManifest();
            }
            FileWriter fw = new FileWriter(mManifestFile, true);
            out = new BufferedWriter(fw);
            String buffer = fileData.filePath + "," + 0 + "," + fileData.time+","+fileData.isCanWrite + "\n";
            out.write(buffer);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private FileData createFile() {
        try {
            String currentFileName = mRootPath + mPrefix + System.currentTimeMillis() + ".txt";
            currentFile = new File(currentFileName);
            BufferedWriter out = null;
            if (!currentFile.exists()){
                currentFile.createNewFile();
                JSONObject json = new JSONObject();
                json.put("deviceId",deviceId);
                String content = json.toString().replace("\n","");
                FileWriter fw = new FileWriter(currentFile, true);
                out = new BufferedWriter(fw);
                out.write(content+"\n");
                out.flush();
                out.close();
            }
            FileData fileData = new FileData(currentFileName, 0, System.currentTimeMillis(),true);
            mManifestFile = new File(mRootPath + "manifest.txt");
            Log.d("writeToManifest", "ex:" +mManifestFile.getAbsolutePath() +"can:" + mManifestFile.canRead());
            if (!mManifestFile.exists()) {
                createManifest();
                Log.d("writeToManifest","createNewFile");
            }
            writeToManifest(fileData);
            return fileData;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private FileData createManifest() {
        try {
            mManifestFile = new File(mRootPath + "manifest.txt");
            if (!mManifestFile.exists()) {
                mManifestFile.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void uploadToServer(String type) {
        currentFile = null;
        HttpUtil httpUtil = HttpUtil.getInstence();
        int count = files.size() - 1;
        File file;
        Map<String, File> fileMap = new HashMap<>();
        if (!type.equals("signl")) {
            count = 0;
        }
        for (int i = files.size() - 1; i >= count; i--) {
            index = i;
            if (!isCanUpload(files.get(i))) {
                deleteFile.add(i);
                continue;
            } else {
                file = new File(files.get(i).filePath);
                fileMap.put("file", file);
                httpUtil.uploadFile("http://192.168.0.107:8080/UploadFile/UploadFile", null, fileMap, new HttpUtil.Success() {
                    @Override
                    public void success(String response) {
                        if(index == files.size()-1) currentFile = null;
                        deleteFile.add(index);
                        if (index == 0) {
                            resetManifest();
                        }
                    }
                }, new HttpUtil.Failure() {
                    @Override
                    public void failure(String error) {
                        files.get(index).repeatCount++;
                        files.get(index).isCanWrite = false;
                        if (index == 0) resetManifest();
                        if(index == files.size()-1) currentFile = null;
                    }
                });
            }
        }
    }

    private boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) return file.delete();
        return false;
    }

    private void resetManifest() {
        mManifestFile.delete();
        mManifestFile = new File(mRootPath + "manifest.txt");
        if (!mManifestFile.exists()) {
            try {
                mManifestFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for (int i = 0; i < files.size(); i++) {
            for(int j=0;j<deleteFile.size();j++){
                if(i == deleteFile.get(j)){
                    if(!deleteFile(files.get(i).filePath)){
                        writeToManifest(files.get(i));
                    }
                }else{
                    writeToManifest(files.get(i));
                }
            }

        }
        files.clear();
        deleteFile.clear();
    }

    private List<FileData> getAllFile() {
        BufferedReader reader;
        try {
            FileReader fileReader = new FileReader(mManifestFile);
            reader = new BufferedReader(fileReader);
            String string;
            while ((string = reader.readLine()) != null) {
                String[] datas = string.split(",");
                if(datas[2].equals("true")){
                    files.add(new FileData(datas[0], Integer.valueOf(datas[1]), Long.valueOf(datas[2]),true));
                }else{
                    files.add(new FileData(datas[0], Integer.valueOf(datas[1]), Long.valueOf(datas[2]),false));
                }

            }
            return files;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //---------------------tools----------------------------------
    private String getmRootPath(Context context) {
        if ((Environment.MEDIA_MOUNTED.endsWith(Environment.getExternalStorageState())) ||
                !Environment.isExternalStorageRemovable() && null != context.getExternalFilesDir(null)) {
            return context.getExternalFilesDir(null).getPath();
        } else {
            return context.getFilesDir().getPath();
        }
    }

    private JSONObject getJson(String tag, String arg0, String arg1) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", tag);
            json.put("activity", arg0);
            if (arg1 != null)
                json.put("weiget", arg1);
            return json;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean isBig(File file) {
        if (file.length() > 10 * 1024) {
            return true;
        }
        return false;
    }

    private boolean isCanUpload(FileData fileData) {
        long time = System.currentTimeMillis();
        if ((time - fileData.time) > duration || fileData.repeatCount > 10) {
            return false;
        }
        return true;
    }

    private void sendToHandler(String buffer, int type) {
        Message msg = handler.obtainMessage();
        msg.what = WRITE_FILE;
        msg.arg1 = type;
        msg.obj = buffer;
        if(handThread.handler == null){
           handler.sendMessageDelayed(msg,2000);
        }else{
            handThread.handler.sendMessage(msg);
        }

    }

    public static boolean isNetworkAvailable() {
        Context context = MyApplication.getAppContext();
        // 获取手机所有连接管理对象（包括对wi-fi,net等连接的管理）
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return false;
        } else {
            // 获取NetworkInfo对象
            NetworkInfo[] networkInfo = connectivityManager.getAllNetworkInfo();

            if (networkInfo != null && networkInfo.length > 0) {
                for (int i = 0; i < networkInfo.length; i++) {
                    // 判断当前网络状态是否为连接状态
                    if (networkInfo[i].getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static String getMD5(String val){
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        md5.update(val.getBytes());
        byte[] m = md5.digest();//加密
        return getString(m);
    }
    private static String getString(byte[] b){
        StringBuffer sb = new StringBuffer();
        for(int i = 0; i < b.length; i ++){
            sb.append(b[i]);
        }
        return sb.toString();
    }

    private static String getLastFile(){
        try {
            mManifestFile = new File(mRootPath+"manifest.txt");
            if(!mManifestFile.exists()) return null;
            FileReader reader = new FileReader(mManifestFile);
            BufferedReader br = new BufferedReader(reader);
            String string = null;
            String filePath= null;
            while((string = br.readLine())!=null){
                filePath = string;
            }
            if(filePath!=null&&filePath.split(",")[3].equals("true"))
                return filePath.split(",")[0];
            else
                return null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
