package com.pat;

// Okay this is just a duplicate of buxxi's flag implimentation
// https://github.com/buxxi/gameboy-emu

public interface Flags {
    boolean isSet(Flag flag);

    void set(Flag flag, boolean set);

    // not needed yet
//    void setInterruptsDisabled(boolean disabled);

//    boolean isInterruptsDisabled();

    /**
     * The allowed flags.
     * Each interrupt has a bit that can be masked against an int to see if that flag is enabled.
     */
    enum Flag {
        ZERO(       0b1000_0000), //Z
        SUBTRACT(   0b0100_0000), //N
        HALF_CARRY( 0b0010_0000), //H
        CARRY(      0b0001_0000); //C

        private final int mask;

        Flag(int mask) {
            this.mask = mask;
        }

        public int mask() {
            return mask;
        }
    }
}
