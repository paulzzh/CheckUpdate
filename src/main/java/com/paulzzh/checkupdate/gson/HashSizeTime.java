package com.paulzzh.checkupdate.gson;

public class HashSizeTime {
    public final String hash;
    public final long size;
    public final long time;

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
