package cn.student.musicplayer.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Message;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cn.student.musicplayer.LocalMusicActivity;
import cn.student.musicplayer.bean.Music;
import cn.student.musicplayer.gson.Result;

public class Utils {

    //累计听歌数量
    public static int count;

    //播放模式
    public static final int TYPE_ORDER = 4212;  //顺序播放
    public static final int TYPE_SINGLE = 4313; //单曲循环
    public static final int TYPE_RANDOM = 4414; //随机播放

    // 获取本地音乐封面图片
    public static Bitmap getLocalMusicBmp(ContentResolver res, String musicPic) {
        InputStream in;
        Bitmap bmp = null;
        try {
            Uri uri = Uri.parse(musicPic);
            in = res.openInputStream(uri);
            BitmapFactory.Options sBitmapOptions = new BitmapFactory.Options();
            bmp = BitmapFactory.decodeStream(in, null, sBitmapOptions);
            in.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return bmp;
    }

    //格式化歌曲时间
    public static String formatTime(long time) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("mm:ss");
        Date data = new Date(time);
        return dateFormat.format(data);
    }
    //将返回的JSON数据解析成Result实体类
    public static List<Result> handleResultResponse(String response){
        try{
            List<Result> onlinemusic_list = new ArrayList<>();
            JSONObject jsonObject = new JSONObject(response);
            JSONArray jsonArray = jsonObject.getJSONArray("result");
            for(int i=0; i<jsonArray.length(); i++){
                JSONObject x = jsonArray.getJSONObject(i);
                String resultContent = x.toString();
                Result result=new Gson().fromJson(resultContent, Result.class);
                JSONObject song=x.getJSONObject("song");
                JSONArray artists = song.getJSONArray("artists");
                JSONObject singer = artists.getJSONObject(0);
                String singerName = singer.getString("name");
                result.setArtists(singerName);
                //将JSON数据转换成result对象
                onlinemusic_list.add(result);
            }
            return onlinemusic_list;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
    public static String getSongUrl(String response){
        String url=null;
        try {
            JSONObject obj = new JSONObject(response);
            JSONArray songs = new JSONArray(obj.getString("data"));
            JSONObject song=songs.getJSONObject(0);
            url=song.getString("url");

        }catch (Exception e){
            e.printStackTrace();
        }
        return url;
    }
}