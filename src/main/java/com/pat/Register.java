package com.pat;


public class Register {
    public static int lowNibble(int x) {
        return (int) (x & 0x00ff);
    }
    public static int highNibble(int x) {
        return (int) ((int) (x >> 8) & 0x00ff);
    }
    public int reg;

    public Register(short reg) {
        setReg(reg);
    }

    public int getLo() {
        return lowNibble(this.reg);
    }

    public void setLo(int lo) {
        setReg((getHi() << 8 | lo << 0));
    }
    public int getHi() {
        return highNibble(this.reg);
    }
    public void setHi(int hi) {
        setReg(((hi << 8) | getLo() << 0));
    }
    public int getReg() {
        return reg;
    }
    public void setReg(int reg) {
        this.reg = reg;
    }

    public enum RegByte {
        HI, LO, WORD;
    }

    public static class RegisterHash {
        public Register AF;
        public Register BC;
        public Register DE;
        public Register HL;

        public RegisterHash(Register AF, Register BC, Register DE, Register HL) {
            this.AF = AF;
            this.BC = BC;
            this.DE = DE;
            this.HL = HL;
        }
    }
}
