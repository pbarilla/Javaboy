import com.pat.Register;
import org.junit.Assert;
import org.junit.Test;

public class CPUTests {

    @Test
    public void testRegister_setRegister_thenTestNibbles() {
        Register register = new Register((short) 0xFA);

        Assert.assertEquals(0xF, register.getHi());
        Assert.assertEquals(0xA, register.getLo());
    }

    @Test
    public void testRegister_setRegister_thenSetNibbles_thenTestRegister() {
        Register register = new Register((short) 0xFA);
        Assert.assertEquals(0xF, register.getHi());
        Assert.assertEquals(0xA, register.getLo());

        register.setHi((char) 0xB);
        Assert.assertEquals(0xB, register.getHi());
        Assert.assertEquals(0xA, register.getLo());
        Assert.assertEquals(0xBA, register.getReg());

        register.setLo((char) 0xC);
        Assert.assertEquals(0xB, register.getHi());
        Assert.assertEquals(0xC, register.getLo());
        Assert.assertEquals(0xBC, register.getReg());
    }
}
