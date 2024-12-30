package com.pat;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.Files;

public class Memory {

    private static final int maxMemorySize = 0xFFFF + 1;
    // just storing the whole cartridge here
    public int[] cartMemory;
    // the whole memory map.
    public int[] generalMemory = new int[maxMemorySize];


    public Memory() {
        generalMemory[0xFF05] = 0x00;
        generalMemory[0xFF06] = 0x00;
        generalMemory[0xFF07] = 0x00;
        generalMemory[0xFF10] = 0x80;
        generalMemory[0xFF11] = 0xBF;
        generalMemory[0xFF12] = 0xF3;
        generalMemory[0xFF14] = 0xBF;
        generalMemory[0xFF16] = 0x3F;
        generalMemory[0xFF17] = 0x00;
        generalMemory[0xFF19] = 0xBF;
        generalMemory[0xFF1A] = 0x7F;
        generalMemory[0xFF1B] = 0xFF;
        generalMemory[0xFF1C] = 0x9F;
        generalMemory[0xFF1E] = 0xBF;
        generalMemory[0xFF20] = 0xFF;
        generalMemory[0xFF21] = 0x00;
        generalMemory[0xFF22] = 0x00;
        generalMemory[0xFF23] = 0xBF;
        generalMemory[0xFF24] = 0x77;
        generalMemory[0xFF25] = 0xF3;
        generalMemory[0xFF26] = 0xF1;
        generalMemory[0xFF40] = 0x91;
        generalMemory[0xFF42] = 0x00;
        generalMemory[0xFF43] = 0x00;
        generalMemory[0xFF45] = 0x00;
        generalMemory[0xFF47] = 0xFC;
        generalMemory[0xFF48] = 0xFF;
        generalMemory[0xFF49] = 0xFF;
        generalMemory[0xFF4A] = 0x00;
        generalMemory[0xFF4B] = 0x00;
        generalMemory[0xFFFF] = 0x00;
    }


    /**
     *
     * Use this to load the cpu_instrs rom directly. Blaarg rom.
     *
     * */
    public void loadTestRom() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("cpu_instrs.gb").getFile());
        InputStream is = Files.newInputStream(file.toPath());

        byte[] bytes = IOUtils.toByteArray(is, file.length());
        int[] intArray = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            intArray[i] = bytes[i] & 0xFF;
        }

        // get some of the stats from the rom itself



        this.cartMemory = intArray;

        // Write the rom between 0x0000 and 0x8000.
        // 0x0000 -> 0x4000 is ROM bank #0
        // 0x4000 -> 0x8000 is ROM bank #n, for multibank etc.
        // Test rom, tetris, is 32kB so no bank switching required
        for (int i = 0; i < this.cartMemory.length && i < 0x8000; i++) {
            writeByteToLocation(this.cartMemory[i], i);
        }

    }

    /**
     *
     * Use this mostly to test little sample roms. Not safe enough for an actual ROM
     *
     * */
    public void loadTestRomByteArray(int[] romMemory) throws IOException {
        int[] intArray = new int[romMemory.length];
        for (int i = 0; i < romMemory.length; i++) {
            intArray[i] = romMemory[i] & 0xFF;
        }

        this.cartMemory = intArray;

        // Write the rom between 0x0000 and 0x8000.
        // 0x0000 -> 0x4000 is ROM bank #0
        // 0x4000 -> 0x8000 is ROM bank #n, for multibank etc.
        // Test rom, tetris, is 32kB so no bank switching required
        for (int i = 0; i < this.cartMemory.length && i < 0x8000; i++) {
            writeByteToLocation(this.cartMemory[i], i);
        }
    }



    // expand this to also echo if needed, etc
    public void writeByteToLocation(int a, int location) {
        if (location >= maxMemorySize) {
            // TODO: fix this later. Right now if it tries to access memory outside of memory space, return 0x00
            System.out.printf("Attempted writing out of bounds %x\n", location);
            return;
        }

        this.generalMemory[location] = a;
    }

    public void writeWordToLocation(int word, int location) {
        writeByteToLocation(word & 0xFF, location); // & 0xFF gets the high 8 bit
        writeByteToLocation(word >> 8, location + 1); // >> 8 gets the low 8 bit
    }

    public int readByteFromLocation(int location) {
        if (location >= maxMemorySize || location < 0) {
            // TODO: fix this later. Right now if it tries to access memory outside of memory space, return 0x00
            System.out.printf("Attempted reading out of bounds %x\n", location);
            return 0x00;
        }
        return this.generalMemory[location];
    }

    public int readWordFromLocation(int location) {
        return readByteFromLocation(location) + ((readByteFromLocation(location + 1) << 8)); // return 2 bytes, one after the other
    }
}
