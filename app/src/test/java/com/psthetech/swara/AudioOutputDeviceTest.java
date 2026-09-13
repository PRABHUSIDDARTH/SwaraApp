package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.AudioDeviceInfo;

import com.psthetech.swara.domain.model.AudioOutputDevice;

import org.junit.Test;

/**
 * Unit tests for AudioOutputDevice model, type mappings, display names, and identity.
 */
public class AudioOutputDeviceTest {

    @Test
    public void testMapDeviceInfoType_speaker() {
        assertEquals(AudioOutputDevice.OutputType.BUILT_IN_SPEAKER,
                AudioOutputDevice.mapDeviceInfoType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
        assertEquals(AudioOutputDevice.OutputType.BUILT_IN_SPEAKER,
                AudioOutputDevice.mapDeviceInfoType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE));
    }

    @Test
    public void testMapDeviceInfoType_wiredAndUsb() {
        assertEquals(AudioOutputDevice.OutputType.WIRED_HEADSET,
                AudioOutputDevice.mapDeviceInfoType(AudioDeviceInfo.TYPE_WIRED_HEADSET));
        assertEquals(AudioOutputDevice.OutputType.WIRED_HEADSET,
                AudioOutputDevice.mapDeviceInfoType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES));
        assertEquals(AudioOutputDevice.OutputType.WIRED_HEADSET,
                AudioOutputDevice.mapDeviceInfoType(AudioDeviceInfo.TYPE_LINE_ANALOG));

        assertEquals(AudioOutputDevice.OutputType.USB_AUDIO,
                AudioOutputDevice.mapDeviceInfoType(AudioDeviceInfo.TYPE_USB_DEVICE));
        assertEquals(AudioOutputDevice.OutputType.USB_AUDIO,
                AudioOutputDevice.mapDeviceInfoType(AudioDeviceInfo.TYPE_USB_ACCESSORY));
        assertEquals(AudioOutputDevice.OutputType.USB_AUDIO,
                AudioOutputDevice.mapDeviceInfoType(22)); // TYPE_USB_HEADSET
    }

    @Test
    public void testMapDeviceInfoType_bluetoothAndBle() {
        assertEquals(AudioOutputDevice.OutputType.BLUETOOTH,
                AudioOutputDevice.mapDeviceInfoType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
        assertEquals(AudioOutputDevice.OutputType.BLUETOOTH,
                AudioOutputDevice.mapDeviceInfoType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO));
        assertEquals(AudioOutputDevice.OutputType.BLUETOOTH,
                AudioOutputDevice.mapDeviceInfoType(23)); // TYPE_HEARING_AID

        assertEquals(AudioOutputDevice.OutputType.BLUETOOTH_LE,
                AudioOutputDevice.mapDeviceInfoType(26)); // TYPE_BLE_HEADSET
        assertEquals(AudioOutputDevice.OutputType.BLUETOOTH_LE,
                AudioOutputDevice.mapDeviceInfoType(27)); // TYPE_BLE_SPEAKER
        assertEquals(AudioOutputDevice.OutputType.BLUETOOTH_LE,
                AudioOutputDevice.mapDeviceInfoType(30)); // TYPE_BLE_BROADCAST
    }

    @Test
    public void testMapDeviceInfoType_unknown() {
        assertEquals(AudioOutputDevice.OutputType.UNKNOWN,
                AudioOutputDevice.mapDeviceInfoType(-1));
        assertEquals(AudioOutputDevice.OutputType.UNKNOWN,
                AudioOutputDevice.mapDeviceInfoType(99999));
    }

    @Test
    public void testTypeDisplayName() {
        AudioOutputDevice speaker = new AudioOutputDevice(
                "1", "Speaker", AudioOutputDevice.OutputType.BUILT_IN_SPEAKER,
                false, true, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, null);
        assertEquals("Phone Speaker", speaker.getTypeDisplayName());

        AudioOutputDevice bt = new AudioOutputDevice(
                "2", "Galaxy Buds", AudioOutputDevice.OutputType.BLUETOOTH,
                false, true, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, null);
        assertEquals("Bluetooth Audio", bt.getTypeDisplayName());

        AudioOutputDevice wired = new AudioOutputDevice(
                "3", "Headphones", AudioOutputDevice.OutputType.WIRED_HEADSET,
                false, true, AudioDeviceInfo.TYPE_WIRED_HEADSET, null);
        assertEquals("Wired Audio / Headphones", wired.getTypeDisplayName());

        AudioOutputDevice usb = new AudioOutputDevice(
                "4", "USB DAC", AudioOutputDevice.OutputType.USB_AUDIO,
                false, true, AudioDeviceInfo.TYPE_USB_DEVICE, null);
        assertEquals("USB-C Audio", usb.getTypeDisplayName());

        AudioOutputDevice ble = new AudioOutputDevice(
                "5", "BLE Earbuds", AudioOutputDevice.OutputType.BLUETOOTH_LE,
                false, true, 26, null);
        assertEquals("Bluetooth LE Audio", ble.getTypeDisplayName());
    }

    @Test
    public void testIconResIdMapping() {
        AudioOutputDevice speaker = new AudioOutputDevice(
                "1", "Speaker", AudioOutputDevice.OutputType.BUILT_IN_SPEAKER,
                false, true, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, null);
        assertEquals(R.drawable.ic_speaker, speaker.getIconResId());

        AudioOutputDevice bt = new AudioOutputDevice(
                "2", "Buds", AudioOutputDevice.OutputType.BLUETOOTH,
                false, true, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, null);
        assertEquals(R.drawable.ic_bluetooth, bt.getIconResId());

        AudioOutputDevice wired = new AudioOutputDevice(
                "3", "Headphones", AudioOutputDevice.OutputType.WIRED_HEADSET,
                false, true, AudioDeviceInfo.TYPE_WIRED_HEADSET, null);
        assertEquals(R.drawable.ic_headphones, wired.getIconResId());

        AudioOutputDevice usb = new AudioOutputDevice(
                "4", "USB", AudioOutputDevice.OutputType.USB_AUDIO,
                false, true, AudioDeviceInfo.TYPE_USB_DEVICE, null);
        assertEquals(R.drawable.ic_usb, usb.getIconResId());
    }

    @Test
    public void testCopyWithCurrent() {
        AudioOutputDevice original = new AudioOutputDevice(
                "dev_1", "Buds Pro", AudioOutputDevice.OutputType.BLUETOOTH,
                false, true, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, null);

        assertFalse(original.isCurrent());

        AudioOutputDevice copied = original.copyWithCurrent(true);
        assertTrue(copied.isCurrent());
        assertEquals(original.getId(), copied.getId());
        assertEquals(original.getName(), copied.getName());
        assertEquals(original.getType(), copied.getType());
        assertEquals(original.getRawType(), copied.getRawType());
        assertEquals(original.isSelectable(), copied.isSelectable());
    }

    @Test
    public void testEqualsAndHashCode() {
        AudioOutputDevice dev1 = new AudioOutputDevice(
                "10", "Buds", AudioOutputDevice.OutputType.BLUETOOTH,
                false, true, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, null);
        AudioOutputDevice dev2 = new AudioOutputDevice(
                "10", "Buds", AudioOutputDevice.OutputType.BLUETOOTH,
                true, true, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, null);
        AudioOutputDevice dev3 = new AudioOutputDevice(
                "11", "Speaker", AudioOutputDevice.OutputType.BUILT_IN_SPEAKER,
                false, true, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, null);

        assertEquals(dev1, dev2);
        assertEquals(dev1.hashCode(), dev2.hashCode());
        assertNotEquals(dev1, dev3);
        assertNotNull(dev1.toString());
    }
}
