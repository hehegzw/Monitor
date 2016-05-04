package progressbar.cn.gzw.myapplication.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import progressbar.cn.gzw.myapplication.MyApplication;

/**
 * Created by gzw on 2016/4/15.
 */
public class HttpUtil {
    private static ExecutorService executorService;
    private static HttpUtil httpUtil;
    private static boolean isRepeadUpload;

    private HttpUtil() {
    }

    public static HttpUtil getInstence() {
        isRepeadUpload = true;
        if (httpUtil == null) {
            return new HttpUtil();
        } else {
            return httpUtil;
        }
    }

    private static void post(String path, Map<String, String> params, Map<String, File> files, Success success, Failure failure) {
        DataOutputStream outStream = null;
        HttpURLConnection conn = null;
        //params.put("type","log");
        try {
            String BOUNDARY = "---------" + UUID.randomUUID().toString();//数据分隔线
            String PREFIX = "--";
            String LINEND = "\r\n";
            String MULTIPART_FORM_DATA = "multipart/from_data";
            String CHARSET = "utf-8";

            URL url = new URL(path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5 * 1000);
            conn.setReadTimeout(20 * 1000);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("connection", "keep-alive");
            conn.setRequestProperty("Charset", CHARSET);
            conn.setRequestProperty("Content-type", MULTIPART_FORM_DATA + ";boundary=" + BOUNDARY);
            outStream = new DataOutputStream(
                    conn.getOutputStream());
            StringBuilder sb = new StringBuilder();
            if(params!=null) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    sb.append(PREFIX);
                    sb.append(BOUNDARY);
                    sb.append(LINEND);
                    sb.append("Content-Disposition: form-data; name=\""
                            + entry.getKey() + "\"" + LINEND);
                    sb.append("Content-Type: text/plain; charset=" + CHARSET + LINEND);
                    sb.append("Content-Transfer-Encoding: 8bit" + LINEND);
                    sb.append(LINEND);
                    sb.append(entry.getValue());
                    sb.append(LINEND);
                }

                outStream.write(sb.toString().getBytes());
            }
            // 发送文件数据
            if (files != null)
                for (Map.Entry<String, File> file : files.entrySet()) {
                    StringBuilder sb1 = new StringBuilder();
                    sb1.append(PREFIX);
                    sb1.append(BOUNDARY);
                    sb1.append(LINEND);
                    sb1.append("Content-Disposition: form-data; name=\"file\"; filename=\""
                            + file.getKey() + "\"" + LINEND);
                    sb1.append("Content-Type: multipart/form-data; charset="
                            + CHARSET + LINEND);
                    sb1.append(LINEND);
                    outStream.write(sb1.toString().getBytes());
                    InputStream is = new FileInputStream(file.getValue());
                    byte[] buffer = new byte[1024];
                    int len = 0;
                    while ((len = is.read(buffer)) != -1) {
                        outStream.write(buffer, 0, len);
                    }
                    is.close();
                    outStream.write(LINEND.getBytes());
                }
            // 请求结束标志
            byte[] end_data = (PREFIX + BOUNDARY + PREFIX + LINEND).getBytes();
            outStream.write(end_data);
            outStream.flush();
            // 得到响应码
            int resCode = conn.getResponseCode();
            if (resCode != 200) {
                if(isRepeadUpload){
                    isRepeadUpload = false;
                    post(path, params, files, success, failure);
                }
                if (failure != null) {
                    failure.failure(resCode + "");
                }
            } else {
                isRepeadUpload = true;
                InputStream in = conn.getInputStream();
                InputStreamReader isReader = new InputStreamReader(in);
                BufferedReader bufReader = new BufferedReader(isReader);
                String line;
                String data = "getResult=";
                while ((line = bufReader.readLine()) != null)
                    data += line;
                if (success != null) {
                    success.success("success");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            if (failure != null) {
                failure.failure(e.toString());
            }
        } finally {
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public void uploadFile(String path, Map<String, String> params, Map<String, File> files, Success success, Failure failure) {
        if(!isNetAvailable()){
            if(failure!=null){
                failure.failure("network is not available");
                return;
            }
        }
        if (executorService == null) {
            executorService = Executors.newCachedThreadPool();
        }
        executorService.execute(new FileThread(path, params, files, success, failure));

    }
    public interface Success {
        void success(String response);
    }

    public interface Failure {
        void failure(String error);
    }

    private class FileThread implements Runnable {
        private String path;
        private Map<String, String> params;
        private Map<String, File> files;
        private Success success;
        private Failure failure;

        public FileThread(String path, Map<String, String> params, Map<String, File> files, Success success, Failure failure) {
            this.path = path;
            this.params = params;
            this.files = files;
            this.success = success;
            this.failure = failure;
        }

        @Override
        public void run() {
            post(path, params, files, success, failure);
        }
    }
    private static boolean isNetAvailable(){
        ConnectivityManager conn = (ConnectivityManager) MyApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if(conn == null){
            return false;
        }
        NetworkInfo[] networkInfos = conn.getAllNetworkInfo();
        if(networkInfos!=null && networkInfos.length>0){
            for(int i=0;i<networkInfos.length;i++){
                if(networkInfos[i].getState() == NetworkInfo.State.CONNECTED){
                    return true;
                }
            }
            return false;
        }else{
            return false;
        }
    }
}