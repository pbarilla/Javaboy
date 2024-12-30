package com.pat;

public class Helpful {
    public static int leastSignificantBit(int x) {
        return x & 0xff;
    }

    public static int mostSignificantBit(int x) {
        return x >> 8;
    }

    public static int lowNibble(int x) {
        return x & 0x0f;
    }

    public static int highNibble(int x) {
        return (x >> 4) & 0x0f;
    }

    // These methods taken directly from coffeegb
    // https://github.com/trekawek/coffee-gb

    public static int toWord(int msb, int lsb) {
        return (msb << 8) | lsb;
    }

    public static boolean getBit(int byteValue, int position) {
        return (byteValue & (1 << position)) != 0;
    }

    public static int setBit(int byteValue, int position, boolean value) {
        return value ? setBit(byteValue, position) : clearBit(byteValue, position);
    }

    public static int setBit(int byteValue, int position) {
        return (byteValue | (1 << position)) & 0xff;
    }

    public static int clearBit(int byteValue, int position) {
        return ~(1 << position) & byteValue & 0xff;
    }

    public static int toSigned(int byteValue) {
        if ((byteValue & (1 << 7)) == 0) {
            return byteValue;
        } else {
            return byteValue - 0x100;
        }
    }


}
