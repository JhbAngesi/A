package cn.student.musicplayer;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import cn.student.musicplayer.bean.Music;
import cn.student.musicplayer.adapter.MusicAdapter;
import cn.student.musicplayer.adapter.PlayingMusicAdapter;
import cn.student.musicplayer.db.LocalMusic;
import cn.student.musicplayer.gson.Result;
import cn.student.musicplayer.service.DownMusicService;
import cn.student.musicplayer.util.Utils;
import cn.student.musicplayer.service.MusicService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OnlineMusicActivity extends AppCompatActivity implements View.OnClickListener{

    private TextView musicCountView;
    private ListView musicListView;
    private TextView playingTitleView;
    private TextView playingArtistView;
    private ImageView playingImgView;
    private ImageView btnPlayOrPause;
    private  List<Result> onlinemusic;

    private List<Music> onlinemusic_list;
    private MusicService.MusicServiceBinder serviceBinder;
    private MusicAdapter adapter;

    private OkHttpClient client;
    private Handler mainHanlder;
    String url;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onlinemusic);
        //初始化
        initActivity();


        mainHanlder = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what){
                    case 60:
                        //更新一首歌曲
                        Music music = (Music) msg.obj;
                        if(music.getSongUrl() != null){
                            onlinemusic_list.add(music);
                        }

                        adapter.notifyDataSetChanged();
                        musicCountView.setText("播放全部(共" + onlinemusic_list.size() + "首)");
                        break;
                }
            }
        };

        // 列表项点击事件
        musicListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Music music = onlinemusic_list.get(position);
                serviceBinder.addPlayList(music);

            }
        });

        //列表项中更多按钮的点击事件
        adapter.setOnMoreButtonListener(new MusicAdapter.onMoreButtonListener() {
            @Override
            public void onClick(final int i) {
                final Music music = onlinemusic_list.get(i);
                final String[] items = new String[] {"收藏到我的音乐", "添加到播放列表","下载"};
                AlertDialog.Builder builder = new AlertDialog.Builder(OnlineMusicActivity.this);
                builder.setTitle(music.title+"-"+music.artist);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case 0:
                                MainActivity.addMymusic(music);
                                Toast.makeText(OnlineMusicActivity.this, "收藏成功！", Toast.LENGTH_SHORT).show();
                                break;
                            case 1:
                                serviceBinder.addPlayList(music);
                                Toast.makeText(OnlineMusicActivity.this, "添加成功！", Toast.LENGTH_SHORT).show();
                                break;
                            case 2:
                                //启动服务
                                Intent intent = new Intent(OnlineMusicActivity.this, DownMusicService.class);
                                intent.putExtra("songurl",music.songUrl);
                                intent.putExtra("actname",music.artist);
                                intent.putExtra("songname",music.title);
                                startService(intent);
                                //放入LocalMusic数据库
                                LocalMusic localMusic=new LocalMusic(music.songUrl,music.title,music.artist,music.imgUrl,false);
                                localMusic.save();
                                break;
                        }
                    }
                });
                builder.create().show();
            }
        });

    }

    // 监听组件
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.play_all:
                serviceBinder.addPlayList(onlinemusic_list);
                break;
            case R.id.player:
                Intent intent = new Intent(OnlineMusicActivity.this, PlayerActivity.class);
                startActivity(intent);
                //弹出动画
                overridePendingTransition(R.anim.bottom_in, R.anim.bottom_silent);
                break;
            case R.id.play_or_pause:
                serviceBinder.playOrPause();
                break;
            case R.id.playing_list:
                showPlayList();
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onlinemusic_list.clear();
        unbindService(mServiceConnection);
        client.dispatcher().cancelAll();
    }

    // 初始化活动
    private void initActivity(){
        //初始化控件
        ImageView btn_playAll = this.findViewById(R.id.play_all);
        musicCountView = this.findViewById(R.id.play_all_title);
        musicListView = this.findViewById(R.id.music_list);
        RelativeLayout playerToolView = this.findViewById(R.id.player);
        playingImgView = this.findViewById(R.id.playing_img);
        playingTitleView = this.findViewById(R.id.playing_title);
        playingArtistView = this.findViewById(R.id.playing_artist);
        btnPlayOrPause = this.findViewById(R.id.play_or_pause);
        ImageView btn_playingList = this.findViewById(R.id.playing_list);

        // 设置监听
        btn_playAll.setOnClickListener(this);
        playerToolView.setOnClickListener(this);
        btnPlayOrPause.setOnClickListener(this);
        btn_playingList.setOnClickListener(this);

        //绑定播放服务
        Intent i = new Intent(this, MusicService.class);
        bindService(i, mServiceConnection, BIND_AUTO_CREATE);

        // 使用ToolBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("网易云新歌榜");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setSupportActionBar(toolbar);

        //初始化OkHttp客户端
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)//设置连接超时时间
                .readTimeout(10, TimeUnit.SECONDS)//设置读取超时时间
                .build();

        // 获取在线音乐
        onlinemusic_list = new ArrayList<>();
        adapter = new MusicAdapter(this, R.layout.music_item, onlinemusic_list);
        musicListView.setAdapter(adapter);
        musicCountView.setText("播放全部(共"+onlinemusic_list.size()+"首)");
        getOlineMusic();
    }


    // 显示当前正在播放的音乐
    private void showPlayList(){
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        //设计对话框的显示标题
        builder.setTitle("播放列表");

        //获取播放列表
        final List<Music> playingList = serviceBinder.getPlayingList();

        if(playingList.size() > 0) {
            //播放列表有曲目，显示所有音乐
            final PlayingMusicAdapter playingAdapter = new PlayingMusicAdapter(this, R.layout.playinglist_item, playingList);
            builder.setAdapter(playingAdapter, new DialogInterface.OnClickListener() {
                //监听列表项点击事件
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    serviceBinder.addPlayList(playingList.get(which));
                }
            });

            //列表项中删除按钮的点击事件
            playingAdapter.setOnDeleteButtonListener(new PlayingMusicAdapter.onDeleteButtonListener() {
                @Override
                public void onClick(int i) {
                    serviceBinder.removeMusic(i);
                    playingAdapter.notifyDataSetChanged();
                }
            });
        }
        else {
            //播放列表没有曲目，显示没有音乐
            builder.setMessage("没有正在播放的音乐");
        }

        //设置该对话框是可以自动取消的，例如当用户在空白处随便点击一下，对话框就会关闭消失
        builder.setCancelable(true);

        //创建并显示对话框
        builder.create().show();
    }

    // 定义与服务的连接的匿名类
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        //绑定成功时调用
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            //绑定成功后，取得MusicSercice提供的接口
            serviceBinder = (MusicService.MusicServiceBinder) service;

            //注册监听器
            serviceBinder.registerOnStateChangeListener(listenr);

            Music item = serviceBinder.getCurrentMusic();

            if (serviceBinder.isPlaying()){
                //如果正在播放音乐, 更新控制栏信息
                btnPlayOrPause.setImageResource(R.drawable.zanting);
                playingTitleView.setText(item.title);
                playingArtistView.setText(item.artist);
                if (item.isOnlineMusic){
                    Glide.with(getApplicationContext())
                            .load(item.imgUrl)
                            .placeholder(R.drawable.defult_music_img)
                            .error(R.drawable.defult_music_img)
                            .into(playingImgView);
                }
                else {
                    ContentResolver resolver = getContentResolver();
                    Bitmap img = Utils.getLocalMusicBmp(resolver, item.imgUrl);
                    Glide.with(getApplicationContext())
                            .load(img)
                            .placeholder(R.drawable.defult_music_img)
                            .error(R.drawable.defult_music_img)
                            .into(playingImgView);
                }
            }
            else if (item != null){
                //当前有可播放音乐但没有播放
                btnPlayOrPause.setImageResource(R.drawable.bofang);
                playingTitleView.setText(item.title);
                playingArtistView.setText(item.artist);
                if (item.isOnlineMusic){
                    Glide.with(getApplicationContext())
                            .load(item.imgUrl)
                            .placeholder(R.drawable.defult_music_img)
                            .error(R.drawable.defult_music_img)
                            .into(playingImgView);
                }
                else {
                    ContentResolver resolver = getContentResolver();
                    Bitmap img = Utils.getLocalMusicBmp(resolver, item.imgUrl);
                    Glide.with(getApplicationContext())
                            .load(img)
                            .placeholder(R.drawable.defult_music_img)
                            .error(R.drawable.defult_music_img)
                            .into(playingImgView);
                }
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            //断开连接时注销监听器
            serviceBinder.unregisterOnStateChangeListener(listenr);
        }
    };

    // 实现监听器监听MusicService的变化，
    private MusicService.OnStateChangeListenr listenr = new MusicService.OnStateChangeListenr() {

        @Override
        public void onPlayProgressChange(long played, long duration) {}

        @Override
        public void onPlay(Music item) {
            //播放状态变为播放时
            btnPlayOrPause.setImageResource(R.drawable.zanting);
            playingTitleView.setText(item.title);
            playingArtistView.setText(item.artist);
            btnPlayOrPause.setEnabled(true);
            if (item.isOnlineMusic){
                Glide.with(getApplicationContext())
                        .load(item.imgUrl)
                        .placeholder(R.drawable.defult_music_img)
                        .error(R.drawable.defult_music_img)
                        .into(playingImgView);
            }
            else {
                ContentResolver resolver = getContentResolver();
                Bitmap img = Utils.getLocalMusicBmp(resolver, item.imgUrl);
                Glide.with(getApplicationContext())
                        .load(img)
                        .placeholder(R.drawable.defult_music_img)
                        .error(R.drawable.defult_music_img)
                        .into(playingImgView);
            }
        }

        @Override
        public void onPause() {
            //播放状态变为暂停时
            btnPlayOrPause.setImageResource(R.drawable.bofang);
            btnPlayOrPause.setEnabled(true);
        }
    };

    // 获取在线音乐
    private void getOlineMusic() {
        //构建Request对象，采用建造者模式，链式调用指明进行Get请求，传入Get的请求地址
        Request request = new Request.Builder().get()
                .url("http://api.we-chat.cn/personalized/newsong?limit=10")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //更新主线程UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //失败处理
                        Toast.makeText(OnlineMusicActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                //返回结果处理
                String result = response.body().string();
                try{
                    onlinemusic=Utils.handleResultResponse(result);
                    for(int i=0; i<onlinemusic.size(); i++){
                        url=getSongUrl(onlinemusic.get(i).dataId);
                        //实例化一首音乐并发送到主线程更新
                        System.out.println(url);
                        Music music = new Music(url, onlinemusic.get(i).dataName, onlinemusic.get(i).getArtists(), onlinemusic.get(i).datapicUrl, true);

                        Message message = mainHanlder.obtainMessage();
                        message.what = 60;
                        message.obj = music;
                        mainHanlder.sendMessage(message);
                        Thread.sleep(30);

                    }

                }catch (Exception e){}
            }
        });
    }
    //获取歌曲url
    private String getSongUrl(String id) throws IOException {
//
        //构建Request对象，采用建造者模式，链式调用指明进行Get请求，传入Get的请求地址
        Request request = new Request.Builder().get()
                .url("http://api.we-chat.cn/song/url?id=" + id)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(OnlineMusicActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String result = response.body().string();
                url =Utils.getSongUrl(result);
            }
            });
        return  url;
    }



    // 返回按钮
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
