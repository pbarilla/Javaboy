package com.pat;

public class Video {

    private static double VERT_SYNC = 59.73;

    private int[][] screenBuffer = new int[256][256];
    public int[][] screenData = new int [160][144];
    private int scrollX, scrollY;
    private int wndPosX, wndPosY;

}
