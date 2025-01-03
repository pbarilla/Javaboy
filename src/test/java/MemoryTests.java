import com.pat.Memory;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class MemoryTests {


    @Test
    public void testLoadingRom_testCopyToGeneralMemory() throws IOException {
        Memory memory = new Memory();
        memory.loadTestRom();
        Assert.assertEquals(memory.cartMemory[0x0000], memory.generalMemory[0x0000]);
        Assert.assertEquals(memory.cartMemory[0x1000], memory.generalMemory[0x1000]);
        Assert.assertEquals(memory.cartMemory[0x0300], memory.generalMemory[0x0300]);
    }

    @Test
    public void testMemoryInit() {
        Memory memory = new Memory();
        Assert.assertEquals(0x00, memory.generalMemory[0xFF05]);
        Assert.assertEquals(0xBF, memory.generalMemory[0xFF11]);
    }

    @Test
    public void testEchoWrites() {
        Memory memory = new Memory();
        Assert.assertEquals(0x00, memory.readByteFromLocation(0x0000));
        // This write should be rejected
        memory.writeByteToLocation(0x0010, 0xFF);
        Assert.assertEquals(0x00, memory.readByteFromLocation(0x0000));

        // This will ECHO 0xC100 -> 0xE100
        memory.writeByteToLocation(0xAA, 0xC100);
        Assert.assertEquals(0xAA, memory.readByteFromLocation(0xC100));
        Assert.assertEquals(0xAA, memory.readByteFromLocation(0xE100));

        // This will test reverse ECHO of 0xE200 -> 0xC200
        memory.writeWordToLocation(0xDD, 0xE200);
        Assert.assertEquals(0xDD, memory.readByteFromLocation(0xC200));
        Assert.assertEquals(0xDD, memory.readByteFromLocation(0xE200));
    }


}
