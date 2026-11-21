package my.glasstv.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiagnosticsTest {
    @Test
    public void keepsTheLastLinesOnly() {
        StringBuilder log = new StringBuilder();
        for (int i = 1; i <= 100; i++) log.append("line ").append(i).append('\n');
        String d = Diagnostics.report("Butang Home tidak membuka Glass TV.", "123|456|mCurrentFocus=x", log.toString());
        assertTrue(d.startsWith("Sebab: Butang Home tidak membuka Glass TV."));
        assertTrue(d.contains("Semakan: 123|456|mCurrentFocus=x"));
        assertTrue(d.contains("line 100"));
        assertTrue(d.contains("line 61"));
        assertTrue(!d.contains("line 60\n"));
    }

    @Test
    public void emptyPartsAreSaid() {
        assertEquals("Sebab: x\nSemakan: (tiada)\nLog:\n(tiada)", Diagnostics.report("x", "", " "));
    }
}
