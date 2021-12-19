package cn.student.musicplayer.gson;
import com.google.gson.annotations.SerializedName;

public class Result {
    @SerializedName("name")
    public String dataName;//歌曲名
    @SerializedName("id")
    public String dataId;//歌曲ID
    @SerializedName("picUrl")
    public String datapicUrl;//歌曲ID

    public String artists;//歌手

    public String getArtists() {
        return artists;
    }

    public void setArtists(String artists) {
        this.artists = artists;
    }
}
