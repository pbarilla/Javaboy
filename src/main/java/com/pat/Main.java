package com.pat;

import java.io.IOException;

public class Main {
    private static final Gameboy gameboy;

    static {
        try {
            gameboy = new Gameboy();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        gameboy.startScreen();
//        gameboy.run();
    }
}