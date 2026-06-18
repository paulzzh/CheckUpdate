package com.paulzzh.checkupdate.gson;

import java.util.Map;

public class Result {
    public boolean major;
    public boolean restart;
    public String version;
    public Map<String, HashSizeTime> filelist;

    public Result(boolean major, boolean restart, String version, Map<String, HashSizeTime> filelist) {
        this.major = major;
        this.restart = restart;
        this.version = version;
        this.filelist = filelist;
    }
}
