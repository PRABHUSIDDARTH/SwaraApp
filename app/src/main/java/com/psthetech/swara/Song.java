package com.psthetech.swara;

public class Song {
    private long id;
    String title;
    String artist;
    String album;
    long duration;
    String path;

    public Song(long id, String title, String artist, String album, long duration, String path) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.path = path;
    }
    public String getPath(){
        return path;
    }
    public String getTitle(){
        return title;
    }
    public long getDuration() {
        return duration;
    }
    public String getAlbum() {
        return album;
    }

    public String getArtist() {
        return artist;
    }
}