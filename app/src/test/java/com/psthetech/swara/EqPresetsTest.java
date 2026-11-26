package com.psthetech.swara;

import static org.junit.Assert.*;

import com.psthetech.swara.audio.EqPresets;

import org.junit.Test;

public class EqPresetsTest {

    @Test public void flatIsAllZero() {
        assertArrayEquals(new int[5], EqPresets.find("flat").levelsMb());
    }

    @Test public void presetsStayInsideRange() {
        for (EqPresets.Preset p : EqPresets.PRESETS) {
            assertEquals(EqPresets.BANDS, p.gainsDb.length);
            for (int mb : p.levelsMb()) assertTrue(p.id, Math.abs(mb) <= EqPresets.RANGE_MB);
        }
    }

    @Test public void snapClampsAndRounds() {
        assertEquals(1200, EqPresets.snap(5000));
        assertEquals(-1200, EqPresets.snap(-5000));
        assertEquals(300, EqPresets.snap(260));
        assertEquals(0, EqPresets.snap(40));
    }

    @Test public void matchingFindsPresetOrNull() {
        assertEquals("rock", EqPresets.matching(EqPresets.find("rock").levelsMb()).id);
        assertNull(EqPresets.matching(new int[]{100, 0, 0, 0, 0}));
    }

    @Test public void mapToDeviceFiveBandsIsIdentity() {
        int[] ref = {700, 500, 100, 0, -200};
        int[] centers = {60000, 230000, 910000, 3600000, 14000000};
        assertArrayEquals(ref, EqPresets.mapToDevice(ref, centers, -1500, 1500));
    }

    @Test public void mapToDeviceInterpolatesAndClamps() {
        int[] ref = {1200, 1200, 0, 0, 0};
        // 3 device bands: low, mid (between 230 and 910 Hz region), high
        int[] out = EqPresets.mapToDevice(ref, new int[]{31000, 500000, 16000000}, -500, 500);
        assertEquals(500, out[0]);          // clamped to device max
        assertTrue(out[1] > 0 && out[1] <= 500);
        assertEquals(0, out[2]);
    }

    @Test public void encodeDecodeRoundTripAndMalformed() {
        int[] v = {100, -200, 0, 300, -1200};
        assertArrayEquals(v, EqPresets.decode(EqPresets.encode(v)));
        assertArrayEquals(new int[5], EqPresets.decode("1,2"));
        assertArrayEquals(new int[5], EqPresets.decode("a,b,c,d,e"));
        assertArrayEquals(new int[5], EqPresets.decode(null));
    }
}
