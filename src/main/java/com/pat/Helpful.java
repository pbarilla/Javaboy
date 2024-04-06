package com.pat;

public class Helpful {
    public static char leastSignificantBit(byte x) {
        return (char) (x & 1);
    }

    public static char mostSignificantBit(byte x) {
        return (char) ((x & 0xFF) >> 7);
    }
}
