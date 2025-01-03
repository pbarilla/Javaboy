import com.pat.CPU;
import com.pat.Memory;
import com.pat.Register;
import com.pat.instructions.LSM;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class CPUTests {

    @Test
    public void testRegister_setRegister_thenTestNibbles() {
        Register register = new Register(0xFA00);

        Assert.assertEquals(0xFA, register.getHi());
        Assert.assertEquals(0x00, register.getLo());
    }

    @Test
    public void testRegister_setRegister_thenSetNibbles_thenTestRegister() {
        Register register = new Register((short) 0xFABC);
        Assert.assertEquals(0xFA, register.getHi());
        Assert.assertEquals(0xBC, register.getLo());

        register.setHi((char) 0x0B);
        Assert.assertEquals(0x0B, register.getHi());
        Assert.assertEquals(0xBC, register.getLo());
        Assert.assertEquals(0x0BBC, register.getReg());

        register.setLo((char) 0xC0);
        Assert.assertEquals(0x0B, register.getHi());
        Assert.assertEquals(0xC0, register.getLo());
        Assert.assertEquals(0x0BC0, register.getReg());
    }

    @Test
    public void testCPU() throws IOException {
        Memory memory = new Memory();
        memory.loadTestRom();

        CPU cpu = new CPU(memory);

        boolean isRunning = true;

        int testRunningIterations = 10; // run for around 10 million operations (should take 0.5 - 1 seconds)

        while (isRunning) {
            int cycles = cpu.fetchDecodeExecute();

            testRunningIterations--;
            if (testRunningIterations <= 0) {
                isRunning = false;
            }

        }

        System.out.println("Probably finished running?");


    }

    @Test
    public void testLoads() throws IOException {
        Memory memory = new Memory();
        memory.loadTestRom();

        // Test memory was loaded right. 0x00 will be 60
        Assert.assertEquals(60, memory.generalMemory[0x00]);

        int cycles;

        // test loading into registers

        Register register = new Register((short) 0xFA00);
        // Test 0xFA hi is 0xF
        Assert.assertEquals(0xFA, register.getHi());
        cycles = LSM.loadValueIntoRegister(0x0A, register, Register.RegByte.HI);
        Assert.assertEquals(4, cycles);
        Assert.assertEquals(0x0A, register.getHi());

        // test loading into lo
        cycles = LSM.loadValueIntoRegister(0xCC, register, Register.RegByte.LO);
        Assert.assertEquals(4, cycles);
        Assert.assertEquals(0xCC, register.getLo());

        // test loading a word

        cycles = LSM.loadValueIntoRegister(0xBDEF, register, Register.RegByte.WORD);
        Assert.assertEquals(8, cycles);
        Assert.assertEquals(0xBD, register.getHi());
        Assert.assertEquals(0xEF, register.getLo());
        Assert.assertEquals(0xBDEF, register.getReg());

    }

    @Test
    public void test8BitLoads() throws IOException {
        Memory memory = new Memory();

        int[] testRom = new int[0xFF];
        testRom[0x00] = 0x00;
        testRom[0x01] = 0x06;
        testRom[0x02] = 0xBB;
        testRom[0x03] = 0x0E;
        testRom[0x04] = 0xCC;
        testRom[0x05] = 0x16;
        testRom[0x06] = 0xDD;
        testRom[0x07] = 0x1E;
        testRom[0x08] = 0xEE;
        testRom[0x09] = 0x26;
        testRom[0x0A] = 0x11;
        testRom[0x0B] = 0x26;

        memory.loadTestRomByteArray(testRom);

        CPU cpu = new CPU(memory);

        // test initialisation
        cpu.forceProgramCounterToPosition(0x00, true);

        // force memory to be something NOT 0x00
        memory.writeByteToLocation(0xFF, 0x80BB);

        // test memory was set
        Assert.assertEquals(0xFF, memory.generalMemory[0x80BB]);

        // run 0x06, load reg B into memory location at PC
        cpu.forceProgramCounterToPosition(0x01, true);

        // test that the load worked, and set it back to reg b (which is 0x00)
        Assert.assertEquals(0x00, memory.generalMemory[0xBB]);
    }


    @Test
    public void testLoadIntoA() throws IOException {
        Memory memory = new Memory();

        int[] testRom = new int[0xFF];
        testRom[0x00] = 0x00;
        testRom[0x01] = 0xFA;
        testRom[0x02] = 0xAA;

        memory.loadTestRomByteArray(testRom);

        CPU cpu = new CPU(memory);

        Register.RegisterHash sampledRegisters = cpu.sampleRegisters();

        cpu.forceProgramCounterToPosition(0x01, true);

        Assert.assertEquals(0xAA, sampledRegisters.AF.getHi());
    }

    @Test
    public void testRotates() throws IOException {
        Memory memory = new Memory();
        int[] testRom = new int[0xFF];
        testRom[0x00] = 0x00;
        testRom[0x01] = 0xFA; // set reg A to 0xAA
        testRom[0x02] = 0xAA;
        testRom[0x03] = 0x07; // rotate A left, with carry unset. regA will be 0x54
        testRom[0x04] = 0x37; // set the carry
        testRom[0x05] = 0xFA; // set reg A back to 0xAA
        testRom[0x06] = 0xAA;
        testRom[0x07] = 0x07; // rotate A left, with carry set. regA will be 0x55
        testRom[0x08] = 0x17;
        testRom[0x09] = 0x0F;
        testRom[0x0A] = 0x1F;

        memory.loadTestRomByteArray(testRom);

        CPU cpu = new CPU(memory);

        Register.RegisterHash sampledRegisters = cpu.sampleRegisters();
        cpu.forceProgramCounterToPosition(0x01, true);

        // ensure reg A is 0xAA
        Assert.assertEquals(0xAA, sampledRegisters.AF.getHi());

        // execute RLCA
        cpu.forceProgramCounterToPosition(0x03, true);
        // did the rotate work?
        Assert.assertEquals(0x54, sampledRegisters.AF.getHi());

        // set carry flag
        cpu.forceProgramCounterToPosition(0x04, true);

        // set reg a back to 0xAA
        cpu.forceProgramCounterToPosition(0x05, true);
        // ensure reg A is 0xAA
        Assert.assertEquals(0xAA, sampledRegisters.AF.getHi());

        // test RLCA with carry set
        cpu.forceProgramCounterToPosition(0x07, true);
        Assert.assertEquals(0x55, sampledRegisters.AF.getHi());

        // set reg a back to 0xAA
        cpu.forceProgramCounterToPosition(0x05, true);
        // ensure reg A is 0xAA
        Assert.assertEquals(0xAA, sampledRegisters.AF.getHi());

        // test RLA
        cpu.forceProgramCounterToPosition(0x08, true);
        Assert.assertEquals(0x54, sampledRegisters.AF.getHi());

        // set reg a back to 0xAA
        cpu.forceProgramCounterToPosition(0x05, true);
        // ensure reg A is 0xAA
        Assert.assertEquals(0xAA, sampledRegisters.AF.getHi());

        // RRCA
        cpu.forceProgramCounterToPosition(0x09, true);
        // at this point the carry bit is set, so RRCA will be 0xd5
        Assert.assertEquals(0xd5, sampledRegisters.AF.getHi());

        // set reg a back to 0xAA
        cpu.forceProgramCounterToPosition(0x05, true);
        // ensure reg A is 0xAA
        Assert.assertEquals(0xAA, sampledRegisters.AF.getHi());

        // RRA
        cpu.forceProgramCounterToPosition(0x0A, true);
        // at this point the carry bit is set, so RRCA will be 0xd5
        Assert.assertEquals(0x55, sampledRegisters.AF.getHi());
    }

    @Test
    public void testShifts() throws IOException {
        Memory memory = new Memory();
        int[] testRom = new int[0xFF];

        testRom[0x00] = 0x00;
        // SLA
        testRom[0x01] = 0xCB; // get into the 0xCBxx opcodes
        testRom[0x02] = 0x27; // SLA A - shift A left into carry. LSB of A set to 0.
        testRom[0x03] = 0xCB;
        testRom[0x04] = 0x20; // SLA B
        testRom[0x05] = 0xCB;
        testRom[0x06] = 0x21; // SLA C
        testRom[0x07] = 0xCB;
        testRom[0x08] = 0x26; // SLA (HL)
        // SRA
        testRom[0x09] = 0xCB;
        testRom[0x0A] = 0x2F; // SRA A
        testRom[0x0B] = 0xCB;
        testRom[0x0C] = 0x28; // SRA B
        testRom[0x0D] = 0xCB;
        testRom[0x0E] = 0x2E; // SRA (HL)
        // SRL
        testRom[0x0F] = 0xCB;
        testRom[0x10] = 0x3F; // SRL A
        testRom[0x11] = 0xCB;
        testRom[0x12] = 0x28; // SRL B
        testRom[0x13] = 0xCB;
        testRom[0x14] = 0x3E; // SRL (HL)

        memory.loadTestRomByteArray(testRom);
        CPU cpu = new CPU(memory);

        Register.RegisterHash sampledRegisters = cpu.sampleRegisters();

        // reset A, B, C to something not 0x00
        sampledRegisters.AF.setHi(0xAA);
        sampledRegisters.BC.setHi(0xBB);
        sampledRegisters.BC.setLo(0xCC);

        // reset HL to something not 0x0000
        sampledRegisters.HL.setReg(0xABCD);

        // Ensure resetting registers worked
        Assert.assertEquals(0xAA, sampledRegisters.AF.getHi());

        // Perform NOP
        cpu.forceProgramCounterToPosition(0x00, true);

        // perform 0xCB27 (SLA A)
        cpu.forceProgramCounterToPosition(0x01, true);
        // Ensure A isn't 0xAA (it shouldn't be that)
        Assert.assertNotEquals(0xAA, sampledRegisters.AF.getHi());
        // Bitshift should be 0x54
        Assert.assertEquals(0x54, sampledRegisters.AF.getHi());

        // 0xCB20
        cpu.forceProgramCounterToPosition(0x03, true);
        // Ensure B isn't 0xBB (it shouldn't be that)
        Assert.assertNotEquals(0xBB, sampledRegisters.BC.getHi());
        // Bitshift should be 0x76
        Assert.assertEquals(0x76, sampledRegisters.BC.getHi());

        // 0xCB2F
        // reset reg A back to 0xAA
        sampledRegisters.AF.setHi(0xAA);
        // Shift reg A right
        cpu.forceProgramCounterToPosition(0x09, true);
        // Ensure its 0x55
        Assert.assertEquals(0x55, sampledRegisters.AF.getHi());
    }



}
