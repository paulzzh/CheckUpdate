package com.paulzzh.checkupdate.gson;

public class HashSizeTime {
    public String hash;
    public long size;
    public long time;

    public HashSizeTime(String hash, long size, long time) {
        this.hash = hash;
        this.size = size;
        this.time = time;
    }

    @Override
    public String toString() {
        return String.format("{hash=%s, size=%d, time=%d}", hash, size, time);
    }
}
