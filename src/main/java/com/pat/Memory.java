package com.pat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Memory {

    // just storing the whole cartridge here
    public byte[] cartMemory;
    // the whole memory map.
    public int[] generalMemory = new int[0x10000];

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


    public void loadTestRom() {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("Tetris.gb").getFile());
        this.cartMemory = new byte[(int) file.length()];

        try(FileInputStream fis = new FileInputStream(file)) {
            fis.read(this.cartMemory);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Write the rom between 0x0000 and 0x8000.
        // 0x0000 -> 0x4000 is ROM bank #0
        // 0x4000 -> 0x8000 is ROM bank #n, for multibank etc.
        // Test rom, tetris, is 32kB so no bank switching required
        for (int i = 0; i < this.cartMemory.length && i < 0x8000; i++) {
            writeByteToLocation(this.cartMemory[i], i);
        }

    }

    // expand this to also echo if needed, etc
    private void writeByteToLocation(int a, int location) {
        this.generalMemory[location] = a;
    }
}
