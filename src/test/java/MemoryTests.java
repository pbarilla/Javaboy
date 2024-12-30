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
}
