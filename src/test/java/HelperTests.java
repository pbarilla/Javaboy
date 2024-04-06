import org.junit.Assert;
import org.junit.Test;

import static com.pat.Helpful.leastSignificantBit;
import static com.pat.Helpful.mostSignificantBit;

public class HelperTests {
    @Test
    public void testLeastSignificantBit() {
        Assert.assertEquals(1, leastSignificantBit((byte) 0x3));
        Assert.assertEquals(0, leastSignificantBit((byte) 0x4));
    }

    @Test
    public void testMostSignificantBit() {
        Assert.assertEquals(1, mostSignificantBit((byte) 0xFF));
        Assert.assertEquals(0, mostSignificantBit((byte) 0x00));
        Assert.assertEquals(0, mostSignificantBit((byte) 0x3));
        Assert.assertEquals(0, mostSignificantBit((byte) 0x4));
        Assert.assertEquals(1, mostSignificantBit((byte) 0x80));
    }
}
