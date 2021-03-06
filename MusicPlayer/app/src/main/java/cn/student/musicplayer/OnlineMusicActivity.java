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
        //?????????
        initActivity();


        mainHanlder = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what){
                    case 60:
                        //??????????????????
                        Music music = (Music) msg.obj;
                        if(music.getSongUrl() != null){
                            onlinemusic_list.add(music);
                        }

                        adapter.notifyDataSetChanged();
                        musicCountView.setText("????????????(???" + onlinemusic_list.size() + "???)");
                        break;
                }
            }
        };

        // ?????????????????????
        musicListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Music music = onlinemusic_list.get(position);
                serviceBinder.addPlayList(music);

            }
        });

        //???????????????????????????????????????
        adapter.setOnMoreButtonListener(new MusicAdapter.onMoreButtonListener() {
            @Override
            public void onClick(final int i) {
                final Music music = onlinemusic_list.get(i);
                final String[] items = new String[] {"?????????????????????", "?????????????????????","??????"};
                AlertDialog.Builder builder = new AlertDialog.Builder(OnlineMusicActivity.this);
                builder.setTitle(music.title+"-"+music.artist);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case 0:
                                MainActivity.addMymusic(music);
                                Toast.makeText(OnlineMusicActivity.this, "???????????????", Toast.LENGTH_SHORT).show();
                                break;
                            case 1:
                                serviceBinder.addPlayList(music);
                                Toast.makeText(OnlineMusicActivity.this, "???????????????", Toast.LENGTH_SHORT).show();
                                break;
                            case 2:
                                //????????????
                                Intent intent = new Intent(OnlineMusicActivity.this, DownMusicService.class);
                                intent.putExtra("songurl",music.songUrl);
                                intent.putExtra("actname",music.artist);
                                intent.putExtra("songname",music.title);
                                startService(intent);
                                //??????LocalMusic?????????
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

    // ????????????
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.play_all:
                serviceBinder.addPlayList(onlinemusic_list);
                break;
            case R.id.player:
                Intent intent = new Intent(OnlineMusicActivity.this, PlayerActivity.class);
                startActivity(intent);
                //????????????
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

    // ???????????????
    private void initActivity(){
        //???????????????
        ImageView btn_playAll = this.findViewById(R.id.play_all);
        musicCountView = this.findViewById(R.id.play_all_title);
        musicListView = this.findViewById(R.id.music_list);
        RelativeLayout playerToolView = this.findViewById(R.id.player);
        playingImgView = this.findViewById(R.id.playing_img);
        playingTitleView = this.findViewById(R.id.playing_title);
        playingArtistView = this.findViewById(R.id.playing_artist);
        btnPlayOrPause = this.findViewById(R.id.play_or_pause);
        ImageView btn_playingList = this.findViewById(R.id.playing_list);

        // ????????????
        btn_playAll.setOnClickListener(this);
        playerToolView.setOnClickListener(this);
        btnPlayOrPause.setOnClickListener(this);
        btn_playingList.setOnClickListener(this);

        //??????????????????
        Intent i = new Intent(this, MusicService.class);
        bindService(i, mServiceConnection, BIND_AUTO_CREATE);

        // ??????ToolBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("??????????????????");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setSupportActionBar(toolbar);

        //?????????OkHttp?????????
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)//????????????????????????
                .readTimeout(10, TimeUnit.SECONDS)//????????????????????????
                .build();

        // ??????????????????
        onlinemusic_list = new ArrayList<>();
        adapter = new MusicAdapter(this, R.layout.music_item, onlinemusic_list);
        musicListView.setAdapter(adapter);
        musicCountView.setText("????????????(???"+onlinemusic_list.size()+"???)");
        getOlineMusic();
    }


    // ?????????????????????????????????
    private void showPlayList(){
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        //??????????????????????????????
        builder.setTitle("????????????");

        //??????????????????
        final List<Music> playingList = serviceBinder.getPlayingList();

        if(playingList.size() > 0) {
            //??????????????????????????????????????????
            final PlayingMusicAdapter playingAdapter = new PlayingMusicAdapter(this, R.layout.playinglist_item, playingList);
            builder.setAdapter(playingAdapter, new DialogInterface.OnClickListener() {
                //???????????????????????????
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    serviceBinder.addPlayList(playingList.get(which));
                }
            });

            //???????????????????????????????????????
            playingAdapter.setOnDeleteButtonListener(new PlayingMusicAdapter.onDeleteButtonListener() {
                @Override
                public void onClick(int i) {
                    serviceBinder.removeMusic(i);
                    playingAdapter.notifyDataSetChanged();
                }
            });
        }
        else {
            //?????????????????????????????????????????????
            builder.setMessage("???????????????????????????");
        }

        //????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        builder.setCancelable(true);

        //????????????????????????
        builder.create().show();
    }

    // ????????????????????????????????????
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        //?????????????????????
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            //????????????????????????MusicSercice???????????????
            serviceBinder = (MusicService.MusicServiceBinder) service;

            //???????????????
            serviceBinder.registerOnStateChangeListener(listenr);

            Music item = serviceBinder.getCurrentMusic();

            if (serviceBinder.isPlaying()){
                //????????????????????????, ?????????????????????
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
                //???????????????????????????????????????
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
            //??????????????????????????????
            serviceBinder.unregisterOnStateChangeListener(listenr);
        }
    };

    // ?????????????????????MusicService????????????
    private MusicService.OnStateChangeListenr listenr = new MusicService.OnStateChangeListenr() {

        @Override
        public void onPlayProgressChange(long played, long duration) {}

        @Override
        public void onPlay(Music item) {
            //???????????????????????????
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
            //???????????????????????????
            btnPlayOrPause.setImageResource(R.drawable.bofang);
            btnPlayOrPause.setEnabled(true);
        }
    };

    // ??????????????????
    private void getOlineMusic() {
        //??????Request?????????????????????????????????????????????????????????Get???????????????Get???????????????
        Request request = new Request.Builder().get()
                .url("http://api.we-chat.cn/personalized/newsong?limit=10")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //???????????????UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //????????????
                        Toast.makeText(OnlineMusicActivity.this, "????????????", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                //??????????????????
                String result = response.body().string();
                try{
                    onlinemusic=Utils.handleResultResponse(result);
                    for(int i=0; i<onlinemusic.size(); i++){
                        url=getSongUrl(onlinemusic.get(i).dataId);
                        //????????????????????????????????????????????????
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
    //????????????url
    private String getSongUrl(String id) throws IOException {
//
        //??????Request?????????????????????????????????????????????????????????Get???????????????Get???????????????
        Request request = new Request.Builder().get()
                .url("http://api.we-chat.cn/song/url?id=" + id)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(OnlineMusicActivity.this, "????????????", Toast.LENGTH_SHORT).show();
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



    // ????????????
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
