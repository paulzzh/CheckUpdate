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

    @SerializedName("retry")
    public int retry;

    @SerializedName("c_timeout")
    public int connectTimeout;

    @SerializedName("r_timeout")
    public int readTimeout;

    @SerializedName("public_key")
    public String publicKey;
}
