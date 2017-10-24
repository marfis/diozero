package com.diozero.internal.provider.remote.firmata;

/*-
 * #%L
 * Organisation: mattjlewis
 * Project:      Device I/O Zero - Remote Provider
 * Filename:     FirmataAdapter.java  
 * 
 * This file is part of the diozero project. More information about this project
 * can be found at http://www.diozero.com/
 * %%
 * Copyright (C) 2016 - 2017 mattjlewis
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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FirmataAdapter implements FirmataProtocol, Runnable, Closeable {
	private static final int I2C_NO_REGISTER = 0;
	
	private AtomicBoolean running;
	private InputStream is;
	private OutputStream os;
	private Queue<ResponseMessage> responseQueue;
	private Lock lock;
	private Condition condition;
	private ExecutorService executor;
	private Future<?> future;
	private ProtocolVersion protocolVersion;
	private Map<Integer, Pin> pins;

	public FirmataAdapter() {
		running = new AtomicBoolean(false);
		responseQueue = new ConcurrentLinkedQueue<>();
		lock = new ReentrantLock();
		condition = lock.newCondition();
		pins = new HashMap<>();
		
		executor = Executors.newSingleThreadExecutor();
	}
	
	protected void connect(InputStream input, OutputStream output) throws IOException {
		this.is = input;
		this.os = output;
		
		future = executor.submit(this);
		
		protocolVersion = getProtocolVersionInternal();
	}
	
	private Pin getPin(int gpio) {
		Integer gpio_int = Integer.valueOf(gpio);
		Pin pin_state = pins.get(gpio_int);
		if (pin_state == null) {
			pin_state = new Pin(gpio);
			pins.put(gpio_int, pin_state);
		}
		
		return pin_state;
	}
	
	private ProtocolVersion getProtocolVersionInternal() throws IOException {
		return sendMessage(new byte[] { PROTOCOL_VERSION }, ProtocolVersion.class);
	}
	
	public ProtocolVersion getProtocolVersion() {
		return protocolVersion;
	}
	
	public FirmwareDetails getFirmware() throws IOException {
		return sendMessage(new byte[] { START_SYSEX, REPORT_FIRMWARE, END_SYSEX }, FirmwareDetails.class);
	}
	
	public List<List<PinCapability>> getBoardCapabilities() throws IOException {
		CapabilityResponse board_capabilities = sendMessage(new byte[] { START_SYSEX, CAPABILITY_QUERY, END_SYSEX },
				CapabilityResponse.class);
		
		// Cache the pin capabilities
		int gpio = 0;
		for (List<PinCapability> pin_capabilities : board_capabilities.getCapabilities()) {
			getPin(gpio++).setCapabilities(pin_capabilities);
		}
		
		return board_capabilities.getCapabilities();
	}
	
	public PinState getPinState(int gpio) throws IOException {
		PinState pin_state = sendMessage(new byte[] { START_SYSEX, PIN_STATE_QUERY, (byte) gpio, END_SYSEX }, PinState.class);
		
		// Update the cached pin mode
		getPin(gpio).setMode(pin_state.getMode());
		// For output modes, the state is any value that has been previously written to the pin
		if (pin_state.getMode().isOutput()) {
			getPin(gpio).setValue(pin_state.getState());
		}
		
		return pin_state;
	}
	
	public void setSamplingInterval(int intervalMs) throws IOException {
		byte[] lsb_msb = convertToLsbMsb(intervalMs);
		sendMessage(new byte[] { START_SYSEX, SAMPLING_INTERVAL, (byte) (intervalMs & 0x7f), lsb_msb[0], lsb_msb[1], END_SYSEX });
	}
	
	// Note enables / disables reporting for all GPIOs in this bank of GPIOs
	public void enableDigitalReporting(int gpio, boolean enabled) throws IOException {
		System.out.println("Enable digital reporting: " + enabled);
		sendMessage(new byte[] { (byte) (REPORT_DIGITAL_PORT | (gpio >> 3)), (byte) gpio, (byte) (enabled ? 1 : 0) });
	}
	
	public PinMode getPinMode(int gpio) {
		return getPin(gpio).getMode();
	}
	
	public void setPinMode(int gpio, PinMode pinMode) throws IOException {
		System.out.println("setPinMode(" + gpio + ", " + pinMode + ")");
		sendMessage(new byte[] { SET_PIN_MODE, (byte) gpio, (byte) pinMode.ordinal() });
		
		// Update the cached pin mode
		getPin(gpio).setMode(pinMode);
	}
	
	public void setDigitalValues(int port, byte values) throws IOException {
		System.out.format("setValues(%d, %d)%n", Integer.valueOf(port), Integer.valueOf(values));
		byte[] lsb_msb = convertToLsbMsb(values);
		sendMessage(new byte[] { (byte) (DIGITAL_IO_START | (port & 0x0f)), lsb_msb[0], lsb_msb[1] });
		
		// Update the cached values
		for (int i=0; i<8; i++) {
			int gpio = 8*port + i;
			getPin(gpio).setValue((values & (1 << i)) != 0);
		}
	}
	
	public boolean getDigitalValue(int gpio) {
		return getPin(gpio).getValue() != 0;
	}
	
	public void setDigitalValue(int gpio, boolean value) throws IOException {
		System.out.format("setDigitalValue(%d, %b)%n", Integer.valueOf(gpio), Boolean.valueOf(value));
		sendMessage(new byte[] { SET_DIGITAL_PIN_VALUE, (byte) gpio, (byte) (value ? 1 : 0) });
		
		// Update the cached value
		getPin(gpio).setValue(value);
	}
	
	public int getValue(int gpio) {
		return getPin(gpio).getValue();
	}
	
	public void setValue(int gpio, int value) throws IOException {
		System.out.format("setValue(%d, %d)%n", Integer.valueOf(gpio), Integer.valueOf(value));
		// Non-extended analog accommodates 16 ports (E0-Ef), with a max value of 16384 (2^14)
		if (gpio < 16 && value < 16384) {
			byte[] lsb_msb = convertToLsbMsb(value);
			sendMessage(new byte[] { (byte) (ANALOG_IO_START | gpio), lsb_msb[0], lsb_msb[1] });
		} else {
			byte[] bytes = convertToBytes(value);
			byte[] data = new byte[4 + bytes.length];
			data[0] = START_SYSEX;
			data[1] = EXTENDED_ANALOG;
			data[2] = (byte) gpio;
			System.arraycopy(bytes, 0, data, 3, bytes.length);
			data[data.length-1] = END_SYSEX;
			sendMessage(data);
		}
	}
	
	public AnalogMappingResponse getAnalogMapping() throws IOException {
		return sendMessage(new byte[] { START_SYSEX, ANALOG_MAPPING_QUERY, END_SYSEX }, AnalogMappingResponse.class);
	}
	
	public I2CResponse i2cRead(int slaveAddress, boolean autoRestart, boolean addressSize10Bit, int length)
			throws IOException {
		byte[] data = new byte[7];
		int index = 0;
		data[index++] = START_SYSEX;
		data[index++] = I2C_REQUEST;
		byte[] address_lsb_msb = convertToI2CSlaveAddressLsbMsb(slaveAddress, autoRestart, addressSize10Bit,
				I2CMode.READ_ONCE);
		data[index++] = address_lsb_msb[0];
		data[index++] = address_lsb_msb[1];
		byte[] length_lsb_msb = convertToLsbMsb(length);
		data[index++] = length_lsb_msb[0];
		data[index++] = length_lsb_msb[1];
		data[index++] = END_SYSEX;
		
		return sendMessage(data, I2CResponse.class);
	}
	
	public void i2cWrite(int slaveAddress, boolean autoRestart, boolean addressSize10Bit, byte[] data)
			throws IOException {
		i2cWriteData(slaveAddress, autoRestart, addressSize10Bit, I2C_NO_REGISTER, data);
	}
	
	public I2CResponse i2cReadData(int slaveAddress, boolean autoRestart, boolean addressSize10Bit, int register, int length)
			throws IOException {
		byte[] data = new byte[9];
		int index = 0;
		data[index++] = START_SYSEX;
		data[index++] = I2C_REQUEST;
		byte[] address_lsb_msb = convertToI2CSlaveAddressLsbMsb(slaveAddress, autoRestart, addressSize10Bit,
				I2CMode.READ_ONCE);
		data[index++] = address_lsb_msb[0];
		data[index++] = address_lsb_msb[1];
		byte[] register_lsb_msb = convertToLsbMsb(register);
		data[index++] = register_lsb_msb[0];
		data[index++] = register_lsb_msb[1];
		byte[] length_lsb_msb = convertToLsbMsb(length);
		data[index++] = length_lsb_msb[0];
		data[index++] = length_lsb_msb[1];
		data[index++] = END_SYSEX;
		
		return sendMessage(data, I2CResponse.class);
	}
	
	public void i2cWriteData(int slaveAddress, boolean autoRestart, boolean addressSize10Bit, int register, byte[] data)
			throws IOException {
		byte[] buffer = new byte[7 + 2*data.length];
		int index = 0;
		buffer[index++] = START_SYSEX;
		buffer[index++] = I2C_REQUEST;
		byte[] address_lsb_msb = convertToI2CSlaveAddressLsbMsb(slaveAddress, autoRestart, addressSize10Bit, I2CMode.WRITE);
		buffer[index++] = address_lsb_msb[0];
		buffer[index++] = address_lsb_msb[1];
		byte[] register_lsb_msb = convertToLsbMsb(register);
		data[index++] = register_lsb_msb[0];
		data[index++] = register_lsb_msb[1];
		for (int i=0; i<data.length; i++) {
			byte[] data_lsb_msb = convertToLsbMsb(data[i]);
			buffer[index++] = data_lsb_msb[0];
			buffer[index++] = data_lsb_msb[1];
		}
		buffer[index++] = END_SYSEX;
		
		sendMessage(buffer);
	}
	
	private void sendMessage(byte[] request) throws IOException {
		sendMessage(request, null);
	}
	
	@SuppressWarnings("unchecked")
	private <T extends ResponseMessage> T sendMessage(byte[] request, Class<T> responseClass) throws IOException {
		ResponseMessage response = null;
		
		lock.lock();
		try {
			os.write(request);
			
			if (responseClass != null) {
				// Wait for the response message
				condition.await();
				response = responseQueue.remove();
			}
		} catch (InterruptedException e) {
			// Ignore
		} finally {
			lock.unlock();
		}
		
		return (T) response;
	}
	
	@Override
	public void close() {
		lock.lock();
		try {
			condition.signal();
		} finally {
			lock.unlock();
		}

		running.compareAndSet(true, false);
		if (! future.cancel(true)) {
			System.out.println("Task could not be cancelled");
		}
		
		if (is != null) { try { is.close(); is = null; } catch (IOException e) { } }
		if (os != null) { try { os.close(); os = null; } catch (IOException e) { } }
		
		executor.shutdown();
	}
	
	@Override
	public void run() {
		if (! running.compareAndSet(false, true)) {
			System.out.println("Already running?");
			return;
		}
		
		try {
			while (running.get()) {
				int i = is.read();
				if (i == -1) {
					running.compareAndSet(true, false);
					break;
				}
				
				byte b = (byte) i;
				if (b == START_SYSEX) {
					lock.lock();
					try {
						responseQueue.add(readSysEx());
						condition.signal();
					} finally {
						lock.unlock();
					}
				} else if (b == PROTOCOL_VERSION) {
					lock.lock();
					try {
						responseQueue.add(readVersionResponse());
						condition.signal();
					} finally {
						lock.unlock();
					}
				} else if (b >= DIGITAL_IO_START && b <= DIGITAL_IO_END) {
					DataResponse response = readDataResponse(b - DIGITAL_IO_START);
					System.out.println("Digital I/O response: " + response);
				} else if (b >= ANALOG_IO_START && b <= ANALOG_IO_END) {
					DataResponse response = readDataResponse(b - ANALOG_IO_START);
					System.out.println("Analog I/O response: " + response);
				} else {
					System.out.println("Unrecognised response: " + b);
				}
			}
		} catch (IOException e) {
			running.compareAndSet(true, false);
			System.out.println("Error: " + e);
		}
		
		System.out.println("Thread: done");
	}

	private SysExResponse readSysEx() throws IOException {
		byte sysex_cmd = readByte();
		long start = System.currentTimeMillis();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		while (true) {
			byte b = readByte();
			if (b == END_SYSEX) {
				break;
			}
			baos.write(b);
		}
		ByteBuffer buffer = ByteBuffer.wrap(baos.toByteArray());
		long duration = System.currentTimeMillis() - start;
		System.out.println("Took " + duration + "ms to read the SysEx message");
		
		SysExResponse response = null;
		switch (sysex_cmd) {
		case REPORT_FIRMWARE:
			byte major = buffer.get();
			byte minor = buffer.get();
			
			byte[] name_buffer = new byte[buffer.remaining()];
			buffer.get(name_buffer);
			String name = new String(name_buffer, Charset.forName("UTF-16LE"));
			
			response = new FirmwareDetails(major, minor, name);
			break;
		case CAPABILITY_RESPONSE:
			List<List<PinCapability>> capabilities = new ArrayList<>();
			List<PinCapability> pin_capabilities = new ArrayList<>();
			while (buffer.remaining() > 0) {
				while (true) {
					byte b = buffer.get();
					if (b == 0x7f) {
						break;
					}
					PinMode mode = PinMode.valueOf(b);
					byte resolution = buffer.get();
					pin_capabilities.add(new PinCapability(mode, resolution));
				}

				capabilities.add(Collections.unmodifiableList(pin_capabilities));
				pin_capabilities = new ArrayList<>();
			}
			response = new CapabilityResponse(capabilities);
			break;
		case PIN_STATE_RESPONSE:
			byte pin = buffer.get();
			PinMode pin_mode = PinMode.valueOf(buffer.get());
			byte[] pin_state = new byte[buffer.remaining()];
			buffer.get(pin_state);
			response = new PinState(pin, pin_mode, convertToValue(pin_state));
			break;
		case ANALOG_MAPPING_RESPONSE:
			byte[] channels = new byte[buffer.remaining()];
			buffer.get(channels);
			response = new AnalogMappingResponse(channels);
			break;
		case I2C_REPLY:
			int slave_address = convertToValue(buffer.get(), buffer.get());
			int register = convertToValue(buffer.get(), buffer.get());
			byte[] data = new byte[buffer.remaining() / 2];
			for (int i=0; i<data.length; i++) {
				data[i] = (byte) convertToValue(buffer.get(), buffer.get());
			}
			buffer.get(data);
			response = new I2CResponse(slave_address, register, data);
			break;
		default:
			System.out.format("Unhandled sysex command: 0x%x%n", Byte.valueOf(sysex_cmd));
		}
		
		return response;
	}
	
	private ProtocolVersion readVersionResponse() throws IOException {
		return new ProtocolVersion(readByte(), readByte());
	}
	
	private DataResponse readDataResponse(int port) throws IOException {
		return new DataResponse(port, readShort());
	}

	private byte readByte() throws IOException {
		int i = is.read();
		if (i == -1) {
			throw new IOException("End of stream unexpected");
		}
		return (byte) i;
	}
	
	private int readShort() throws IOException {
		// LSB (bits 0-6), MSB(bits 7-13)
		return convertToValue(readByte(), readByte());
		//return ((msb & 0x7f) << 7) | (lsb & 0x7f);		
	}
	
	static byte[] convertToLsbMsb(int value) {
		return new byte[] { (byte) (value & 0x7f), (byte) ((value >> 7) & 0x7f) };
	}
	
	static byte[] convertToBytes(int value) {
		int num_bytes;
		if (value < 128 || value >= 268435456) { // 2^7 or greater than max val (2^28)
			num_bytes = 1;
		} else if (value < 16384) { // 2^14
			num_bytes = 2;
		} else if (value < 2097152) { // 2^21
			num_bytes = 3;
		} else {
			num_bytes = 4;
		}
		byte[] bytes = new byte[num_bytes];
		for (int i=0; i<num_bytes; i++) {
			bytes[i] = (byte) ((value >> (i*7)) & 0x7f);
		}
		return bytes;
	}
	
	static int convertToValue(byte... values) {
		int value = 0;
		for (int i=0; i<values.length; i++) {
			value |= ((values[i] & 0x7f) << (i*7));
		}
		return value;
	}
	
	/*
	 * slave address (MSB) + read/write and address mode bits
	 * {bit 7: always 0}
	 * {bit 6: auto restart transmission, 0 = stop (default), 1 = restart}
	 * {bit 5: address mode, 1 = 10-bit mode}
	 * {bits 4-3: read/write, 00 = write, 01 = read once, 10 = read continuously, 11 = stop reading}
	 * {bits 2-0: slave address MSB in 10-bit mode, not used in 7-bit mode}
	 */
	static byte[] convertToI2CSlaveAddressLsbMsb(int slaveAddress, boolean autoRestart, boolean addressSize10Bit,
			I2CMode mode) {
		byte[] lsb_msb = convertToLsbMsb(slaveAddress);
		
		lsb_msb[1] &= 0x7;
		if (autoRestart) {
			lsb_msb[1] |= 0x40;
		}
		if (addressSize10Bit) {
			lsb_msb[1] |= 0x20;
		}
		lsb_msb[1] |= (mode.ordinal() << 3);
		
		return lsb_msb;
	}
	
	static class ResponseMessage {
	}
	
	static class SysExResponse extends ResponseMessage {
	}
	
	static class FirmwareDetails extends SysExResponse {
		private byte major;
		private byte minor;
		private String name;
		
		FirmwareDetails(byte major, byte minor, String name) {
			this.major = major;
			this.minor = minor;
			this.name = name;
		}

		public byte getMajor() {
			return major;
		}

		public byte getMinor() {
			return minor;
		}

		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			return "FirmwareResponse [major=" + major + ", minor=" + minor + ", name=" + name + "]";
		}
	}
	
	static class CapabilityResponse extends SysExResponse {
		private List<List<PinCapability>> capabilities;
		
		CapabilityResponse(List<List<PinCapability>> capabilities) {
			this.capabilities = capabilities;
		}

		public List<List<PinCapability>> getCapabilities() {
			return capabilities;
		}

		@Override
		public String toString() {
			return "CapabilityResponse [capabilities=" + capabilities + "]";
		}
	}
	
	/**
	 * The pin state is any data written to the pin (it is important to note that
	 * pin state != pin value). For output modes (digital output, PWM, and Servo),
	 * the state is any value that has been previously written to the pin. For input
	 * modes, typically the state is zero. However, for digital inputs, the state is
	 * the status of the pull-up resistor which is 1 if enabled, 0 if disabled.
	 * 
	 * The pin state query can also be used as a verification after sending pin modes
	 * or data messages.
	 */
	static class PinState extends SysExResponse {
		private byte pin;
		private PinMode mode;
		private int state;

		public PinState(byte pin, PinMode mode, int state) {
			this.pin = pin;
			this.mode = mode;
			this.state = state;
		}

		public byte getPin() {
			return pin;
		}

		public PinMode getMode() {
			return mode;
		}

		public int getState() {
			return state;
		}

		@Override
		public String toString() {
			return "PinStateResponse [pin=" + pin + ", mode=" + mode + ", state=" + state + "]";
		}
	}
	
	static class ProtocolVersion extends ResponseMessage {
		private byte major;
		private byte minor;
		
		ProtocolVersion(byte major, byte minor) {
			this.major = major;
			this.minor = minor;
		}

		public byte getMajor() {
			return major;
		}

		public byte getMinor() {
			return minor;
		}

		@Override
		public String toString() {
			return "VersionResponse [major=" + major + ", minor=" + minor + "]";
		}
	}
	
	static class DataResponse extends ResponseMessage {
		private int port;
		private int values;
		
		DataResponse(int port, int values) {
			this.port = port;
			// LSB (pins 0-6 bit-mask), MSB (pin 7 bit-mask)
			this.values = values;
		}

		public int getPort() {
			return port;
		}

		public int getValues() {
			return values;
		}

		@Override
		public String toString() {
			return String.format("DigitalIOResponse [port=%d, values=0x%x]", Integer.valueOf(port), Integer.valueOf(values));
		}
	}
	
	static class AnalogMappingResponse extends SysExResponse {
		private byte[] channels;
		
		public AnalogMappingResponse(byte[] channels) {
			this.channels = channels;
		}
		
		public byte[] getChannels() {
			return channels;
		}

		@Override
		public String toString() {
			return "AnalogMappingResponse [channels=" + Arrays.toString(channels) + "]";
		}
	}
	
	static class Pin {
		private int gpio;
		// List of pin mode and resolution pairs
		private List<PinCapability> capabilities;
		private PinMode mode = PinMode.UNKNOWN;
		private int value;
		
		public Pin(int gpio) {
			this.gpio = gpio;
		}
		
		public int getGpio() {
			return gpio;
		}
		
		public List<PinCapability> getCapabilities() {
			return capabilities;
		}
		
		public void setCapabilities(List<PinCapability> capabilities) {
			this.capabilities = capabilities;
		}
		
		public PinMode getMode() {
			return mode;
		}
		
		public void setMode(PinMode mode) {
			this.mode = mode;
		}
		
		public int getValue() {
			return value;
		}
		
		public void setValue(int value) {
			this.value = value;
		}
		
		public void setValue(boolean value) {
			this.value = value ? 1 : 0;
		}
	}
	
	static enum I2CMode {
		WRITE, READ_ONCE, READ_CONTINUOUSLY, STOP_READING;
	}
	
	static class I2CResponse extends SysExResponse {
		private int slaveAddress;
		private int register;
		private byte[] data;
		
		public I2CResponse(int slaveAddress, int register, byte[] data) {
			this.slaveAddress = slaveAddress;
			this.register = register;
			this.data = data;
		}

		public int getSlaveAddress() {
			return slaveAddress;
		}

		public int getRegister() {
			return register;
		}

		public byte[] getData() {
			return data;
		}
	}
}
