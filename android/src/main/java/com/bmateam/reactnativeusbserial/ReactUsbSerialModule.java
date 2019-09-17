// Original version comes from
// https://github.com/edvijaka/react-native-usbserial/blob/master/android/src/main/java/com/bmateam/reactnativeusbserial/ReactUsbSerialModule.java
// It has been customized with code coming from https://github.com/kai-morich/SimpleUsbTerminal

package com.bmateam.reactnativeusbserial;

import android.util.Log;
import androidx.annotation.NonNull;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.Base64;

import static android.content.ContentValues.TAG;

public class ReactUsbSerialModule extends ReactContextBaseJavaModule {

    private final HashMap<String, UsbSerialDevice> usbSerialDriverDict = new HashMap<>();

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private UsbDeviceConnection mConnection;

    UsbSerialDriver currentDriver = null;

    private SerialInputOutputManager.Listener mListener = new SerialInputOutputManager.Listener() {
        @Override
        public void onRunError(Exception e) {
            Log.d(TAG, "Runner stopped.");
        }

        @Override
        public void onNewData(final byte[] data) {
            Log.d(TAG, "received " + data.length + " bytes of data from usb device");
            if (data.length > 0) {
                ReactUsbSerialModule.this.emitNewData(data);
            }
        }
    };

    public ReactApplicationContext REACTCONTEXT;

    public ReactUsbSerialModule(ReactApplicationContext reactContext) {
        super(reactContext);
        REACTCONTEXT = reactContext;
    }

    @Override
    public String getName() {
        return "UsbSerial";
    }

    @ReactMethod
    public void getDeviceListAsync(Promise p) {

        try {
            UsbManager usbManager = getUsbManager();

            HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
            WritableArray deviceArray = Arguments.createArray();

            for (String key : usbDevices.keySet()) {
                UsbDevice device = usbDevices.get(key);
                WritableMap map = Arguments.createMap();

                map.putString("name", device.getDeviceName());
                map.putInt("deviceId", device.getDeviceId());
                map.putInt("productId", device.getProductId());
                map.putInt("vendorId", device.getVendorId());
                map.putString("deviceName", device.getDeviceName());

                deviceArray.pushMap(map);
            }

            p.resolve(deviceArray);
        } catch (Exception e) {
            p.reject(e);
        }
    }

    @ReactMethod
    public void openDeviceAsync(ReadableMap deviceObject, Promise p) {

        try {
            int prodId = deviceObject.getInt("productId");
            UsbManager manager = getUsbManager();

            HashMap<String, UsbDevice> usbDevices = manager.getDeviceList();
            for (String key : usbDevices.keySet()) {
                UsbDevice device = usbDevices.get(key);

                if (device.getProductId() == prodId) {
                    currentDriver =
                            new CdcAcmSerialDriver(device);

                    if (manager.hasPermission(device)) {
                        WritableMap usd = createUsbSerialDevice(manager, currentDriver);
                        p.resolve(usd);
                        break;
                    } else {
                        requestUsbPermission(manager, device, p);
                    }

                    break;
                }
            }

        } catch (Exception e) {
            String stackTrace = Log.getStackTraceString(e);
            p.reject(stackTrace, e);
        }
    }

    @ReactMethod
    public void closeDeviceAsync(String deviceId, Promise p) {

        try {

            UsbSerialDevice usd = usbSerialDriverDict.get(deviceId);

            if (usd == null) {
                throw new Exception(String.format("No device opened for the id '%s'", deviceId));
            }
            mListener = null;

            if (mSerialIoManager != null) {
                mSerialIoManager.setListener(null);
                mSerialIoManager.stop();
                mSerialIoManager = null;
            }

            usd.closeAsync(p);

            if (mConnection != null) {
                mConnection.close();
                mConnection = null;
            }

        } catch (Exception e) {
            p.reject(e);
        }
    }

    @ReactMethod
    public void writeInDeviceAsync(String deviceId, String value, Promise p) {

        try {
            UsbSerialDevice usd = usbSerialDriverDict.get(deviceId);

            if (usd == null) {
                throw new Exception(String.format("No device opened for the id '%s'", deviceId));
            }

            usd.writeAsync(value, p);
        } catch (Exception e) {
            p.reject(e);
        }
    }

    private void sendEvent(ReactContext reactContext, String eventName, @NonNull WritableMap params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
    }

    public void emitNewData(byte[] data) {
        if (REACTCONTEXT != null) {
            WritableMap params = Arguments.createMap();
            params.putString("data", Base64.encodeToString(data, Base64.DEFAULT));
            sendEvent(REACTCONTEXT, "newData", params);
        }
    }

    private WritableMap createUsbSerialDevice(UsbManager manager, UsbSerialDriver driver) throws IOException {

        mConnection = manager.openDevice(driver.getDevice());

        // Most have just one port (port 0).
        UsbSerialPort port = driver.getPorts().get(0);

        port.open(mConnection);
        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        port.setDTR(true);
        port.setRTS(true);
        mSerialIoManager = new SerialInputOutputManager(port, mListener);
        mExecutor.submit(mSerialIoManager);

        String id = generateId();
        UsbSerialDevice usd = new UsbSerialDevice(port);
        WritableMap map = Arguments.createMap();

        // Add UsbSerialDevice to the usbSerialDriverDict map
        usbSerialDriverDict.put(id, usd);

        map.putString("id", id);

        return map;
    }

    private void requestUsbPermission(UsbManager manager, UsbDevice device, Promise p) {

        try {
            ReactApplicationContext rAppContext = getReactApplicationContext();
            PendingIntent permIntent = PendingIntent.getBroadcast(rAppContext, 0, new Intent(ACTION_USB_PERMISSION), 0);

            registerBroadcastReceiver(p);

            manager.requestPermission(device, permIntent);
        } catch (Exception e) {
            p.reject(e);
        }
    }

    private static final String ACTION_USB_PERMISSION = "com.bmateam.reactnativeusbserial.USB_PERMISSION";

    private UsbManager getUsbManager() {
        ReactApplicationContext rAppContext = getReactApplicationContext();
        UsbManager usbManager = (UsbManager) rAppContext.getSystemService(rAppContext.USB_SERVICE);

        return usbManager;
    }

    private UsbSerialDriver getUsbSerialDriver(int prodId, UsbManager manager) throws Exception {

        if (prodId == 0)
            throw new Error(new Error("The deviceObject is not a valid 'UsbDevice' reference"));

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);

        // Reject if no driver is available
        if (availableDrivers.isEmpty())
            throw new Exception("No available drivers to communicate with devices");

        for (UsbSerialDriver drv : availableDrivers) {

            if (drv.getDevice().getProductId() == prodId)
                return drv;
        }

        // Reject if no driver exists for the current productId
        throw new Exception(String.format("No driver found for productId '%s'", prodId));
    }

    private void registerBroadcastReceiver(final Promise p) {
        IntentFilter intFilter = new IntentFilter(ACTION_USB_PERMISSION);
        final BroadcastReceiver receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                if (ACTION_USB_PERMISSION.equals(intent.getAction())) {

                    synchronized (this) {
                        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                        boolean usbPermissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                        if (usbPermissionGranted) {
                            UsbManager manager = getUsbManager();

                            try {
                                WritableMap usd = createUsbSerialDevice(manager, currentDriver);
                                p.resolve(usd);
                            } catch (Exception e) {
                                p.reject(e);
                            }

                        } else {
                            p.reject(new Exception(
                                    String.format("Permission denied by user for device %s", device.getDeviceName())));
                        }
                    }
                }
                unregisterReceiver(this);
            }
        };

        getReactApplicationContext().registerReceiver(receiver, intFilter);

    }

    private void unregisterReceiver(BroadcastReceiver receiver) {
        getReactApplicationContext().unregisterReceiver(receiver);
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }
}
