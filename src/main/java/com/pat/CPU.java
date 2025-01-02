package com.pat;

import com.pat.instructions.LSM;

public class CPU {
    public static final int CLOCK_SPEED = 4194303;

    private final Memory memory; // memory includes cart and onboard memory

    private static Register registerAF = new Register((short) 0x0000);
    private static Register registerBC = new Register((short) 0x0000);
    private static Register registerDE = new Register((short) 0x0000);
    private static Register registerHL = new Register((short) 0x0000);
    private final Flags flags = new FlagsImpl();

    private static boolean interruptsEnabled = false;


    private int programCounter = 0x100; // 0x100 is the default starting position of the PC
    private int stackPointer = 0xEEEE;

    public CPU(Memory memory) {
        this.memory = memory;
    }

    /**
     * Test helper stuff
     */

    public void forceProgramCounterToPosition(int position, boolean executeNextOpCode) {
        programCounter = position;
        if (executeNextOpCode) {
            fetchDecodeExecute();
        }
    }


    public Register.RegisterHash sampleRegisters() {
        return new Register.RegisterHash(registerAF, registerBC, registerDE, registerHL);
    }

    /**
     * Rotates and Shifts
     */

    enum RotateDirection {
        RIGHT, LEFT
    }

    // 8bit rotate
    private int rotate(RotateDirection direction, Register register, Register.RegByte regByte, boolean includeCarry) {

        if (regByte == Register.RegByte.WORD) {
            System.out.println("Operation not supported rotateRight WORD");
        }

        int reg = regByte == Register.RegByte.HI ? register.getHi() : register.getLo();
        boolean oldBit = Helpful.getBit(reg, direction == RotateDirection.RIGHT ? 0 : 7);

        int result = direction == RotateDirection.RIGHT ? reg >> 1 : reg << 1;

        if (includeCarry) {
            boolean carry = flags.isSet(Flags.Flag.CARRY);
            result = Helpful.setBit(result, direction == RotateDirection.RIGHT ? 7 : 0, carry);
        }

        flags.set(Flags.Flag.CARRY, oldBit);
        flags.set(Flags.Flag.ZERO, result == 0);
        flags.set(Flags.Flag.HALF_CARRY, false);
        flags.set(Flags.Flag.SUBTRACT, false);

        if (regByte == Register.RegByte.HI) {
            register.setHi(result);
        } else {
            register.setLo(result);
        }

        // CB07 is also 0x07. 0x07 is 4 cycles, but CB07 is 8 for some reason.
        // So in the switch CB07 add 4 cycles just to fix the timing.
        if (register == registerAF && regByte == Register.RegByte.HI) {
            return 4;
        }

        return 8;
    }

    private int shift(RotateDirection direction, Register register, Register.RegByte regByte, boolean overrideLSBorMSB) {
        if (regByte == Register.RegByte.WORD) {
            System.out.println("Operation not supported rotateRight WORD");
            return 16;
        }

        int reg = regByte == Register.RegByte.HI ? register.getHi() : register.getLo();
        boolean oldBit = Helpful.getBit(reg, direction == RotateDirection.RIGHT ? 0 : 7);

        int result = direction == RotateDirection.RIGHT ? reg >> 1 : reg << 1;

        if (direction == RotateDirection.RIGHT && overrideLSBorMSB) {
            result = Helpful.setBit(result, 0, false);
        }

        flags.set(Flags.Flag.CARRY, oldBit);
        flags.set(Flags.Flag.ZERO, result == 0);
        flags.set(Flags.Flag.HALF_CARRY, false);
        flags.set(Flags.Flag.SUBTRACT, false);

        if (regByte == Register.RegByte.HI) {
            register.setHi(result);
        } else {
            register.setLo(result);
        }

        return 8;

    }

    /**
     * Bit Opcodes
     */

    public int testRegisterBit(Register register, Register.RegByte regByte, int bitPosition) {
        if (regByte == Register.RegByte.WORD) {
            System.out.println("Operation not supported rotateRight WORD");
            return 16;
        }

        boolean bitFlipped = Helpful.getBit(regByte == Register.RegByte.HI ? register.getHi() : register.getLo(), bitPosition);
        flags.set(Flags.Flag.ZERO, bitFlipped);
        flags.set(Flags.Flag.SUBTRACT, false);
        flags.set(Flags.Flag.HALF_CARRY, true);
        return 8;
    }

    public int setBit(Register register, Register.RegByte regByte, int bitPosition) {
        if (regByte == Register.RegByte.WORD) {
            System.out.println("Operation not supported rotateRight WORD");
            return 16;
        }

        int value = regByte == Register.RegByte.HI ? register.getHi() : register.getLo();
        int result = Helpful.setBit(value, bitPosition, true);
        if (regByte == Register.RegByte.HI) {
            register.setHi(result);
        } else if (regByte == Register.RegByte.LO) {
            register.setLo(result);
        }

        return 8;
    }

    // basically a duplicate of setBit with false instead of true. should probably refactor this eventually...
    public int resetBit(Register register, Register.RegByte regByte, int bitPosition) {
        if (regByte == Register.RegByte.WORD) {
            System.out.println("Operation not supported rotateRight WORD");
            return 16;
        }

        int value = regByte == Register.RegByte.HI ? register.getHi() : register.getLo();
        int result = Helpful.setBit(value, bitPosition, false);
        if (regByte == Register.RegByte.HI) {
            register.setHi(result);
        } else if (regByte == Register.RegByte.LO) {
            register.setLo(result);
        }

        return 8;
    }


    /**
     * Instructions that should be refactored to another class
     */

    public void addIntoA(int value) {
        int result = registerAF.getHi() + value;

        flags.set(Flags.Flag.CARRY, result > 0xFF);
        // make sure the value fits in a byte with no overflow
        result = result & 0xFF;

        boolean halfCarry = ((result ^ registerAF.getHi() ^ value) & 0x10) != 0;

        flags.set(Flags.Flag.ZERO, result == 0);
        flags.set(Flags.Flag.ZERO, false);
        flags.set(Flags.Flag.HALF_CARRY, halfCarry);

        registerAF.setHi(result);
    }

    // 16 bit
    public void addIntoHL(int value) {
        int result = registerHL.getReg() + value;

        boolean carry = result > 0xFFFF;
        result = result & 0xFFFF;
        boolean halfCarry = ((result ^ registerHL.getReg() ^ value) & 0x1000) != 0;

        registerHL.setReg(result);

        flags.set(Flags.Flag.SUBTRACT, false);
        flags.set(Flags.Flag.HALF_CARRY, halfCarry);
        flags.set(Flags.Flag.CARRY, carry);
    }

    public void addWithCarryIntoA(int value) {
        addIntoA(value + (flags.isSet(Flags.Flag.CARRY) ? 1 : 0));
    }

    public void subtractFromA(int value) {
        int result = (registerAF.getHi() - value) & 0xFF;
        boolean carry = value > registerAF.getHi();
        boolean halfCarry = (value & 0x0F) > (registerAF.getHi() & 0x0F);

        registerAF.setHi(result);

        flags.set(Flags.Flag.ZERO, result == 0);
        flags.set(Flags.Flag.SUBTRACT, true);
        flags.set(Flags.Flag.HALF_CARRY, halfCarry);
        flags.set(Flags.Flag.CARRY, carry);
    }

    public void subtractWithCarryFromA(int value) {
        subtractFromA(value + (flags.isSet(Flags.Flag.CARRY) ? 1 : 0));
    }

    public void andIntoA(int value) {
        int result = registerAF.getHi() & value;

        flags.set(Flags.Flag.ZERO, result == 0);
        flags.set(Flags.Flag.SUBTRACT, false);
        flags.set(Flags.Flag.HALF_CARRY, true);
        flags.set(Flags.Flag.CARRY, false);

        registerAF.setHi(result);
    }

    public void orIntoA(int value) {
        int result = registerAF.getHi() | value;

        flags.set(Flags.Flag.ZERO, result == 0);
        flags.set(Flags.Flag.SUBTRACT, false);
        flags.set(Flags.Flag.HALF_CARRY, false);
        flags.set(Flags.Flag.CARRY, false);

        registerAF.setHi(result);
    }

    public void xorIntoA(int value) {
        int result = (registerAF.getHi() ^ value) & 0xFF;

        flags.set(Flags.Flag.ZERO, result == 0);
        flags.set(Flags.Flag.SUBTRACT, false);
        flags.set(Flags.Flag.HALF_CARRY, false);
        flags.set(Flags.Flag.CARRY, false);

        registerAF.setHi(result);
    }

    public void cpToA(int value) {
        flags.set(Flags.Flag.ZERO, value == registerAF.getHi());
        flags.set(Flags.Flag.SUBTRACT, true);
        flags.set(Flags.Flag.HALF_CARRY, (value & 0x0F) > (registerAF.getHi() & 0x0F));
        flags.set(Flags.Flag.CARRY, registerAF.getHi() < value);
    }

    public void incRegister(Register register, Register.RegByte regByte) {
        int value;
        if (regByte == Register.RegByte.HI) {
            value = register.getHi();
            value++;
            register.setHi(value);
        } else if (regByte == Register.RegByte.LO) {
            value = register.getLo();
            value++;
            register.setLo(value);
        } else {
            value = register.getReg();
            value++;
            register.setReg(value);
        }

        flags.set(Flags.Flag.ZERO, value == 0);
        flags.set(Flags.Flag.SUBTRACT, false);
        flags.set(Flags.Flag.HALF_CARRY, value > 0x10);
    }

    public void decRegister(Register register, Register.RegByte regByte) {
        int value;
        if (regByte == Register.RegByte.HI) {
            value = register.getHi();
            value--;
            register.setHi(value);
        } else if (regByte == Register.RegByte.LO) {
            value = register.getLo();
            value--;
            register.setLo(value);
        } else {
            value = register.getReg();
            value--;
            register.setReg(value);
        }

        flags.set(Flags.Flag.ZERO, value == 0);
        flags.set(Flags.Flag.SUBTRACT, true);
        flags.set(Flags.Flag.HALF_CARRY, value < 0x10);
    }

    public void swapRegister(Register register, Register.RegByte regByte) {
        int value = 0;
        if (regByte == Register.RegByte.HI) {
            value = register.getHi();
            int highNibble = Helpful.highNibble(value);
            int lowNibble = Helpful.lowNibble(value);
            value = highNibble & 0xF | lowNibble >> 4;
            register.setHi(value);
        } else if (regByte == Register.RegByte.LO) {
            value = register.getLo();
            int highNibble = Helpful.highNibble(value);
            int lowNibble = Helpful.lowNibble(value);
            value = highNibble & 0xF | lowNibble >> 4;
            register.setLo(value);
        } else if (regByte == Register.RegByte.WORD) {
            int hi = register.getHi();
            int lo = register.getLo();
            register.setHi(lo);
            register.setLo(hi);
            value = register.getReg();
        }

        flags.set(Flags.Flag.ZERO, value == 0);
        flags.set(Flags.Flag.SUBTRACT, false);
        flags.set(Flags.Flag.HALF_CARRY, false);
        flags.set(Flags.Flag.CARRY, false);
    }

    public int fetchDecodeExecute() {
        int opcode = this.memory.generalMemory[programCounter];

        int cycles = 0;

        switch (opcode) {

            // 8 bit loads
            // LD nn,n (page 65)
            case 0x06:
                cycles = LSM.loadValueIntoLocation(registerBC.getHi(), memory.generalMemory[programCounter + 1], memory);
                break;
            case 0x0E:
                cycles = LSM.loadValueIntoLocation(registerBC.getLo(), memory.generalMemory[programCounter + 1], memory);
                break;
            case 0x16:
                cycles = LSM.loadValueIntoLocation(registerDE.getHi(), memory.generalMemory[programCounter + 1], memory);
                break;
            case 0x1E:
                cycles = LSM.loadValueIntoLocation(registerDE.getLo(), memory.generalMemory[programCounter + 1], memory);
                break;
            case 0x26:
                cycles = LSM.loadValueIntoLocation(registerHL.getHi(), memory.generalMemory[programCounter + 1], memory);
                break;
            case 0x2E:
                cycles = LSM.loadValueIntoLocation(registerHL.getLo(), memory.generalMemory[programCounter + 1], memory);
                break;

            // LD r1, r2 (page 66)
            case 0x7F:
                cycles = LSM.loadValueIntoRegister(registerAF.getHi(), registerAF, Register.RegByte.HI);
                break;
            case 0x78, 0x47:
                cycles = LSM.loadValueIntoRegister(registerBC.getHi(), registerAF, Register.RegByte.HI);
                break;
            case 0x79, 0x4F:
                cycles = LSM.loadValueIntoRegister(registerBC.getLo(), registerAF, Register.RegByte.HI);
                break;
            case 0x7A:
                cycles = LSM.loadValueIntoRegister(registerDE.getHi(), registerAF, Register.RegByte.HI);
                break;
            case 0x7B:
                cycles = LSM.loadValueIntoRegister(registerDE.getLo(), registerAF, Register.RegByte.HI);
                break;
            case 0x7C:
                cycles = LSM.loadValueIntoRegister(registerHL.getHi(), registerAF, Register.RegByte.HI);
                break;
            case 0x7D:
                cycles = LSM.loadValueIntoRegister(registerHL.getLo(), registerAF, Register.RegByte.HI);
                break;
            case 0x7E:
                cycles = LSM.loadValueIntoRegister(registerHL.getReg(), registerAF, Register.RegByte.HI);
                break;
            case 0x40:
                cycles = LSM.loadValueIntoRegister(registerBC.getHi(), registerBC, Register.RegByte.HI);
                break;
            case 0x41:
                cycles = LSM.loadValueIntoRegister(registerBC.getLo(), registerBC, Register.RegByte.HI);
                break;
            case 0x42:
                cycles = LSM.loadValueIntoRegister(registerDE.getHi(), registerBC, Register.RegByte.HI);
                break;
            case 0x43:
                cycles = LSM.loadValueIntoRegister(registerDE.getLo(), registerBC, Register.RegByte.HI);
                break;
            case 0x44:
                cycles = LSM.loadValueIntoRegister(registerHL.getHi(), registerBC, Register.RegByte.HI);
                break;
            case 0x45:
                cycles = LSM.loadValueIntoRegister(registerHL.getLo(), registerBC, Register.RegByte.HI);
                break;
            case 0x46:
                cycles = LSM.loadValueIntoRegister(registerHL.getReg(), registerBC, Register.RegByte.HI);
                break;
            case 0x48:
                cycles = LSM.loadValueIntoRegister(registerBC.getHi(), registerBC, Register.RegByte.LO);
                break;
            case 0x49:
                cycles = LSM.loadValueIntoRegister(registerBC.getLo(), registerBC, Register.RegByte.LO);
                break;
            case 0x4A:
                cycles = LSM.loadValueIntoRegister(registerDE.getHi(), registerBC, Register.RegByte.LO);
                break;
            case 0x4B:
                cycles = LSM.loadValueIntoRegister(registerDE.getLo(), registerBC, Register.RegByte.LO);
                break;
            case 0x4C:
                cycles = LSM.loadValueIntoRegister(registerHL.getHi(), registerBC, Register.RegByte.LO);
                break;
            case 0x4D:
                cycles = LSM.loadValueIntoRegister(registerHL.getHi(), registerBC, Register.RegByte.LO);
                break;
            case 0x4E:
                cycles = LSM.loadValueIntoRegister(registerHL.getReg(), registerBC, Register.RegByte.LO);
                break;
            case 0x50:
                cycles = LSM.loadValueIntoRegister(registerBC.getHi(), registerDE, Register.RegByte.HI);
                break;
            case 0x51:
                cycles = LSM.loadValueIntoRegister(registerBC.getLo(), registerDE, Register.RegByte.HI);
                break;
            case 0x52:
                cycles = LSM.loadValueIntoRegister(registerDE.getHi(), registerDE, Register.RegByte.HI);
                break;
            case 0x53:
                cycles = LSM.loadValueIntoRegister(registerDE.getLo(), registerDE, Register.RegByte.HI);
                break;
            case 0x54:
                cycles = LSM.loadValueIntoRegister(registerHL.getHi(), registerDE, Register.RegByte.HI);
                break;
            case 0x55:
                cycles = LSM.loadValueIntoRegister(registerHL.getLo(), registerDE, Register.RegByte.HI);
                break;
            case 0x56:
                cycles = LSM.loadValueIntoRegister(registerHL.getReg(), registerDE, Register.RegByte.HI);
                break;
            case 0x58:
                cycles = LSM.loadValueIntoRegister(registerBC.getHi(), registerDE, Register.RegByte.LO);
                break;
            case 0x59:
                cycles = LSM.loadValueIntoRegister(registerBC.getLo(), registerDE, Register.RegByte.LO);
                break;
            case 0x5A:
                cycles = LSM.loadValueIntoRegister(registerDE.getHi(), registerDE, Register.RegByte.LO);
                break;
            case 0x5B:
                cycles = LSM.loadValueIntoRegister(registerDE.getLo(), registerDE, Register.RegByte.LO);
                break;
            case 0x5C:
                cycles = LSM.loadValueIntoRegister(registerHL.getHi(), registerDE, Register.RegByte.LO);
                break;
            case 0x5D:
                cycles = LSM.loadValueIntoRegister(registerHL.getLo(), registerDE, Register.RegByte.LO);
                break;
            case 0x5E:
                cycles = LSM.loadValueIntoRegister(registerHL.getReg(), registerDE, Register.RegByte.LO);
                break;
            case 0x60:
                cycles = LSM.loadValueIntoRegister(registerBC.getHi(), registerHL, Register.RegByte.HI);
                break;
            case 0x61:
                cycles = LSM.loadValueIntoRegister(registerBC.getLo(), registerHL, Register.RegByte.HI);
                break;
            case 0x62:
                cycles = LSM.loadValueIntoRegister(registerDE.getHi(), registerHL, Register.RegByte.HI);
                break;
            case 0x63:
                cycles = LSM.loadValueIntoRegister(registerDE.getLo(), registerHL, Register.RegByte.HI);
                break;
            case 0x64:
                cycles = LSM.loadValueIntoRegister(registerHL.getHi(), registerHL, Register.RegByte.HI);
                break;
            case 0x65:
                cycles = LSM.loadValueIntoRegister(registerHL.getLo(), registerHL, Register.RegByte.HI);
                break;
            case 0x66:
                cycles = LSM.loadValueIntoRegister(registerHL.getReg(), registerHL, Register.RegByte.HI);
                break;
            case 0x68:
                cycles = LSM.loadValueIntoRegister(registerBC.getHi(), registerHL, Register.RegByte.LO);
                break;
            case 0x69:
                cycles = LSM.loadValueIntoRegister(registerBC.getLo(), registerHL, Register.RegByte.LO);
                break;
            case 0x6A:
                cycles = LSM.loadValueIntoRegister(registerDE.getHi(), registerHL, Register.RegByte.LO);
                break;
            case 0x6B:
                cycles = LSM.loadValueIntoRegister(registerDE.getLo(), registerHL, Register.RegByte.LO);
                break;
            case 0x6C:
                cycles = LSM.loadValueIntoRegister(registerHL.getHi(), registerHL, Register.RegByte.LO);
                break;
            case 0x6D:
                cycles = LSM.loadValueIntoRegister(registerHL.getLo(), registerHL, Register.RegByte.LO);
                break;
            case 0x6E:
                cycles = LSM.loadValueIntoRegister(registerHL.getReg(), registerHL, Register.RegByte.LO);
                break;
            case 0x70:
                cycles = LSM.loadValueIntoRegister(registerBC.getHi(), registerHL, Register.RegByte.WORD);
                break;
            case 0x71:
                cycles = LSM.loadValueIntoRegister(registerBC.getLo(), registerHL, Register.RegByte.WORD);
                break;
            case 0x72:
                cycles = LSM.loadValueIntoRegister(registerDE.getHi(), registerHL, Register.RegByte.WORD);
                break;
            case 0x73:
                cycles = LSM.loadValueIntoRegister(registerDE.getLo(), registerHL, Register.RegByte.WORD);
                break;
            case 0x74:
                cycles = LSM.loadValueIntoRegister(registerHL.getHi(), registerHL, Register.RegByte.WORD);
                break;
            case 0x75:
                cycles = LSM.loadValueIntoRegister(registerHL.getLo(), registerHL, Register.RegByte.WORD);
                break;
            case 0x36:
                cycles = LSM.loadImmediateIntoRegister(programCounter + 1, LSM.ImmediateLength.BYTE, memory, registerHL, Register.RegByte.WORD);
                break;

            // LD A,n (page 68)
            case 0x0A:
                cycles = LSM.loadValueIntoRegister(registerBC.getReg(), registerAF, Register.RegByte.HI);
                break;
            case 0x1A:
                cycles = LSM.loadValueIntoRegister(registerDE.getReg(), registerAF, Register.RegByte.HI);
                break;
            case 0xFA:
                cycles = LSM.loadImmediateIntoRegister(programCounter + 1, LSM.ImmediateLength.WORD, memory, registerAF, Register.RegByte.HI);
                break;
            case 0x3E:
                cycles = LSM.loadImmediateIntoRegister(programCounter + 1, LSM.ImmediateLength.BYTE, memory, registerAF, Register.RegByte.HI);
                break;
            case 0x57:
                cycles = LSM.loadValueIntoRegister(registerDE.getHi(), registerAF, Register.RegByte.HI);
                break;
            case 0x5F:
                cycles = LSM.loadValueIntoRegister(registerDE.getLo(), registerAF, Register.RegByte.HI);
                break;
            case 0x67:
                cycles = LSM.loadValueIntoRegister(registerHL.getHi(), registerAF, Register.RegByte.HI);
                break;
            case 0x6F:
                cycles = LSM.loadValueIntoRegister(registerHL.getLo(), registerAF, Register.RegByte.HI);
                break;
            case 0x02:
                cycles = LSM.loadValueIntoRegister(registerBC.getReg(), registerAF, Register.RegByte.HI);
                break;
            case 0x12:
                cycles = LSM.loadValueIntoRegister(registerDE.getReg(), registerAF, Register.RegByte.HI);
                break;
            case 0x77:
                cycles = LSM.loadValueIntoRegister(registerHL.getReg(), registerAF, Register.RegByte.HI);
                break;
            case 0xEA:
                cycles = LSM.loadImmediateIntoRegister(programCounter + 1, LSM.ImmediateLength.BYTE, memory, registerAF, Register.RegByte.WORD);
                break;

            case 0xF2: {
                // LD A,(C)
                // value 0xFF00 + C
                // page 70
                int memoryValue = memory.readByteFromLocation(0xFF00);
                memoryValue = memoryValue + registerBC.getLo();
                cycles = LSM.loadValueIntoRegister(memoryValue, registerAF, Register.RegByte.HI);
                break;
            }

            case 0xE2: {
                // LD (C), A
                // Load Register A into location (0xFF00 + Register C)
                // page 70

                int location = 0xFF00 + registerBC.getLo();
                cycles = LSM.loadValueIntoLocation(registerAF.getHi(), location, memory);
                break;
            }

            case 0x3A: {
                // LD A, (HDL)
                // LD A, (HL-)
                // LDD A, (HL)
                // page 71
                LSM.loadValueIntoRegister(memory.readByteFromLocation(registerHL.getReg()), registerAF, Register.RegByte.HI);
                // override cycles to 8, regardless of above
                cycles = 8;
                int hlValue = registerHL.getReg();
                hlValue--;
                registerHL.setReg(hlValue);
                break;
            }

            case 0x32: {
                // LD (HDL), A
                // LD (HL-), A
                // LDD (HL), A
                // page 72
                memory.writeByteToLocation(registerAF.getHi(), registerHL.getReg());
                int hlValue = registerHL.getReg();
                hlValue--;
                registerHL.setReg(hlValue);
                cycles = 8;
                break;
            }

            case 0x2A: {
                // LD A, (HLI)
                // LD A, (HL+)
                // LDI A, *HL)
                // page 73
                LSM.loadValueIntoRegister(memory.readByteFromLocation(registerHL.getReg()), registerAF, Register.RegByte.HI);
                // override cycles to 8, regardless of above
                cycles = 8;
                int hlValue = registerHL.getReg();
                hlValue++;
                registerHL.setReg(hlValue);
                break;
            }

            case 0x22: {
                // LD (HLI), A
                // LD (HL+), A
                // LDI (HL), A
                // Page 74
                memory.writeByteToLocation(registerAF.getHi(), registerHL.getReg());
                int hlValue = registerHL.getReg();
                hlValue++;
                registerHL.setReg(hlValue);
                cycles = 8;
                break;
            }

            case 0xE0: {
                // LDH (n), A
                // page 75
                int immediate = 0xFF00 + memory.readByteFromLocation(programCounter + 1);
                LSM.loadValueIntoLocation(registerAF.getHi(), immediate, memory);
                cycles = 12;
                break;
            }

            case 0xF0: {
                // LDH A, (n)
                // page 75
                int immediate = 0xFF00 + memory.readByteFromLocation(programCounter + 1);
                LSM.loadValueIntoRegister(immediate, registerAF, Register.RegByte.HI);
                cycles = 12;
                break;
            }

// 16 bit loads
            // LD n, nn
            // page 76
            case 0x01:
                LSM.loadValueIntoRegister(memory.readWordFromLocation(programCounter + 1), registerBC, Register.RegByte.WORD);
                cycles = 12;
                break;
            case 0x11:
                LSM.loadValueIntoRegister(memory.readWordFromLocation(programCounter + 1), registerDE, Register.RegByte.WORD);
                cycles = 12;
                break;
            case 0x21:
                LSM.loadValueIntoRegister(memory.readWordFromLocation(programCounter + 1), registerHL, Register.RegByte.WORD);
                cycles = 12;
                break;
            case 0x31: {
                stackPointer = memory.readWordFromLocation(programCounter + 1);
                cycles = 12;
                break;
            }

            case 0xF9: {
                // LD SP, HL
                // page 67
                stackPointer = registerHL.getReg();
                cycles = 8;
                break;
            }

            case 0xF8: {
                // LD HL, SP+n
                // LDHL SP,n
                // page 77


                int n = memory.readByteFromLocation(programCounter);
                int value = (stackPointer + n) & 0xFFFF;

                boolean carry = ((stackPointer ^ n ^ value) & 0x100) != 0;
                boolean halfCarry = ((stackPointer ^ n ^ value) & 0x10) != 0;

                registerHL.setReg(value);

                flags.set(Flags.Flag.SUBTRACT, false);
                flags.set(Flags.Flag.ZERO, false);
                flags.set(Flags.Flag.CARRY, carry);
                flags.set(Flags.Flag.HALF_CARRY, halfCarry);

                cycles = 12;

                break;
            }

            case 0x08: {
                // LD (nn), SP
                // page 78
                int immediate = memory.readWordFromLocation(programCounter + 1);
                LSM.loadValueIntoLocation(stackPointer, immediate, memory);
                cycles = 20;
                break;
            }

            // PUSH nn
            // page 78
            case 0xF5: {
                cycles = push(registerAF);
                break;
            }
            case 0xC5: {
                cycles = push(registerBC);
                break;
            }
            case 0xD5: {
                cycles = push(registerDE);
                break;
            }
            case 0xE5: {
                cycles = push(registerHL);
                break;
            }

            // POP nn
            // page 79
            case 0xF1: {
                cycles = pop(registerAF);
                break;
            }
            case 0xC1: {
                cycles = pop(registerBC);
                break;
            }
            case 0xD1: {
                cycles = pop(registerDE);
                break;
            }
            case 0xE1: {
                cycles = pop(registerHL);
                break;
            }

            // 8bit ALU
            // ADD A,n
            // page 80
            case 0x87: {
                addIntoA(registerAF.getHi());
                cycles = 4;
                break;
            }
            case 0x80: {
                addIntoA(registerBC.getHi());
                cycles = 4;
                break;
            }
            case 0x81: {
                addIntoA(registerBC.getLo());
                cycles = 4;
                break;
            }
            case 0x82: {
                addIntoA(registerDE.getHi());
                cycles = 4;
                break;
            }
            case 0x83: {
                addIntoA(registerDE.getLo());
                cycles = 4;
                break;
            }
            case 0x84: {
                addIntoA(registerHL.getHi());
                cycles = 4;
                break;
            }
            case 0x85: {
                addIntoA(registerHL.getLo());
                cycles = 4;
                break;
            }
            case 0x86: {
                addIntoA(registerHL.getReg());
                cycles = 8;
                break;
            }
            case 0xC6: {
                addIntoA(memory.readByteFromLocation(programCounter + 1));
                cycles = 8;
                break;
            }

            // ADC A,n
            // page 81
            case 0x8F: {
                addWithCarryIntoA(registerAF.getHi());
                cycles = 4;
                break;
            }
            case 0x88: {
                addWithCarryIntoA(registerBC.getHi());
                cycles = 4;
                break;
            }
            case 0x89: {
                addWithCarryIntoA(registerBC.getLo());
                cycles = 4;
                break;
            }
            case 0x8A: {
                addWithCarryIntoA(registerDE.getHi());
                cycles = 4;
                break;
            }
            case 0x8B: {
                addWithCarryIntoA(registerDE.getLo());
                cycles = 4;
                break;
            }
            case 0x8C: {
                addWithCarryIntoA(registerHL.getHi());
                cycles = 4;
                break;
            }
            case 0x8D: {
                addWithCarryIntoA(registerHL.getLo());
                cycles = 4;
                break;
            }
            case 0x8E: {
                addWithCarryIntoA(registerHL.getReg());
                cycles = 8;
                break;
            }
            case 0xCE: {
                addWithCarryIntoA(memory.readByteFromLocation(programCounter + 1));
                cycles = 8;
                break;
            }

            // SBC A,n
            // page 83
            case 0x97:
                subtractFromA(registerAF.getHi());
                cycles = 4;
                break;
            case 0x90:
                subtractFromA(registerBC.getHi());
                cycles = 4;
                break;
            case 0x91:
                subtractFromA(registerBC.getLo());
                cycles = 4;
                break;
            case 0x92:
                subtractFromA(registerDE.getHi());
                cycles = 4;
                break;
            case 0x93:
                subtractFromA(registerDE.getLo());
                cycles = 4;
                break;
            case 0x94:
                subtractFromA(registerHL.getHi());
                cycles = 4;
                break;
            case 0x95:
                subtractFromA(registerHL.getLo());
                cycles = 4;
                break;
            case 0x96:
                subtractFromA(registerHL.getReg());
                cycles = 8;
                break;
            case 0xD6:
                subtractFromA(memory.readByteFromLocation(programCounter + 1));
                cycles = 8;
                break;


            // SBC A,n
            // page 83
            case 0x9F: {
                subtractWithCarryFromA(registerAF.getHi());
                cycles = 4;
                break;
            }
            case 0x98: {
                subtractWithCarryFromA(registerBC.getHi());
                cycles = 4;
                break;
            }

            case 0x99: {
                subtractWithCarryFromA(registerBC.getLo());
                cycles = 4;
                break;
            }

            case 0x9A: {
                subtractWithCarryFromA(registerDE.getHi());
                cycles = 4;
                break;
            }
            case 0x9B: {
                subtractWithCarryFromA(registerDE.getLo());
                cycles = 4;
                break;
            }
            case 0x9C: {
                subtractWithCarryFromA(registerHL.getHi());
                cycles = 4;
                break;
            }
            case 0x9D: {
                subtractWithCarryFromA(registerHL.getLo());
                cycles = 4;
                break;
            }
            case 0x9E: {
                subtractWithCarryFromA(registerHL.getReg());
                cycles = 8;
                break;
            }

            // AND n
            // page 84
            case 0xA7: {
                andIntoA(registerAF.getHi());
                cycles = 4;
                break;
            }
            case 0xA0: {
                andIntoA(registerBC.getHi());
                cycles = 4;
                break;
            }
            case 0xA1: {
                andIntoA(registerBC.getLo());
                cycles = 4;
                break;
            }
            case 0xA2: {
                andIntoA(registerDE.getHi());
                cycles = 4;
                break;
            }
            case 0xA3: {
                andIntoA(registerDE.getLo());
                cycles = 4;
                break;
            }
            case 0xA4: {
                andIntoA(registerHL.getHi());
                cycles = 4;
                break;
            }
            case 0xA5: {
                andIntoA(registerHL.getLo());
                cycles = 4;
                break;
            }
            case 0xA6: {
                andIntoA(registerHL.getReg());
                cycles = 8;
                break;
            }
            case 0xE6: {
                andIntoA(memory.readByteFromLocation(programCounter + 1));
                cycles = 8;
                break;
            }

            // OR n
            // page 85
            case 0xB7: {
                orIntoA(registerAF.getHi());
                cycles = 4;
                break;
            }
            case 0xB0: {
                orIntoA(registerBC.getHi());
                cycles = 4;
                break;
            }
            case 0xB1: {
                orIntoA(registerBC.getLo());
                cycles = 4;
                break;
            }
            case 0xB2: {
                orIntoA(registerDE.getHi());
                cycles = 4;
                break;
            }
            case 0xB3: {
                orIntoA(registerDE.getLo());
                cycles = 4;
                break;
            }
            case 0xB4: {
                orIntoA(registerHL.getHi());
                cycles = 4;
                break;
            }
            case 0xB5: {
                orIntoA(registerHL.getLo());
                cycles = 4;
                break;
            }
            case 0xB6: {
                orIntoA(registerHL.getReg());
                cycles = 8;
                break;
            }
            case 0xF6: {
                orIntoA(memory.readByteFromLocation(programCounter + 1));
                cycles = 8;
                break;
            }

            // XOR n
            // page 86
            case 0xAF:
                xorIntoA(registerAF.getHi());
                cycles = 4;
                break;
            case 0xA8:
                xorIntoA(registerBC.getHi());
                cycles = 4;
                break;
            case 0xA9:
                xorIntoA(registerBC.getLo());
                cycles = 4;
                break;
            case 0xAA:
                xorIntoA(registerDE.getHi());
                cycles = 4;
                break;
            case 0xAB:
                xorIntoA(registerDE.getLo());
                cycles = 4;
                break;
            case 0xAC:
                xorIntoA(registerHL.getHi());
                cycles = 4;
                break;
            case 0xAD:
                xorIntoA(registerHL.getLo());
                cycles = 4;
                break;
            case 0xAE:
                xorIntoA(registerHL.getReg());
                cycles = 8;
                break;
            case 0xEE: {
                xorIntoA(memory.readByteFromLocation(programCounter + 1));
                cycles = 8;
                break;
            }

            // CP n
            // page 87
            case 0xBF:
                cpToA(registerAF.getHi());
                cycles = 4;
                break;
            case 0xB8:
                cpToA(registerBC.getHi());
                cycles = 4;
                break;
            case 0xB9:
                cpToA(registerBC.getLo());
                cycles = 4;
                break;
            case 0xBA:
                cpToA(registerDE.getHi());
                cycles = 4;
                break;
            case 0xBB:
                cpToA(registerDE.getLo());
                cycles = 4;
                break;
            case 0xBC:
                cpToA(registerHL.getHi());
                cycles = 4;
                break;
            case 0xBD:
                cpToA(registerHL.getLo());
                cycles = 4;
                break;
            case 0xBE:
                cpToA(registerHL.getReg());
                cycles = 8;
                break;
            case 0xFE: {
                cpToA(memory.readByteFromLocation(programCounter + 1));
                cycles = 8;
                break;
            }

            // INC n
            // page 88
            case 0x3C:
                incRegister(registerAF, Register.RegByte.HI);
                cycles = 4;
                break;
            case 0x04:
                incRegister(registerBC, Register.RegByte.HI);
                cycles = 4;
                break;
            case 0x0C:
                incRegister(registerBC, Register.RegByte.LO);
                cycles = 4;
                break;
            case 0x14:
                incRegister(registerDE, Register.RegByte.HI);
                cycles = 4;
                break;
            case 0x1C:
                incRegister(registerDE, Register.RegByte.LO);
                cycles = 4;
                break;
            case 0x24:
                incRegister(registerHL, Register.RegByte.HI);
                cycles = 4;
                break;
            case 0x2C:
                incRegister(registerHL, Register.RegByte.LO);
                cycles = 4;
                break;
            case 0x34: {
                incRegister(registerHL, Register.RegByte.WORD);
                cycles = 12;
                break;
            }

            // DEC n
            // page 89
            case 0x3D:
                decRegister(registerAF, Register.RegByte.HI);
                cycles = 4;
                break;
            case 0x05:
                decRegister(registerBC, Register.RegByte.HI);
                cycles = 4;
                break;
            case 0x0D:
                decRegister(registerBC, Register.RegByte.LO);
                cycles = 4;
                break;
            case 0x15:
                decRegister(registerDE, Register.RegByte.HI);
                cycles = 4;
                break;
            case 0x1D:
                decRegister(registerDE, Register.RegByte.LO);
                cycles = 4;
                break;
            case 0x25:
                decRegister(registerHL, Register.RegByte.HI);
                cycles = 4;
                break;
            case 0x2D:
                decRegister(registerHL, Register.RegByte.LO);
                cycles = 4;
                break;
            case 0x35: {
                decRegister(registerHL, Register.RegByte.WORD);
                cycles = 12;
                break;
            }

            // 16 bit arithmetic
            // ADD HL, n
            // page 90
            case 0x09:
                addIntoHL(registerBC.getReg());
                cycles = 8;
                break;
            case 0x19:
                addIntoHL(registerDE.getReg());
                cycles = 8;
                break;
            case 0x29:
                addIntoHL(registerHL.getReg());
                cycles = 8;
                break;
            case 0x39: {
                addIntoHL(stackPointer);
                cycles = 8;

                break;
            }

            case 0xE8: {
                // ADD SP, n
                // page 91
                int value = memory.readByteFromLocation(programCounter + 1);
                int result = stackPointer + value;

                boolean carry = result > 0xFFFF;
                result = result & 0xFFFF;
                boolean halfCarry = ((result ^ stackPointer ^ value) & 0x1000) != 0;

                stackPointer = result;
                flags.set(Flags.Flag.ZERO, false);
                flags.set(Flags.Flag.SUBTRACT, false);
                flags.set(Flags.Flag.HALF_CARRY, halfCarry);
                flags.set(Flags.Flag.CARRY, carry);

                cycles = 16;

                break;
            }

            // INC nn
            // page 92
            case 0x03:
                registerBC.setReg(registerBC.getReg() + 1);
                cycles = 8;
                break;
            case 0x13:
                registerDE.setReg(registerDE.getReg() + 1);
                cycles = 8;
                break;
            case 0x23:
                registerHL.setReg(registerHL.getReg() + 1);
                cycles = 8;
                break;
            case 0x33: {
                stackPointer++;
                cycles = 8;
                break;
            }

            // DEC nn
            // page 93
            case 0x0B:
                registerBC.setReg(registerBC.getReg() - 1);
                cycles = 8;
                break;
            case 0x1B:
                registerDE.setReg(registerDE.getReg() - 1);
                cycles = 8;
                break;
            case 0x2B:
                registerHL.setReg(registerHL.getReg() - 1);
                cycles = 8;
                break;
            case 0x3B: {
                stackPointer--;
                cycles = 8;
                break;
            }

            case 0x27: {
                // DAA
                // page 95
                // likely no operation needed huh
                cycles = 4;
                flags.set(Flags.Flag.ZERO, registerAF.getHi() == 0);
                flags.set(Flags.Flag.HALF_CARRY, false);

                // probably don't need to touch carry flag

                break;
            }

            case 0x2F: {
                // CPL
                // page 95
                int value = registerAF.getHi();
                value = Helpful.setBit(value, 0, !(boolean) Helpful.getBit(value, 0));
                value = Helpful.setBit(value, 1, !(boolean) Helpful.getBit(value, 1));
                value = Helpful.setBit(value, 2, !(boolean) Helpful.getBit(value, 2));
                value = Helpful.setBit(value, 3, !(boolean) Helpful.getBit(value, 3));
                value = Helpful.setBit(value, 4, !(boolean) Helpful.getBit(value, 4));
                value = Helpful.setBit(value, 5, !(boolean) Helpful.getBit(value, 5));
                value = Helpful.setBit(value, 6, !(boolean) Helpful.getBit(value, 6));
                value = Helpful.setBit(value, 7, !(boolean) Helpful.getBit(value, 7));
                registerAF.setHi(value);
                cycles = 4;
                break;
            }

            case 0x3F: {
                // CCF
                // page 96
                flags.set(Flags.Flag.CARRY, !flags.isSet(Flags.Flag.CARRY));

                flags.set(Flags.Flag.SUBTRACT, false);
                flags.set(Flags.Flag.HALF_CARRY, false);
                cycles = 4;
                break;
            }

            case 0x37: {
                // SCF
                // page 96
                flags.set(Flags.Flag.CARRY, true);

                flags.set(Flags.Flag.SUBTRACT, false);
                flags.set(Flags.Flag.HALF_CARRY, false);
                cycles = 4;
                break;
            }

            case 0x00: {
                // NOP
                // page 97
                cycles = 4;
                break;
            }

            case 0x76: {
                // HALT
                // page 97
                cycles = 4;
                break;
            }

            case 0xF3: {
                // DI
                // page 98
                // Disable Interrupts
                interruptsEnabled = false;
                cycles = 4;

                break;
            }

            case 0xFB: {
                // EI
                // page 98
                // Enable Interrupts
                interruptsEnabled = true;
                cycles = 4;

                break;
            }

            case 0x07: {
                // RLCA
                // page 99

                cycles = rotate(RotateDirection.LEFT, registerAF, Register.RegByte.HI, true);
                break;
            }

            case 0x17: {
                // RLA
                // page 99

                cycles = rotate(RotateDirection.LEFT, registerAF, Register.RegByte.HI, false);
                break;
            }

            case 0x0F: {
                // RRCA
                // page 100

                cycles = rotate(RotateDirection.RIGHT, registerAF, Register.RegByte.HI, true);

                break;
            }

            case 0x1F: {
                // RRA
                // page 100
                cycles = rotate(RotateDirection.RIGHT, registerAF, Register.RegByte.HI, false);
                break;
            }

            // Jumps
            // JP nn
            // page 111
            case 0xC3: {

                forceProgramCounterToPosition(this.memory.readWordFromLocation(programCounter + 1), true);
                // cycles might not be reflected here...
                cycles = 12;
                break;
            }

            // JP cc,nn
            // page 111
            case 0xC2: {
                // if Z flag is reset
                if (!flags.isSet(Flags.Flag.ZERO)) {
                    forceProgramCounterToPosition(this.memory.readWordFromLocation(programCounter + 1), true);
                }
                cycles = 12;
                break;
            }
            case 0xCA: {
                if (flags.isSet(Flags.Flag.ZERO)) {
                    forceProgramCounterToPosition(this.memory.readWordFromLocation(programCounter + 1), true);
                }
                cycles = 12;
                break;
            }
            case 0xD2: {
                if (!flags.isSet(Flags.Flag.CARRY)) {
                    forceProgramCounterToPosition(this.memory.readWordFromLocation(programCounter + 1), true);
                }
                cycles = 12;
                break;
            }
            case 0xDA: {

                if (flags.isSet(Flags.Flag.CARRY)) {
                    forceProgramCounterToPosition(this.memory.readWordFromLocation(programCounter + 1), true);
                }
                cycles = 12;
                break;
            }

            case 0xE9: {
                // JP (HL)
                // page 112
                forceProgramCounterToPosition(registerHL.getReg(), true);
                cycles = 4;
                break;
            }

            case 0x18: {
                // JR n
                // page 112
                int immediateByte = this.memory.readByteFromLocation(programCounter + 1);
                programCounter = programCounter + immediateByte;
                forceProgramCounterToPosition(programCounter, true);
                cycles = 8;
                break;
            }


            // JR cc,n
            // page 113

            case 0x20: {
                if (!flags.isSet(Flags.Flag.ZERO)) {
                    forceProgramCounterToPosition(this.memory.readByteFromLocation(programCounter + 1), true);
                }
                cycles = 8;
                break;
            }
            case 0x28: {
                if (flags.isSet(Flags.Flag.ZERO)) {
                    forceProgramCounterToPosition(this.memory.readByteFromLocation(programCounter + 1), true);
                }
                cycles = 8;
                break;
            }
            case 0x30: {
                if (!flags.isSet(Flags.Flag.CARRY)) {
                    forceProgramCounterToPosition(this.memory.readByteFromLocation(programCounter + 1), true);
                }
                cycles = 8;
                break;
            }
            case 0x38: {

                if (flags.isSet(Flags.Flag.CARRY)) {
                    forceProgramCounterToPosition(this.memory.readByteFromLocation(programCounter + 1), true);
                }
                cycles = 8;
                break;
            }

            // Calls
            // CALL nn
            // page 114
            case 0xCD: {

                int secondByte = this.memory.readByteFromLocation(programCounter + 1);
                stackPointer = programCounter;
                forceProgramCounterToPosition(secondByte, true);
                cycles = 12;
                break;
            }

            // CALL cc,nn
            // page 115

            case 0xC4: {
                if (!flags.isSet(Flags.Flag.ZERO)) {
                    int secondByte = this.memory.readByteFromLocation(programCounter + 1);
                    stackPointer = programCounter;
                    forceProgramCounterToPosition(secondByte, true);
                    cycles = 12;
                    break;
                }
                cycles = 8;
                break;
            }
            case 0xCC: {
                if (flags.isSet(Flags.Flag.ZERO)) {
                    int secondByte = this.memory.readByteFromLocation(programCounter + 1);
                    stackPointer = programCounter;
                    forceProgramCounterToPosition(secondByte, true);
                    cycles = 12;
                    break;
                }
                cycles = 8;
                break;
            }
            case 0xD4: {
                if (!flags.isSet(Flags.Flag.CARRY)) {
                    int secondByte = this.memory.readByteFromLocation(programCounter + 1);
                    stackPointer = programCounter;
                    forceProgramCounterToPosition(secondByte, true);
                    cycles = 12;
                    break;
                }
                cycles = 8;
                break;
            }
            case 0xDC: {
                if (flags.isSet(Flags.Flag.CARRY)) {
                    int secondByte = this.memory.readByteFromLocation(programCounter + 1);
                    stackPointer = programCounter;
                    forceProgramCounterToPosition(secondByte, true);
                    cycles = 12;
                    break;
                }
                cycles = 8;
                break;

            }

            // Restarts
            // RST n
            // page 116
            case 0xC7: {
                stackPointer = programCounter;
                forceProgramCounterToPosition(0x00, true);
                cycles = 32;
                break;
            }
            case 0xCF: {
                stackPointer = programCounter;
                forceProgramCounterToPosition(0x08, true);
                cycles = 32;
                break;
            }
            case 0xD7: {
                stackPointer = programCounter;
                forceProgramCounterToPosition(0x10, true);
                cycles = 32;
                break;
            }
            case 0xDF: {
                stackPointer = programCounter;
                forceProgramCounterToPosition(0x18, true);
                cycles = 32;
                break;
            }
            case 0xE7: {
                stackPointer = programCounter;
                forceProgramCounterToPosition(0x20, true);
                cycles = 32;
                break;
            }
            case 0xEF: {
                stackPointer = programCounter;
                forceProgramCounterToPosition(0x28, true);
                cycles = 32;
                break;
            }
            case 0xF7: {
                stackPointer = programCounter;
                forceProgramCounterToPosition(0x30, true);
                cycles = 32;
                break;
            }
            case 0xFF: {
                stackPointer = programCounter;
                forceProgramCounterToPosition(0x38, true);
                cycles = 32;
                break;


            }

            // Returns
            // RET
            // page 117
            case 0xC9: {

                int address = stackPointer;
                int value = (memory.readByteFromLocation(address + 1) << 8) | memory.readByteFromLocation(address);
                stackPointer = stackPointer + 2;
                forceProgramCounterToPosition(value, true);
                cycles = 8;
                break;
            }


            // RET cc
            // page 117
            case 0xC0: {
                if (!flags.isSet(Flags.Flag.ZERO)) {
                    int address = stackPointer;
                    int value = (memory.readByteFromLocation(address + 1) << 8) | memory.readByteFromLocation(address);
                    stackPointer = stackPointer + 2;
                    forceProgramCounterToPosition(value, true);
                }
                cycles = 8;
                break;
            }
            case 0xC8: {
                if (flags.isSet(Flags.Flag.ZERO)) {
                    int address = stackPointer;
                    int value = (memory.readByteFromLocation(address + 1) << 8) | memory.readByteFromLocation(address);
                    stackPointer = stackPointer + 2;
                    forceProgramCounterToPosition(value, true);
                }
                cycles = 8;
                break;
            }
            case 0xD0: {
                if (!flags.isSet(Flags.Flag.CARRY)) {
                    int address = stackPointer;
                    int value = (memory.readByteFromLocation(address + 1) << 8) | memory.readByteFromLocation(address);
                    stackPointer = stackPointer + 2;
                    forceProgramCounterToPosition(value, true);
                }
                cycles = 8;
                break;
            }
            case 0xD8: {
                if (flags.isSet(Flags.Flag.CARRY)) {
                    int address = stackPointer;
                    int value = (memory.readByteFromLocation(address + 1) << 8) | memory.readByteFromLocation(address);
                    stackPointer = stackPointer + 2;
                    forceProgramCounterToPosition(value, true);
                }
                cycles = 8;

                break;
            }

            case 0xD9: {
                // RETI
                // page 118
                int address = stackPointer;
                int value = (memory.readByteFromLocation(address + 1) << 8) | memory.readByteFromLocation(address);
                stackPointer = stackPointer + 2;
                forceProgramCounterToPosition(value, true);
                cycles = 8;
                interruptsEnabled = true;
                break;
            }


            case 0x10: {
                int secondOpcode = this.memory.generalMemory[programCounter + 1];

                if (secondOpcode == 0x00) {
                    // STOP
                    // page 97
                    cycles = 4;
                } else {
                    System.out.printf(" :: 0x%x missing :: ", secondOpcode);
                }
                break;
            }


            case 0xCB: {
                int secondOpcode = this.memory.generalMemory[programCounter + 1];
                switch (secondOpcode) {
                    // SWAP n
                    // page 94
                    case 0x37:
                        swapRegister(registerAF, Register.RegByte.HI);
                        cycles = 8;
                        break;
                    case 0x30:
                        swapRegister(registerBC, Register.RegByte.HI);
                        cycles = 8;
                        break;
                    case 0x31:
                        swapRegister(registerBC, Register.RegByte.LO);
                        cycles = 8;
                        break;
                    case 0x32:
                        swapRegister(registerDE, Register.RegByte.HI);
                        cycles = 8;
                        break;
                    case 0x33:
                        swapRegister(registerDE, Register.RegByte.LO);
                        cycles = 8;
                        break;
                    case 0x34:
                        swapRegister(registerHL, Register.RegByte.HI);
                        cycles = 8;
                        break;
                    case 0x35:
                        swapRegister(registerHL, Register.RegByte.LO);
                        cycles = 8;
                        break;
                    case 0x36:
                        swapRegister(registerHL, Register.RegByte.WORD);
                        cycles = 16;
                        break;

                    case 0x07: {
                        cycles = rotate(RotateDirection.LEFT, registerAF, Register.RegByte.HI, true);
                        // Add 4 to the cycle count because 0xCB07 is different to 0x07 for some reason?
                        cycles += 4;
                        break;
                    }

                    // RLC n
                    // page 101
                    case 0x00:
                        cycles = rotate(RotateDirection.LEFT, registerBC, Register.RegByte.HI, true);
                        break;
                    case 0x01:
                        cycles = rotate(RotateDirection.LEFT, registerBC, Register.RegByte.LO, true);
                        break;
                    case 0x02:
                        cycles = rotate(RotateDirection.LEFT, registerDE, Register.RegByte.HI, true);
                        break;
                    case 0x03:
                        cycles = rotate(RotateDirection.LEFT, registerDE, Register.RegByte.LO, true);
                        break;
                    case 0x04:
                        cycles = rotate(RotateDirection.LEFT, registerHL, Register.RegByte.HI, true);
                        break;
                    case 0x05:
                        cycles = rotate(RotateDirection.LEFT, registerHL, Register.RegByte.LO, true);
                        break;
                    case 0x06:
                        rotate(RotateDirection.LEFT, registerHL, Register.RegByte.WORD, true);
                        cycles = 16;
                        break;


                    // RL n
                    // page 102
                    case 0x17: {
                        cycles = rotate(RotateDirection.LEFT, registerAF, Register.RegByte.HI, false);
                        // Add 4 to the cycle count because 0xCB07 is different to 0x07 for some reason?
                        cycles += 4;
                        break;
                    }
                    case 0x10:
                        cycles = rotate(RotateDirection.LEFT, registerBC, Register.RegByte.HI, false);
                        break;
                    case 0x11:
                        cycles = rotate(RotateDirection.LEFT, registerBC, Register.RegByte.LO, false);
                        break;
                    case 0x12:
                        cycles = rotate(RotateDirection.LEFT, registerDE, Register.RegByte.HI, false);
                        break;
                    case 0x13:
                        cycles = rotate(RotateDirection.LEFT, registerDE, Register.RegByte.LO, false);
                        break;
                    case 0x14:
                        cycles = rotate(RotateDirection.LEFT, registerHL, Register.RegByte.HI, false);
                        break;
                    case 0x15:
                        cycles = rotate(RotateDirection.LEFT, registerHL, Register.RegByte.LO, false);
                        break;
                    case 0x16: {
                        rotate(RotateDirection.LEFT, registerHL, Register.RegByte.WORD, false);
                        cycles = 16;
                        break;
                    }

                    // RRC n
                    // page 103

                    case 0x0F: {
                        cycles = rotate(RotateDirection.RIGHT, registerAF, Register.RegByte.HI, true);
                        // Add 4 to the cycle count because 0xCB07 is different to 0x07 for some reason?
                        cycles += 4;
                        break;
                    }
                    case 0x08:
                        cycles = rotate(RotateDirection.RIGHT, registerBC, Register.RegByte.HI, true);
                        break;
                    case 0x09:
                        cycles = rotate(RotateDirection.RIGHT, registerBC, Register.RegByte.LO, true);
                        break;
                    case 0x0A:
                        cycles = rotate(RotateDirection.RIGHT, registerDE, Register.RegByte.HI, true);
                        break;
                    case 0x0B:
                        cycles = rotate(RotateDirection.RIGHT, registerDE, Register.RegByte.LO, true);
                        break;
                    case 0x0C:
                        cycles = rotate(RotateDirection.RIGHT, registerHL, Register.RegByte.HI, true);
                        break;
                    case 0x0D:
                        cycles = rotate(RotateDirection.RIGHT, registerHL, Register.RegByte.LO, true);
                        break;
                    case 0x0E: {

                        rotate(RotateDirection.RIGHT, registerHL, Register.RegByte.WORD, true);
                        cycles = 16;
                        break;
                    }

                    // RR n
                    // page 104
                    case 0x1F: {
                        cycles = rotate(RotateDirection.RIGHT, registerAF, Register.RegByte.HI, false);
                        cycles += 4;
                        break;
                    }
                    case 0x18:
                        cycles = rotate(RotateDirection.RIGHT, registerBC, Register.RegByte.HI, false);
                        break;
                    case 0x19:
                        cycles = rotate(RotateDirection.RIGHT, registerBC, Register.RegByte.LO, false);
                        break;
                    case 0x1A:
                        cycles = rotate(RotateDirection.RIGHT, registerDE, Register.RegByte.HI, false);
                        break;
                    case 0x1B:
                        cycles = rotate(RotateDirection.RIGHT, registerDE, Register.RegByte.LO, false);
                        break;
                    case 0x1C:
                        cycles = rotate(RotateDirection.RIGHT, registerHL, Register.RegByte.HI, false);
                        break;
                    case 0x1D:
                        cycles = rotate(RotateDirection.RIGHT, registerHL, Register.RegByte.LO, false);
                        break;
                    case 0x1E: {

                        rotate(RotateDirection.RIGHT, registerHL, Register.RegByte.WORD, false);
                        cycles = 16;
                        break;

                    }


                    // SLA n
                    // page 105
                    case 0x27:
                        cycles = shift(RotateDirection.LEFT, registerAF, Register.RegByte.HI, false);
                        break;
                    case 0x20:
                        cycles = shift(RotateDirection.LEFT, registerBC, Register.RegByte.HI, false);
                        break;
                    case 0x21:
                        cycles = shift(RotateDirection.LEFT, registerBC, Register.RegByte.LO, false);
                        break;
                    case 0x22:
                        cycles = shift(RotateDirection.LEFT, registerDE, Register.RegByte.HI, false);
                        break;
                    case 0x23:
                        cycles = shift(RotateDirection.LEFT, registerDE, Register.RegByte.LO, false);
                        break;
                    case 0x24:
                        cycles = shift(RotateDirection.LEFT, registerHL, Register.RegByte.HI, false);
                        break;
                    case 0x25:
                        cycles = shift(RotateDirection.LEFT, registerHL, Register.RegByte.LO, false);
                        break;
                    case 0x26:
                        cycles = shift(RotateDirection.LEFT, registerHL, Register.RegByte.WORD, false);
                        break;

                    // SRA n
                    // page 106
                    case 0x2F:
                        cycles = shift(RotateDirection.RIGHT, registerAF, Register.RegByte.HI, false);
                        break;
                    case 0x28:
                        cycles = shift(RotateDirection.RIGHT, registerBC, Register.RegByte.HI, false);
                        break;
                    case 0x29:
                        cycles = shift(RotateDirection.RIGHT, registerBC, Register.RegByte.LO, false);
                        break;
                    case 0x2A:
                        cycles = shift(RotateDirection.RIGHT, registerDE, Register.RegByte.HI, false);
                        break;
                    case 0x2B:
                        cycles = shift(RotateDirection.RIGHT, registerDE, Register.RegByte.LO, false);
                        break;
                    case 0x2C:
                        cycles = shift(RotateDirection.RIGHT, registerHL, Register.RegByte.HI, false);
                        break;
                    case 0x2D:
                        cycles = shift(RotateDirection.RIGHT, registerHL, Register.RegByte.LO, false);
                        break;
                    case 0x2E:
                        cycles = shift(RotateDirection.RIGHT, registerHL, Register.RegByte.WORD, false);
                        break;

                    // SRL n
                    // page 107
                    case 0x3F:
                        cycles = shift(RotateDirection.RIGHT, registerAF, Register.RegByte.HI, true);
                        break;
                    case 0x38:
                        cycles = shift(RotateDirection.RIGHT, registerBC, Register.RegByte.HI, true);
                        break;
                    case 0x39:
                        cycles = shift(RotateDirection.RIGHT, registerBC, Register.RegByte.LO, true);
                        break;
                    case 0x3A:
                        cycles = shift(RotateDirection.RIGHT, registerDE, Register.RegByte.HI, true);
                        break;
                    case 0x3B:
                        cycles = shift(RotateDirection.RIGHT, registerDE, Register.RegByte.LO, true);
                        break;
                    case 0x3C:
                        cycles = shift(RotateDirection.RIGHT, registerHL, Register.RegByte.HI, true);
                        break;
                    case 0x3D:
                        cycles = shift(RotateDirection.RIGHT, registerHL, Register.RegByte.LO, true);
                        break;
                    case 0x3E:
                        cycles = shift(RotateDirection.RIGHT, registerHL, Register.RegByte.WORD, true);
                        break;


                    // Bit Opcodes

                    // BIT b,r
                    // 0xCB40 -> 0xCB70
                    // 40 -> 47 :: bit 0 (B, C, D, E, F, H, L, (HL), A)
                    // 48 -> 4F :: bit 1
                    // 50 -> 57 :: bit 2
                    // 58 -> 5F :: bit 3
                    // 60 -> 67 :: bit 4
                    // 68 -> 6F :: bit 5
                    // 70 -> 77 :: bit 6
                    // 78 -> 7F :: bit 7

                    // bit 0
                    case 0x40:
                        cycles = testRegisterBit(registerBC, Register.RegByte.HI, 0);
                        break;
                    case 0x41:
                        cycles = testRegisterBit(registerBC, Register.RegByte.LO, 0);
                        break;
                    case 0x42:
                        cycles = testRegisterBit(registerDE, Register.RegByte.HI, 0);
                        break;
                    case 0x43:
                        cycles = testRegisterBit(registerDE, Register.RegByte.LO, 0);
                        break;
                    case 0x44:
                        cycles = testRegisterBit(registerHL, Register.RegByte.HI, 0);
                        break;
                    case 0x45:
                        cycles = testRegisterBit(registerHL, Register.RegByte.LO, 0);
                        break;
                    case 0x46:
                        cycles = testRegisterBit(registerHL, Register.RegByte.WORD, 0);
                        break;
                    case 0x47:
                        cycles = testRegisterBit(registerAF, Register.RegByte.HI, 0);
                        break;

                    // bit 1
                    case 0x48:
                        cycles = testRegisterBit(registerBC, Register.RegByte.HI, 1);
                        break;
                    case 0x49:
                        cycles = testRegisterBit(registerBC, Register.RegByte.LO, 1);
                        break;
                    case 0x4A:
                        cycles = testRegisterBit(registerDE, Register.RegByte.HI, 1);
                        break;
                    case 0x4B:
                        cycles = testRegisterBit(registerDE, Register.RegByte.LO, 1);
                        break;
                    case 0x4C:
                        cycles = testRegisterBit(registerHL, Register.RegByte.HI, 1);
                        break;
                    case 0x4D:
                        cycles = testRegisterBit(registerHL, Register.RegByte.LO, 1);
                        break;
                    case 0x4E:
                        cycles = testRegisterBit(registerHL, Register.RegByte.WORD, 1);
                        break;
                    case 0x4F:
                        cycles = testRegisterBit(registerAF, Register.RegByte.HI, 1);
                        break;

                    // bit 2
                    case 0x50:
                        cycles = testRegisterBit(registerBC, Register.RegByte.HI, 2);
                        break;
                    case 0x51:
                        cycles = testRegisterBit(registerBC, Register.RegByte.LO, 2);
                        break;
                    case 0x52:
                        cycles = testRegisterBit(registerDE, Register.RegByte.HI, 2);
                        break;
                    case 0x53:
                        cycles = testRegisterBit(registerDE, Register.RegByte.LO, 2);
                        break;
                    case 0x54:
                        cycles = testRegisterBit(registerHL, Register.RegByte.HI, 2);
                        break;
                    case 0x55:
                        cycles = testRegisterBit(registerHL, Register.RegByte.LO, 2);
                        break;
                    case 0x56:
                        cycles = testRegisterBit(registerHL, Register.RegByte.WORD, 2);
                        break;
                    case 0x57:
                        cycles = testRegisterBit(registerAF, Register.RegByte.HI, 2);
                        break;

                    // bit 3
                    case 0x58:
                        cycles = testRegisterBit(registerBC, Register.RegByte.HI, 3);
                        break;
                    case 0x59:
                        cycles = testRegisterBit(registerBC, Register.RegByte.LO, 3);
                        break;
                    case 0x5A:
                        cycles = testRegisterBit(registerDE, Register.RegByte.HI, 3);
                        break;
                    case 0x5B:
                        cycles = testRegisterBit(registerDE, Register.RegByte.LO, 3);
                        break;
                    case 0x5C:
                        cycles = testRegisterBit(registerHL, Register.RegByte.HI, 3);
                        break;
                    case 0x5D:
                        cycles = testRegisterBit(registerHL, Register.RegByte.LO, 3);
                        break;
                    case 0x5E:
                        cycles = testRegisterBit(registerHL, Register.RegByte.WORD, 3);
                        break;
                    case 0x5F:
                        cycles = testRegisterBit(registerAF, Register.RegByte.HI, 3);
                        break;

                    // bit 4
                    case 0x60:
                        cycles = testRegisterBit(registerBC, Register.RegByte.HI, 4);
                        break;
                    case 0x61:
                        cycles = testRegisterBit(registerBC, Register.RegByte.LO, 4);
                        break;
                    case 0x62:
                        cycles = testRegisterBit(registerDE, Register.RegByte.HI, 4);
                        break;
                    case 0x63:
                        cycles = testRegisterBit(registerDE, Register.RegByte.LO, 4);
                        break;
                    case 0x64:
                        cycles = testRegisterBit(registerHL, Register.RegByte.HI, 4);
                        break;
                    case 0x65:
                        cycles = testRegisterBit(registerHL, Register.RegByte.LO, 4);
                        break;
                    case 0x66:
                        cycles = testRegisterBit(registerHL, Register.RegByte.WORD, 4);
                        break;
                    case 0x67:
                        cycles = testRegisterBit(registerAF, Register.RegByte.HI, 4);
                        break;

                    // bit 5
                    case 0x68:
                        cycles = testRegisterBit(registerBC, Register.RegByte.HI, 5);
                        break;
                    case 0x69:
                        cycles = testRegisterBit(registerBC, Register.RegByte.LO, 5);
                        break;
                    case 0x6A:
                        cycles = testRegisterBit(registerDE, Register.RegByte.HI, 5);
                        break;
                    case 0x6B:
                        cycles = testRegisterBit(registerDE, Register.RegByte.LO, 5);
                        break;
                    case 0x6C:
                        cycles = testRegisterBit(registerHL, Register.RegByte.HI, 5);
                        break;
                    case 0x6D:
                        cycles = testRegisterBit(registerHL, Register.RegByte.LO, 5);
                        break;
                    case 0x6E:
                        cycles = testRegisterBit(registerHL, Register.RegByte.WORD, 5);
                        break;
                    case 0x6F:
                        cycles = testRegisterBit(registerAF, Register.RegByte.HI, 5);
                        break;

                    // bit 6
                    case 0x70:
                        cycles = testRegisterBit(registerBC, Register.RegByte.HI, 6);
                        break;
                    case 0x71:
                        cycles = testRegisterBit(registerBC, Register.RegByte.LO, 6);
                        break;
                    case 0x72:
                        cycles = testRegisterBit(registerDE, Register.RegByte.HI, 6);
                        break;
                    case 0x73:
                        cycles = testRegisterBit(registerDE, Register.RegByte.LO, 6);
                        break;
                    case 0x74:
                        cycles = testRegisterBit(registerHL, Register.RegByte.HI, 6);
                        break;
                    case 0x75:
                        cycles = testRegisterBit(registerHL, Register.RegByte.LO, 6);
                        break;
                    case 0x76:
                        cycles = testRegisterBit(registerHL, Register.RegByte.WORD, 6);
                        break;
                    case 0x77:
                        cycles = testRegisterBit(registerAF, Register.RegByte.HI, 6);
                        break;

                    // bit 7
                    case 0x78:
                        cycles = testRegisterBit(registerBC, Register.RegByte.HI, 7);
                        break;
                    case 0x79:
                        cycles = testRegisterBit(registerBC, Register.RegByte.LO, 7);
                        break;
                    case 0x7A:
                        cycles = testRegisterBit(registerDE, Register.RegByte.HI, 7);
                        break;
                    case 0x7B:
                        cycles = testRegisterBit(registerDE, Register.RegByte.LO, 7);
                        break;
                    case 0x7C:
                        cycles = testRegisterBit(registerHL, Register.RegByte.HI, 7);
                        break;
                    case 0x7D:
                        cycles = testRegisterBit(registerHL, Register.RegByte.LO, 7);
                        break;
                    case 0x7E:
                        cycles = testRegisterBit(registerHL, Register.RegByte.WORD, 7);
                        break;
                    case 0x7F:
                        cycles = testRegisterBit(registerAF, Register.RegByte.HI, 7);
                        break;


                    // SET b, r
                    // c0 -> ff
                    case 0xC0:
                        cycles = setBit(registerBC, Register.RegByte.HI, 0);
                        break;
                    case 0xC1:
                        cycles = setBit(registerBC, Register.RegByte.LO, 0);
                        break;
                    case 0xC2:
                        cycles = setBit(registerDE, Register.RegByte.HI, 0);
                        break;
                    case 0xC3:
                        cycles = setBit(registerDE, Register.RegByte.LO, 0);
                        break;
                    case 0xC4:
                        cycles = setBit(registerHL, Register.RegByte.HI, 0);
                        break;
                    case 0xC5:
                        cycles = setBit(registerHL, Register.RegByte.LO, 0);
                        break;
                    case 0xC6:
                        cycles = setBit(registerHL, Register.RegByte.WORD, 0);
                        break;
                    case 0xC7:
                        cycles = setBit(registerAF, Register.RegByte.HI, 0);
                        break;
                    case 0xC8:
                        cycles = setBit(registerBC, Register.RegByte.HI, 1);
                        break;
                    case 0xC9:
                        cycles = setBit(registerBC, Register.RegByte.LO, 1);
                        break;
                    case 0xCA:
                        cycles = setBit(registerDE, Register.RegByte.HI, 1);
                        break;
                    case 0xCB:
                        cycles = setBit(registerDE, Register.RegByte.LO, 1);
                        break;
                    case 0xCC:
                        cycles = setBit(registerHL, Register.RegByte.HI, 1);
                        break;
                    case 0xCD:
                        cycles = setBit(registerHL, Register.RegByte.LO, 1);
                        break;
                    case 0xCE:
                        cycles = setBit(registerHL, Register.RegByte.WORD, 1);
                        break;
                    case 0xCF:
                        cycles = setBit(registerAF, Register.RegByte.HI, 1);
                        break;
                    case 0xD0:
                        cycles = setBit(registerBC, Register.RegByte.HI, 2);
                        break;
                    case 0xD1:
                        cycles = setBit(registerBC, Register.RegByte.LO, 2);
                        break;
                    case 0xD2:
                        cycles = setBit(registerDE, Register.RegByte.HI, 2);
                        break;
                    case 0xD3:
                        cycles = setBit(registerDE, Register.RegByte.LO, 2);
                        break;
                    case 0xD4:
                        cycles = setBit(registerHL, Register.RegByte.HI, 2);
                        break;
                    case 0xD5:
                        cycles = setBit(registerHL, Register.RegByte.LO, 2);
                        break;
                    case 0xD6:
                        cycles = setBit(registerHL, Register.RegByte.WORD, 2);
                        break;
                    case 0xD7:
                        cycles = setBit(registerAF, Register.RegByte.HI, 2);
                        break;
                    case 0xD8:
                        cycles = setBit(registerBC, Register.RegByte.HI, 3);
                        break;
                    case 0xD9:
                        cycles = setBit(registerBC, Register.RegByte.LO, 3);
                        break;
                    case 0xDA:
                        cycles = setBit(registerDE, Register.RegByte.HI, 3);
                        break;
                    case 0xDB:
                        cycles = setBit(registerDE, Register.RegByte.LO, 3);
                        break;
                    case 0xDC:
                        cycles = setBit(registerHL, Register.RegByte.HI, 3);
                        break;
                    case 0xDD:
                        cycles = setBit(registerHL, Register.RegByte.LO, 3);
                        break;
                    case 0xDE:
                        cycles = setBit(registerHL, Register.RegByte.WORD, 3);
                        break;
                    case 0xDF:
                        cycles = setBit(registerAF, Register.RegByte.HI, 3);
                        break;
                    case 0xE0:
                        cycles = setBit(registerBC, Register.RegByte.HI, 4);
                        break;
                    case 0xE1:
                        cycles = setBit(registerBC, Register.RegByte.LO, 4);
                        break;
                    case 0xE2:
                        cycles = setBit(registerDE, Register.RegByte.HI, 4);
                        break;
                    case 0xE3:
                        cycles = setBit(registerDE, Register.RegByte.LO, 4);
                        break;
                    case 0xE4:
                        cycles = setBit(registerHL, Register.RegByte.HI, 4);
                        break;
                    case 0xE5:
                        cycles = setBit(registerHL, Register.RegByte.LO, 4);
                        break;
                    case 0xE6:
                        cycles = setBit(registerHL, Register.RegByte.WORD, 4);
                        break;
                    case 0xE7:
                        cycles = setBit(registerAF, Register.RegByte.HI, 4);
                        break;
                    case 0xE8:
                        cycles = setBit(registerBC, Register.RegByte.HI, 5);
                        break;
                    case 0xE9:
                        cycles = setBit(registerBC, Register.RegByte.LO, 5);
                        break;
                    case 0xEA:
                        cycles = setBit(registerDE, Register.RegByte.HI, 5);
                        break;
                    case 0xEB:
                        cycles = setBit(registerDE, Register.RegByte.LO, 5);
                        break;
                    case 0xEC:
                        cycles = setBit(registerHL, Register.RegByte.HI, 5);
                        break;
                    case 0xED:
                        cycles = setBit(registerHL, Register.RegByte.LO, 5);
                        break;
                    case 0xEE:
                        cycles = setBit(registerHL, Register.RegByte.WORD, 5);
                        break;
                    case 0xEF:
                        cycles = setBit(registerAF, Register.RegByte.HI, 5);
                        break;
                    case 0xF0:
                        cycles = setBit(registerBC, Register.RegByte.HI, 6);
                        break;
                    case 0xF1:
                        cycles = setBit(registerBC, Register.RegByte.LO, 6);
                        break;
                    case 0xF2:
                        cycles = setBit(registerDE, Register.RegByte.HI, 6);
                        break;
                    case 0xF3:
                        cycles = setBit(registerDE, Register.RegByte.LO, 6);
                        break;
                    case 0xF4:
                        cycles = setBit(registerHL, Register.RegByte.HI, 6);
                        break;
                    case 0xF5:
                        cycles = setBit(registerHL, Register.RegByte.LO, 6);
                        break;
                    case 0xF6:
                        cycles = setBit(registerHL, Register.RegByte.WORD, 6);
                        break;
                    case 0xF7:
                        cycles = setBit(registerAF, Register.RegByte.HI, 6);
                        break;
                    case 0xF8:
                        cycles = setBit(registerBC, Register.RegByte.HI, 7);
                        break;
                    case 0xF9:
                        cycles = setBit(registerBC, Register.RegByte.LO, 7);
                        break;
                    case 0xFA:
                        cycles = setBit(registerDE, Register.RegByte.HI, 7);
                        break;
                    case 0xFB:
                        cycles = setBit(registerDE, Register.RegByte.LO, 7);
                        break;
                    case 0xFC:
                        cycles = setBit(registerHL, Register.RegByte.HI, 7);
                        break;
                    case 0xFD:
                        cycles = setBit(registerHL, Register.RegByte.LO, 7);
                        break;
                    case 0xFE:
                        cycles = setBit(registerHL, Register.RegByte.WORD, 7);
                        break;
                    case 0xFF:
                        cycles = setBit(registerAF, Register.RegByte.HI, 7);
                        break;

                    // RES b,r
                    // page 110
// 0x80 -> 0xbf
                    case 0x80:
                        cycles = resetBit(registerBC, Register.RegByte.HI, 0);
                        break;
                    case 0x81:
                        cycles = resetBit(registerBC, Register.RegByte.LO, 0);
                        break;
                    case 0x82:
                        cycles = resetBit(registerDE, Register.RegByte.HI, 0);
                        break;
                    case 0x83:
                        cycles = resetBit(registerDE, Register.RegByte.LO, 0);
                        break;
                    case 0x84:
                        cycles = resetBit(registerHL, Register.RegByte.HI, 0);
                        break;
                    case 0x85:
                        cycles = resetBit(registerHL, Register.RegByte.LO, 0);
                        break;
                    case 0x86:
                        cycles = resetBit(registerHL, Register.RegByte.WORD, 0);
                        break;
                    case 0x87:
                        cycles = resetBit(registerAF, Register.RegByte.HI, 0);
                        break;
                    case 0x88:
                        cycles = resetBit(registerBC, Register.RegByte.HI, 1);
                        break;
                    case 0x89:
                        cycles = resetBit(registerBC, Register.RegByte.LO, 1);
                        break;
                    case 0x8A:
                        cycles = resetBit(registerDE, Register.RegByte.HI, 1);
                        break;
                    case 0x8B:
                        cycles = resetBit(registerDE, Register.RegByte.LO, 1);
                        break;
                    case 0x8C:
                        cycles = resetBit(registerHL, Register.RegByte.HI, 1);
                        break;
                    case 0x8D:
                        cycles = resetBit(registerHL, Register.RegByte.LO, 1);
                        break;
                    case 0x8E:
                        cycles = resetBit(registerHL, Register.RegByte.WORD, 1);
                        break;
                    case 0x8F:
                        cycles = resetBit(registerAF, Register.RegByte.HI, 1);
                        break;
                    case 0x90:
                        cycles = resetBit(registerBC, Register.RegByte.HI, 2);
                        break;
                    case 0x91:
                        cycles = resetBit(registerBC, Register.RegByte.LO, 2);
                        break;
                    case 0x92:
                        cycles = resetBit(registerDE, Register.RegByte.HI, 2);
                        break;
                    case 0x93:
                        cycles = resetBit(registerDE, Register.RegByte.LO, 2);
                        break;
                    case 0x94:
                        cycles = resetBit(registerHL, Register.RegByte.HI, 2);
                        break;
                    case 0x95:
                        cycles = resetBit(registerHL, Register.RegByte.LO, 2);
                        break;
                    case 0x96:
                        cycles = resetBit(registerHL, Register.RegByte.WORD, 2);
                        break;
                    case 0x97:
                        cycles = resetBit(registerAF, Register.RegByte.HI, 2);
                        break;
                    case 0x98:
                        cycles = resetBit(registerBC, Register.RegByte.HI, 3);
                        break;
                    case 0x99:
                        cycles = resetBit(registerBC, Register.RegByte.LO, 3);
                        break;
                    case 0x9A:
                        cycles = resetBit(registerDE, Register.RegByte.HI, 3);
                        break;
                    case 0x9B:
                        cycles = resetBit(registerDE, Register.RegByte.LO, 3);
                        break;
                    case 0x9C:
                        cycles = resetBit(registerHL, Register.RegByte.HI, 3);
                        break;
                    case 0x9D:
                        cycles = resetBit(registerHL, Register.RegByte.LO, 3);
                        break;
                    case 0x9E:
                        cycles = resetBit(registerHL, Register.RegByte.WORD, 3);
                        break;
                    case 0x9F:
                        cycles = resetBit(registerAF, Register.RegByte.HI, 3);
                        break;
                    case 0xA0:
                        cycles = resetBit(registerBC, Register.RegByte.HI, 4);
                        break;
                    case 0xA1:
                        cycles = resetBit(registerBC, Register.RegByte.LO, 4);
                        break;
                    case 0xA2:
                        cycles = resetBit(registerDE, Register.RegByte.HI, 4);
                        break;
                    case 0xA3:
                        cycles = resetBit(registerDE, Register.RegByte.LO, 4);
                        break;
                    case 0xA4:
                        cycles = resetBit(registerHL, Register.RegByte.HI, 4);
                        break;
                    case 0xA5:
                        cycles = resetBit(registerHL, Register.RegByte.LO, 4);
                        break;
                    case 0xA6:
                        cycles = resetBit(registerHL, Register.RegByte.WORD, 4);
                        break;
                    case 0xA7:
                        cycles = resetBit(registerAF, Register.RegByte.HI, 4);
                        break;
                    case 0xA8:
                        cycles = resetBit(registerBC, Register.RegByte.HI, 5);
                        break;
                    case 0xA9:
                        cycles = resetBit(registerBC, Register.RegByte.LO, 5);
                        break;
                    case 0xAA:
                        cycles = resetBit(registerDE, Register.RegByte.HI, 5);
                        break;
                    case 0xAB:
                        cycles = resetBit(registerDE, Register.RegByte.LO, 5);
                        break;
                    case 0xAC:
                        cycles = resetBit(registerHL, Register.RegByte.HI, 5);
                        break;
                    case 0xAD:
                        cycles = resetBit(registerHL, Register.RegByte.LO, 5);
                        break;
                    case 0xAE:
                        cycles = resetBit(registerHL, Register.RegByte.WORD, 5);
                        break;
                    case 0xAF:
                        cycles = resetBit(registerAF, Register.RegByte.HI, 5);
                        break;
                    case 0xB0:
                        cycles = resetBit(registerBC, Register.RegByte.HI, 6);
                        break;
                    case 0xB1:
                        cycles = resetBit(registerBC, Register.RegByte.LO, 6);
                        break;
                    case 0xB2:
                        cycles = resetBit(registerDE, Register.RegByte.HI, 6);
                        break;
                    case 0xB3:
                        cycles = resetBit(registerDE, Register.RegByte.LO, 6);
                        break;
                    case 0xB4:
                        cycles = resetBit(registerHL, Register.RegByte.HI, 6);
                        break;
                    case 0xB5:
                        cycles = resetBit(registerHL, Register.RegByte.LO, 6);
                        break;
                    case 0xB6:
                        cycles = resetBit(registerHL, Register.RegByte.WORD, 6);
                        break;
                    case 0xB7:
                        cycles = resetBit(registerAF, Register.RegByte.HI, 6);
                        break;
                    case 0xB8:
                        cycles = resetBit(registerBC, Register.RegByte.HI, 7);
                        break;
                    case 0xB9:
                        cycles = resetBit(registerBC, Register.RegByte.LO, 7);
                        break;
                    case 0xBA:
                        cycles = resetBit(registerDE, Register.RegByte.HI, 7);
                        break;
                    case 0xBB:
                        cycles = resetBit(registerDE, Register.RegByte.LO, 7);
                        break;
                    case 0xBC:
                        cycles = resetBit(registerHL, Register.RegByte.HI, 7);
                        break;
                    case 0xBD:
                        cycles = resetBit(registerHL, Register.RegByte.LO, 7);
                        break;
                    case 0xBE:
                        cycles = resetBit(registerHL, Register.RegByte.WORD, 7);
                        break;
                    case 0xBF:
                        cycles = resetBit(registerAF, Register.RegByte.HI, 7);
                        break;


                    default: {
                        System.out.printf(" :: opcode missing 0x%x :: ", secondOpcode);
                        break;
                    }
                }
            }


            default: {
                break;
            }

        }


        this.programCounter++;

        if (cycles == 0) {
            System.out.printf("Opcode 0x%x not implemented\n", opcode);
        }

        return cycles;
    }

    private int push(Register register) {
        int valueByte = register.getReg();
        int address = stackPointer - 1;
        memory.writeByteToLocation(address, valueByte >> 8);
        address--;
        memory.writeByteToLocation(address, valueByte & 0xFF);
        stackPointer = address;
        return 16;
    }

    private int pop(Register register) {
        int value = (memory.readByteFromLocation(stackPointer + 1) << 8) | memory.readByteFromLocation(stackPointer);
        stackPointer = stackPointer + 2;
        register.setReg(value);
        return 12;
    }

    // Based off of buxxi implementation. thanks <3
    // https://github.com/buxxi/gameboy-emu
    private static class FlagsImpl implements Flags {
        public boolean isSet(Flag flag) {
            return (registerAF.getLo() & flag.mask()) != 0;
        }

        public void set(Flag flag, boolean value) {
            int f = registerAF.getLo();
            if (value) {
                f = f | flag.mask();
            } else {
                f = f & (~flag.mask());
            }
            registerAF.setLo(f);
        }
    }
}
