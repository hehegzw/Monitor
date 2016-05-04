package progressbar.cn.gzw.myapplication;

/**
 * Created by gzw on 16-5-2.
 */
public class StringUtil {
    public static boolean isEmpty(String ...strings){
            for(String s : strings){
                if(s == null ||s.equals("")){
                    return true;
                }
            }
            return false;
        }
}
