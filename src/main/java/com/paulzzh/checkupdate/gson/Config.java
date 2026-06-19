package com.paulzzh.checkupdate.gson;

import com.google.gson.annotations.SerializedName;

public class Config {
    @SerializedName("pack_name")
    public String name;

    @SerializedName("version_name")
    public String version;

    @SerializedName("host")
    public String host;

    @SerializedName("thread")
    public int thread;
}
