package com.pat;


public class Register {
    public static char lowNibble(short x) {
        return (char) (x & 0x0f);
    }
    public static char highNibble(short x) {
        return (char) ((char) (x >> 4) & 0x0f);
    }
    public short reg;

    public Register(short reg) {
        setReg(reg);
    }

    public char getLo() {
        return lowNibble(this.reg);
    }

    public void setLo(char lo) {
        setReg((short) (getHi() << 4 | lo << 0));
    }
    public char getHi() {
        return highNibble(this.reg);
    }
    public void setHi(char hi) {
        setReg((short) ((hi << 4) | getLo() << 0));
    }
    public short getReg() {
        return reg;
    }
    public void setReg(short reg) {
        this.reg = reg;
    }
}
