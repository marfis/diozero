package com.diozero.sampleapps;

/*-
 * #%L
 * Organisation: diozero
 * Project:      diozero - Sample applications
 * Filename:     SevenSegmentDisplayTest.java
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

import com.diozero.devices.SevenSegmentDisplay;
import com.diozero.util.SleepUtil;

public class SevenSegmentDisplayTest {
	public static void main(String[] args) {
		try (SevenSegmentDisplay disp = new SevenSegmentDisplay(25, 23, 5, 6, 16, 24, 11,
				new int[] { 20, 21, 19, 26 })) {
			Logger.info("First only");
			boolean[] digits = new boolean[] { true, false, false, false };
			int delay_ms = 200;
			for (int i = 0; i < 10; i++) {
				disp.displayNumbers(i, digits);
				SleepUtil.sleepMillis(delay_ms);
			}

			Logger.info("All");
			digits = new boolean[] { true, true, true, true };
			for (int i = 0; i < 10; i++) {
				disp.displayNumbers(i, digits);
				SleepUtil.sleepMillis(delay_ms);
			}

			Logger.info("None");
			digits = new boolean[] { false, false, false, false };
			for (int i = 0; i < 10; i++) {
				disp.displayNumbers(i, digits);
				SleepUtil.sleepMillis(delay_ms);
			}

			Logger.info("Alternate");
			digits = new boolean[] { true, false, true, false };
			for (int i = 0; i < 10; i++) {
				disp.displayNumbers(i, digits);
				SleepUtil.sleepMillis(delay_ms);
			}

			Logger.info("Countdown");
			// Countdown from 9999
			int number = 9999;
			int decrement_delta_ms = 50;
			long last_change = System.currentTimeMillis();
			while (true) {
				for (int i = 0; i < 4; i++) {
					int digit = (number / (int) (Math.pow(10, 3 - i))) % 10;
					disp.enableDigit(i);
					disp.displayNumber(digit);
					SleepUtil.sleepMillis(5);
				}
				if (System.currentTimeMillis() - last_change > decrement_delta_ms) {
					number--;
					if (number < 0) {
						number = 9999;
					}
					last_change = System.currentTimeMillis();
				}
			}
		}
	}
}
