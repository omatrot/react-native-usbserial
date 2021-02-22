package com.bmateam.reactnativeusbserial;

import android.util.Base64;

import com.facebook.react.bridge.Promise;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

import java.nio.charset.StandardCharsets;

public class UsbSerialDevice {
    public UsbSerialPort port;
    private static final int SERIAL_TIMEOUT = 1000;

    public UsbSerialDevice(UsbSerialPort port) {
        this.port = port;
    }

    public void writeAsync(String value, Promise promise) {

        if (port != null) {

            try {
                String s = new String(value.getBytes(StandardCharsets.UTF_8));
                final byte[] stringBytes = s.getBytes(StandardCharsets.ISO_8859_1);
                int written = port.write(stringBytes, SERIAL_TIMEOUT);

                promise.resolve(written);
            } catch (IOException e) {
                promise.reject(e);
            }

        } else {
            promise.reject(getNoPortErrorMessage());
        }
    }

    public void readAsync(int size, Promise promise) throws IOException {
        if (port != null) {
            final byte[] data = new byte[size];
            int got = port.read(data, SERIAL_TIMEOUT * 4);
            if (got != 20)
            {
                promise.reject(gotInvalidByteCountOnRead(size, got));
            }
            else {
                promise.resolve(Base64.encodeToString(data, Base64.NO_WRAP));
            }
        } else {
            promise.reject(getNoPortErrorMessage());
        }
    }

    public void closeAsync(Promise promise) {
        if (port != null) {
            try {
                port.setDTR(false);
                port.setRTS(false);
            } catch (Exception ignored) {

            }
            try {
                port.close();
                port = null;
            } catch (IOException e) {
                promise.reject(e);
            }
            promise.resolve(null);
        }
    }

    private Exception getNoPortErrorMessage() {
        return new Exception("No port present for the UsbSerialDevice instance");
    }

    private Exception gotInvalidByteCountOnRead(int expected, int got) {
        return new Exception(String.format("Got an invalid byte number while reading data. Expected[%d] Got [%d]", expected, got));
    }
}
