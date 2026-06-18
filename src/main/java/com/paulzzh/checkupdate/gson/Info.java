package com.paulzzh.checkupdate.gson;

import java.util.Map;

public class Info {
    public Map<String, HashSizeTime> info;
    public long time;
    public Map<String, Map<String, HashSizeTime>> versions;
}
