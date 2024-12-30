package com.pat.instructions;

import com.pat.Memory;
import com.pat.Register;

public class LSM {
    // 8bit load/store/move
    // 16bit load/store/move

    public static int loadValueIntoLocation(int value, int location, Memory memory) {
        memory.writeByteToLocation(value, location);
        return 8;
    }

    public static int loadValueIntoRegister(int value, Register register, Register.RegByte regByte) {
        switch (regByte) {
            case HI -> {
                register.setHi(value);
                return 4;
            }
            case LO -> {
                register.setLo(value);
                return 4;
            }
            case WORD -> {
                register.setReg(value);
                return 8;
            }
            default -> {
                return 0;
            }
        }
    }

    public enum ImmediateLength {
        BYTE, WORD
    }

    public static int loadImmediateIntoRegister(int location, ImmediateLength length, Memory memory, Register register, Register.RegByte regByte) {

        int immediate = length == ImmediateLength.BYTE ? memory.readByteFromLocation(location) : memory.readWordFromLocation(location);

        switch (regByte) {
            case HI -> {
                register.setHi(immediate);
                return 8;
            }
            case LO -> {
                register.setLo(immediate);
                return 8;
            }
            case WORD -> {
                register.setReg(immediate);
                return 12;
            }
            default -> {
                return 0;
            }
        }

    }

}


