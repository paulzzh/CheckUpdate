package com.paulzzh.checkupdate.gson;

import java.util.Map;

public class Result {
    public final boolean major;
    public final boolean restart;
    public final String version;
    public final Map<String, HashSizeTime> filelist;

    public Result(boolean major, boolean restart, String version, Map<String, HashSizeTime> filelist) {
        this.major = major;
        this.restart = restart;
        this.version = version;
        this.filelist = filelist;
    }
}
