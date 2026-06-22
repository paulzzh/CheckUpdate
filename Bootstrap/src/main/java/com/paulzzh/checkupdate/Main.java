package com.paulzzh.checkupdate;

import static com.paulzzh.checkupdate.Utils.*;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println(HOME);
        ensureJar();
        launchGUI();
    }
}
