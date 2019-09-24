package com.bmateam.reactnativeusbserial;

import com.facebook.react.bridge.Promise;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

public class UsbSerialDevice {
    public UsbSerialPort port;
    private static final int SERIAL_TIMEOUT = 1000;

    public UsbSerialDevice(UsbSerialPort port) {
        this.port = port;
    }

    public void writeAsync(String value, Promise promise) {

        if (port != null) {

            try {
                int written = port.write(value.getBytes(), SERIAL_TIMEOUT);

                promise.resolve(written);
            } catch (IOException e) {
                promise.reject(e);
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
}
