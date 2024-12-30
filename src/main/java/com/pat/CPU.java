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

    private int rotate(RotateDirection direction, Register register, Register.RegByte regByte, boolean includeCarry) {
        if (regByte == Register.RegByte.WORD) {
            System.out.println("Operation not supported rotateRight WORD");
        }

        int reg = regByte == Register.RegByte.HI ? register.getHi() : register.getLo();
        boolean oldBit = Helpful.getBit(reg, direction == RotateDirection.RIGHT ? 0 : 7);

        int result = direction == RotateDirection.RIGHT ? reg >> 1 : reg << 1;

        if (includeCarry) {
            boolean carry = flags.isSet(Flags.Flag.CARRY);
            result = Helpful.setBit(result, direction == RotateDirection.RIGHT  ? 7 : 0, carry);
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

        return 4;
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

//        System.out.printf("Instruction for PC %x is 0x%x\n", programCounter, opcode);

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
                int regA = registerAF.getHi();

                boolean oldBit7 = Helpful.getBit(regA, 0);
                boolean carry = flags.isSet(Flags.Flag.CARRY);

                int result = regA << 1;

                // Only difference between 0x07 and 0x17 is setting this bit
                result = Helpful.setBit(result, 0, carry);

                flags.set(Flags.Flag.CARRY, oldBit7);
                flags.set(Flags.Flag.ZERO, result == 0);
                flags.set(Flags.Flag.HALF_CARRY, false);
                flags.set(Flags.Flag.SUBTRACT, false);

                registerAF.setHi(result);
                cycles = 4;
                break;
            }

            case 0x17: {
                // RLA
                // page 99
                int regA = registerAF.getHi();

                boolean oldBit7 = Helpful.getBit(regA, 7);


                int result = regA << 1;

                flags.set(Flags.Flag.CARRY, oldBit7);
                flags.set(Flags.Flag.ZERO, result == 0);
                flags.set(Flags.Flag.HALF_CARRY, false);
                flags.set(Flags.Flag.SUBTRACT, false);

                registerAF.setHi(result);
                cycles = 4;
                break;
            }

            case 0x0F: {
                // RRCA
                // page 100

                cycles = rotate(RotateDirection.RIGHT,registerAF, Register.RegByte.HI, true);

                break;
            }

            case 0x1F: {
                // RRA
                // page 100
                cycles = rotate(RotateDirection.RIGHT,registerAF, Register.RegByte.HI, false);
                break;
            }

            // Jumps

            case 0xC3: {
                // JP nn
                // page 111
                break;
            }

            case 0xC2:
            case 0xCA:
            case 0xD2:
            case 0xDA: {
                // JP cc,nn
                // page 111
                break;
            }

            case 0xE9: {
                // JP (HL)
                // page 112
                break;
            }

            case 0x18: {
                // JR n
                // page 112
                break;
            }

            case 0x20:
            case 0x28:
            case 0x30:
            case 0x38: {
                // JR cc,n
                // page 113
                break;
            }

            // Calls
            case 0xCD: {
                // CALL nn
                // page 114
                break;
            }

            case 0xC4:
            case 0xCC:
            case 0xD4:
            case 0xDC: {
                // CALL cc,nn
                // page 115
                break;
            }

            // Restarts
            case 0xC7:
            case 0xCF:
            case 0xD7:
            case 0xDF:
            case 0xE7:
            case 0xEF:
            case 0xF7:
            case 0xFF: {
                // RST n
                // page 116
                break;
            }

            // Returns

            case 0xC9: {
                // RET
                // page 117
                break;
            }

            case 0xC0:
            case 0xC8:
            case 0xD0:
            case 0xD8: {
                // RET cc
                // page 117
                break;
            }

            case 0xD9: {
                // RETI
                // page 118
                break;
            }


            case 0x10: {
                int secondOpcode = this.memory.generalMemory[programCounter + 1];

                if (secondOpcode == 0x00) {
                    // STOP
                    // page 97
                    cycles = 4;
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

                    case 0x07:
                    case 0x00:
                    case 0x01:
                    case 0x02:
                    case 0x03:
                    case 0x04:
                    case 0x05:
                    case 0x06: {
                        // RLC n
                        // page 101
                        break;
                    }

                    case 0x17:
                    case 0x10:
                    case 0x11:
                    case 0x12:
                    case 0x13:
                    case 0x14:
                    case 0x15:
                    case 0x16: {
                        // RL n
                        // page 102
                        break;
                    }

                    case 0x0F:
                    case 0x08:
                    case 0x09:
                    case 0x0A:
                    case 0x0B:
                    case 0x0C:
                    case 0x0D:
                    case 0x0E: {
                        // RRC n
                        // page 103
                        break;
                    }

                    case 0x1F:
                    case 0x18:
                    case 0x19:
                    case 0x1A:
                    case 0x1B:
                    case 0x1C:
                    case 0x1D:
                    case 0x1E: {
                        // RR n
                        // page 104
                        break;
                    }

                    case 0x27:
                    case 0x20:
                    case 0x21:
                    case 0x22:
                    case 0x23:
                    case 0x24:
                    case 0x25:
                    case 0x26: {
                        // SLA n
                        // page 105
                        break;
                    }

                    case 0x2F:
                    case 0x28:
                    case 0x29:
                    case 0x2A:
                    case 0x2B:
                    case 0x2C:
                    case 0x2D:
                    case 0x2E: {
                        // SRA n
                        // page 106
                        break;
                    }

                    case 0x3F:
                    case 0x38:
                    case 0x39:
                    case 0x3A:
                    case 0x3B:
                    case 0x3C:
                    case 0x3D:
                    case 0x3E: {
                        // SRL n
                        // page 107
                        break;
                    }

                    // Bit Opcodes

                    case 0x47:
                    case 0x40:
                    case 0x41:
                    case 0x42:
                    case 0x43:
                    case 0x44:
                    case 0x45:
                    case 0x46: {
                        // BIT b,r
                        // page 108
                        break;
                    }

                    case 0xC7:
                    case 0xC0:
                    case 0xC1:
                    case 0xC2:
                    case 0xC3:
                    case 0xC4:
                    case 0xC5:
                    case 0xC6: {
                        // SET b,r
                        // page 109
                        break;
                    }

                    case 0x87:
                    case 0x80:
                    case 0x81:
                    case 0x82:
                    case 0x83:
                    case 0x84:
                    case 0x85:
                    case 0x86: {
                        // RES b,r
                        // page 110
                        break;
                    }


                    default: {
                        break;
                    }
                }
            }


            default: {
                break;
            }

        }


        this.programCounter++;

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
