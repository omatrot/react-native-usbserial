This fork is customized for using a USB oxymeter peripheral.

It is using the same version of the package com.github.kai-morich:usb-serial-for-android package as [SimpleUsbTerminal](https://github.com/kai-morich/SimpleUsbTerminal)

You may have to subscribe to the data event before opening the device, because received data will be sent immediately upon connection.

# react-native-usbserial

This wrapper implementation is totally based on [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android). Thanks [mik3y](https://github.com/mik3y) for the awesome project.

## Installation

```
npm install react-native-usbserial --save
```

## Integrate module

To integrate `react-native-usbserial` with the rest of your react app just execute:
```
react-native link react-native-usbserial
```

### Android

To integrate it with your android application you also need to add these following lines on your `android/app/build.gradle`:

```gradle
repositories {
        maven { url "https://oss.sonatype.org/content/repositories/snapshots/" }
}
```

## Usage

```javascript
import { UsbSerial} from 'react-native-usbserial';

const usbs = new UsbSerial();

async function getDeviceAsync() {

    try {
        const deviceList = await usbs.getDeviceListAsync();
        const firstDevice = deviceList[0];
        
        console.log(firstDevice);

        if (firstDevice) {
            const usbSerialDevice = await usbs.openDeviceAsync(firstDevice);
            
            console.log(usbSerialDevice);
        }
    } catch (err) {
        console.warn(err);
    }
}

getDeviceAsync();
```
