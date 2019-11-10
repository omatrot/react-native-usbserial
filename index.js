"use strict";

import { Platform, NativeModules } from "react-native";
import UsbSerialDevice from "./UsbSerialDevice";

const UsbSerialModule = NativeModules.UsbSerial;

export class UsbSerial {
  constructor() {
    if (Platform.OS != "android") {
      throw "Unfortunately only android is supported";
    }
  }

  getDeviceListAsync() {
    return UsbSerialModule.getDeviceListAsync();
  }

  async openDeviceAsync(deviceObject = {}) {
    return await UsbSerialModule.openDeviceAsync(deviceObject).then(
      usbSerialDevNativeObject => {
        return new UsbSerialDevice(UsbSerialModule, usbSerialDevNativeObject);
      }
    );
  }

  async writeInDeviceAsync(id, data) {
    return await UsbSerialModule.writeInDeviceAsync(id, data);
  }

  async closeDeviceAsync(id) {
    await UsbSerialModule.closeDeviceAsync(id);
  }
}
