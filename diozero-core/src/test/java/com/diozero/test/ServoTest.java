package com.diozero.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.tinylog.Logger;

import com.diozero.devices.Servo;
import com.diozero.internal.provider.test.TestDeviceFactory;
import com.diozero.internal.provider.test.TestPwmOutputDevice;
import com.diozero.util.ServoUtil;

@SuppressWarnings("static-method")
public class ServoTest {
	private static final double TOWERPRO_SG5010_MIN_MS = 1;
	private static final double TOWERPRO_SG5010_MAX_MS = 2;
	private static final double TOWERPRO_SG90_MIN_MS = 0.6;
	private static final double TOWERPRO_SG90_MAX_MS = 2.5;
	private static final float DELTA = 0.001f;

	@BeforeAll
	public static void beforeAll() {
		TestDeviceFactory.setPwmOutputDeviceClass(TestPwmOutputDevice.class);
	}

	@Test
	public void testPulsewidthCalcs() {
		int pwm_freq = 60;
		int bits = 12;
		int range = (int) Math.pow(2, bits);
		double ms_per_bit = ServoUtil.calcPulseMsPerBit(pwm_freq, range);

		double pulse_width_ms = TOWERPRO_SG5010_MIN_MS;
		int servo_pulse = ServoUtil.calcServoPulseBits(pulse_width_ms, ms_per_bit);
		System.out.println("TowerPro SG5010 Min: On time=" + pulse_width_ms + ", servo_pulse=" + servo_pulse);
		Assertions.assertEquals(245, servo_pulse);

		pulse_width_ms = TOWERPRO_SG5010_MAX_MS;
		servo_pulse = ServoUtil.calcServoPulseBits(pulse_width_ms, ms_per_bit);
		System.out.println("TowerPro SG5010 Max: On time=" + pulse_width_ms + ", servo_pulse=" + servo_pulse);
		Assertions.assertEquals(491, servo_pulse);

		pulse_width_ms = TOWERPRO_SG90_MIN_MS;
		servo_pulse = ServoUtil.calcServoPulseBits(pulse_width_ms, ms_per_bit);
		System.out.println("TowerPro SG90 Min: On time=" + pulse_width_ms + ", servo_pulse=" + servo_pulse);
		Assertions.assertEquals(147, servo_pulse);

		pulse_width_ms = TOWERPRO_SG90_MAX_MS;
		servo_pulse = ServoUtil.calcServoPulseBits(pulse_width_ms, ms_per_bit);
		System.out.println("TowerPro SG90 Max: On time=" + pulse_width_ms + ", servo_pulse=" + servo_pulse);
		Assertions.assertEquals(614, servo_pulse);

		// Error - value greater than range...
		pulse_width_ms = 20;
		servo_pulse = ServoUtil.calcServoPulseBits(pulse_width_ms, ms_per_bit);
		System.out.println("TowerPro SG90 Max: On time=" + pulse_width_ms + ", servo_pulse=" + servo_pulse);

		double max_pulse_width = 16.666666667;
		servo_pulse = ServoUtil.calcServoPulseBits(max_pulse_width, pwm_freq, range);
		Assertions.assertEquals(range, servo_pulse);
		System.out.println(servo_pulse);

		System.out.println("Max pulse width=" + (1000.0 / pwm_freq) + "ms");

		double val = TOWERPRO_SG90_MIN_MS * pwm_freq / 1000f;
		System.out.println("Pulse width of " + TOWERPRO_SG90_MIN_MS + "ms is " + (val * 100f) + "%");
		Assertions.assertEquals(0.036, val, DELTA);

		pulse_width_ms = val * 1000f / pwm_freq;
		System.out.println("Val of " + (val * 100f) + "% is " + TOWERPRO_SG90_MIN_MS + "ms");
		Assertions.assertEquals(TOWERPRO_SG90_MIN_MS, pulse_width_ms, DELTA);
	}

	@Test
	public void servoTest() {
		Servo.Trim trim = Servo.Trim.MG996R;
		Logger.debug("Min Pulse Width: {}, Min Angle: {}", Float.valueOf(trim.getMinPulseWidthMs()),
				Float.valueOf(trim.getMinAngle()));
		Logger.debug("Mid Pulse Width: {}, Mid Angle: {}", Float.valueOf(trim.getMidPulseWidthMs()),
				Float.valueOf(trim.getMidAngle()));
		Logger.debug("Max Pulse Width: {}, Max Angle: {}", Float.valueOf(trim.getMaxPulseWidthMs()),
				Float.valueOf(trim.getMaxAngle()));
		try (Servo servo = new Servo(0, trim.getMidPulseWidthMs(), trim)) {
			servo.setAngle(0);
			Logger.debug("Value: {}, Pulse Width: {}, Angle: {}", Float.valueOf(servo.getValue()),
					Float.valueOf(servo.getPulseWidthMs()), Float.valueOf(servo.getAngle()));
			Assertions.assertEquals(trim.getMidPulseWidthMs() - trim.getNinetyDegPulseWidthMs(),
					servo.getPulseWidthMs(), DELTA);
			servo.setAngle(90);
			Logger.debug("Value: {}, Pulse Width: {}, Angle: {}", Float.valueOf(servo.getValue()),
					Float.valueOf(servo.getPulseWidthMs()), Float.valueOf(servo.getAngle()));
			Assertions.assertEquals(trim.getMidPulseWidthMs(), servo.getPulseWidthMs(), DELTA);
			servo.setAngle(180);
			Logger.debug("Value: {}, Pulse Width: {}, Angle: {}", Float.valueOf(servo.getValue()),
					Float.valueOf(servo.getPulseWidthMs()), Float.valueOf(servo.getAngle()));
			Assertions.assertEquals(trim.getMidPulseWidthMs() + trim.getNinetyDegPulseWidthMs(),
					servo.getPulseWidthMs(), DELTA);
		}
	}
}
