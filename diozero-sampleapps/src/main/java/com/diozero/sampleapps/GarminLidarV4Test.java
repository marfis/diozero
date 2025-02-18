package com.diozero.sampleapps;

/*-
 * #%L
 * Organisation: diozero
 * Project:      diozero - Sample applications
 * Filename:     GarminLidarV4Test.java
 * 
 * This file is part of the diozero project. More information about this project
 * can be found at https://www.diozero.com/.
 * %%
 * Copyright (C) 2016 - 2021 diozero
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import org.tinylog.Logger;

import com.diozero.devices.GarminLidarLiteV4;
import com.diozero.devices.GarminLidarLiteV4.PowerMode;
import com.diozero.util.Hex;

/*-
 * Unit Id: 560C0003
 * Hardware Version: 16. A
 * Firmware Version: 210. 2.10
 * Lib Version: BKY2.02B00 
 * Board Temperature: 37
 * SoC Temperature: 36
 * Flash storage enabled: false
 * Power mode: ALWAYS_ON
 * Detection sensitivity: 0
 * High accuracy mode: 20
 * High accuracy mode enabled: true
 * Max acquisition count: 255
 * Quick acquisition termination enabled: false
 */
@SuppressWarnings("boxing")
public class GarminLidarV4Test {
	public static void main(String[] args) {
		try (GarminLidarLiteV4 lidar = new GarminLidarLiteV4()) {
			printVersions(lidar);
			printTemperatures(lidar);

			System.out.format("Status: 0x%x%n", lidar.getStatusByte());

			lidar.configure(GarminLidarLiteV4.Preset.MAXIMUM_RANGE);
			lidar.disableHighAccuracyMode();
			lidar.setPowerMode(PowerMode.ALWAYS_ON);
			while (lidar.getStatus().isInLowerPowerMode()) {
				Thread.sleep(1);
			}
			lidar.setHighAccuracyMode(20);
			System.out.format("Status: 0x%x%n", lidar.getStatusByte());

			printConfig(lidar);
			for (int i = 0; i < 10; i++) {
				int reading = lidar.getSingleReading();
				System.out.println("Distance: " + reading + " cm");
				System.out.format("Status: 0x%x%n", lidar.getStatusByte());
				Thread.sleep(100);
			}

			lidar.disableHighAccuracyMode();
			lidar.setQuickTerminationEnabled(true);
			lidar.setMaximumAcquisitionCount(128);
			System.out.format("Status: 0x%x%n", lidar.getStatusByte());

			printConfig(lidar);
			for (int i = 0; i < 10; i++) {
				int reading = lidar.getSingleReading();
				System.out.println("Distance: " + reading + " cm");
				System.out.format("Status: 0x%x%n", lidar.getStatusByte());
				Thread.sleep(100);
			}

			lidar.configure(GarminLidarLiteV4.Preset.MID_RANGE_HIGH_SPEED);
			lidar.setPowerMode(PowerMode.ASYNCHRONOUS);
			System.out.format("Status: 0x%x%n", lidar.getStatusByte());

			printConfig(lidar);
			for (int i = 0; i < 10; i++) {
				int reading = lidar.getSingleReading();
				System.out.println("Distance: " + reading + " cm");
				System.out.format("Status: 0x%x%n", lidar.getStatusByte());
				Thread.sleep(100);
			}
		} catch (InterruptedException e) {
			Logger.info(e, "Interrupted: {}", e);
		}
	}

	private static void printVersions(GarminLidarLiteV4 lidar) {
		System.out.println("Unit Id: " + Hex.encodeHexString(lidar.getUnitId()));
		int hardware_version = lidar.getHardwareVersion();
		System.out.format("Hardware Version: 0x%x. Rev %s%n", hardware_version,
				GarminLidarLiteV4.HardwareRevision.of(hardware_version));
		int firmware_version = lidar.getFirmwareVersion();
		System.out.format("Firmware Version: v%d.%d%n", (firmware_version / 100), (firmware_version % 100));
		System.out.format("Library Version: %s%n", lidar.getLibraryVersion());
	}

	private static void printTemperatures(GarminLidarLiteV4 lidar) {
		System.out.format("Board Temperature: %d dec C%n", lidar.getBoardTemperature());
		System.out.format("SoC Temperature: %d dec C%n", lidar.getSoCTemperature());
	}

	private static void printConfig(GarminLidarLiteV4 lidar) {
		System.out.format("Flash storage enabled: %b%n", lidar.isFlashStorageEnabled());
		System.out.format("Power mode: %s%n", lidar.getPowerMode());
		System.out.format("Detection sensitivity: %d%n", lidar.getDetectionSensitivity());
		System.out.format("High accuracy mode: %d%n", lidar.getHighAccuracyMode());
		System.out.format("High accuracy mode enabled: %b%n", lidar.isHighAccuracyModeEnabled());
		System.out.format("Max acquisition count: %d%n", lidar.getMaximumAcquisitionCount());
		System.out.format("Quick acquisition termination enabled: %b%n", lidar.isQuickAcquistionTerminationEnabled());
	}
}
