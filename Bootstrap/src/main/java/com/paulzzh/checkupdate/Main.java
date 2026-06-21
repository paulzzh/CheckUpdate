package com.paulzzh.checkupdate;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import static com.paulzzh.checkupdate.Utils.*;

public class Main {
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        System.out.println(HOME);
        ensureJar();
        launchGUI();
    }
}
