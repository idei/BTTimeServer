/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.unsj.idei.btletimeserver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.UUID;

/**
 * Implementation of the Bluetooth GATT Time Profile.
 * https://www.bluetooth.com/specifications/adopted-specifications
 */
@SuppressWarnings("unused")
public class TimeProfile
{
	private static final String TAG = TimeProfile.class.getSimpleName();

	/* Current Time Service UUID */
	static UUID TIME_SERVICE = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
	/* Mandatory Current Time Information Characteristic */
	static UUID CURRENT_TIME = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb");
	/* Optional Local Time Information Characteristic */
	static UUID LOCAL_TIME_INFO = UUID.fromString("00002a0f-0000-1000-8000-00805f9b34fb");
	/* Mandatory Client Characteristic Config Descriptor */
	static UUID CLIENT_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	static UUID ORIENTATION_SERVICE = UUID.fromString("00001821-0000-1000-8000-00805f9b34fb");
	static UUID ORIENTATION_DATA = UUID.fromString("A0E8C677-B42B-43FB-9710-8AC0AB24B3BB");
	//static UUID ORIENTATION_CONFIG = UUID.fromString("C9B63447-94C8-4B0B-89F2-71F19F37D6FA");

	// Adjustment Flags
	static final byte ADJUST_NONE = 0x0;
	static final byte ADJUST_MANUAL = 0x1;
	public static final byte ADJUST_EXTERNAL = 0x2;
	static final byte ADJUST_TIMEZONE = 0x4;
	public static final byte ADJUST_DST = 0x8;

	/*
	 * Return a configured {@link BluetoothGattService} instance for the
	 * Current Time Service.
	 */

	static BluetoothGattService createTimeService()
	{
		// Start a new service
		BluetoothGattService service = new BluetoothGattService(
				TIME_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);

		// Configure the Current Time characteristic as Read-only and for supporting notifications
		BluetoothGattCharacteristic currentTime = new BluetoothGattCharacteristic(
				CURRENT_TIME,
				BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
				BluetoothGattCharacteristic.PERMISSION_READ);

		// Describe the current time characteristic with a Read/write descriptor
		BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(
				CLIENT_CONFIG,
				BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
		currentTime.addDescriptor(configDescriptor);

		// Configure the Local Time Information characteristic as read-only
		BluetoothGattCharacteristic localTime = new BluetoothGattCharacteristic(
				LOCAL_TIME_INFO,
				BluetoothGattCharacteristic.PROPERTY_READ,
				BluetoothGattCharacteristic.PERMISSION_READ);

		service.addCharacteristic(currentTime);
		service.addCharacteristic(localTime);

		return service;
	}


	/**
	 * Return a configured {@link BluetoothGattService} instance for the Device Orientation.
	 *
	 * @return The new orientation service.
	 */
	static BluetoothGattService createOrientationService()
	{
		BluetoothGattService service = new BluetoothGattService(ORIENTATION_SERVICE,
				BluetoothGattService.SERVICE_TYPE_PRIMARY);

		// Current Orientation characteristic
		BluetoothGattCharacteristic curOrientation = new BluetoothGattCharacteristic(ORIENTATION_DATA,
				//Read-only characteristic, supports notifications
				BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
				BluetoothGattCharacteristic.PERMISSION_READ);
		BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
				//Read/write descriptor
				BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
		curOrientation.addDescriptor(configDescriptor);
		service.addCharacteristic(curOrientation);
		return service;
	}

	static byte[] getDeviceOrientation(short[] orientation)
	{
		ByteBuffer buffer = ByteBuffer.allocate(6);
		buffer.putShort(orientation[0]);
		buffer.putShort(orientation[1]);
		buffer.putShort(orientation[2]);
		return buffer.array();
	}

	/**
	 * Construct the field values for a Current Time characteristic
	 * from the given epoch timestamp and adjustment reason.
	 */
	static byte[] getExactTime(long timestamp, byte adjustReason)
	{
		Calendar time = Calendar.getInstance();
		time.setTimeInMillis(timestamp);

		byte[] field = new byte[10];

		// Year
		int year = time.get(Calendar.YEAR);
		field[0] = (byte) (year & 0xFF);
		field[1] = (byte) ((year >> 8) & 0xFF);
		// Month
		field[2] = (byte) (time.get(Calendar.MONTH) + 1);
		// Day
		field[3] = (byte) time.get(Calendar.DATE);
		// Hours
		field[4] = (byte) time.get(Calendar.HOUR_OF_DAY);
		// Minutes
		field[5] = (byte) time.get(Calendar.MINUTE);
		// Seconds
		field[6] = (byte) time.get(Calendar.SECOND);
		// Day of Week (1-7)
		field[7] = getDayOfWeekCode(time.get(Calendar.DAY_OF_WEEK));
		// Fractions256
		field[8] = (byte) (time.get(Calendar.MILLISECOND) / 256);

		field[9] = adjustReason;

		return field;
	}

	/* Time bucket constants for local time information */
	private static final int FIFTEEN_MINUTE_MILLIS = 900000;
	private static final int HALF_HOUR_MILLIS = 1800000;

	/**
	 * Construct the field values for a Local Time Information characteristic
	 * from the given epoch timestamp.
	 */
	static byte[] getLocalTimeInfo(long timestamp)
	{
		Calendar time = Calendar.getInstance();
		time.setTimeInMillis(timestamp);

		byte[] field = new byte[2];

		// Time zone
		int zoneOffset = time.get(Calendar.ZONE_OFFSET) / FIFTEEN_MINUTE_MILLIS; // 15 minute intervals
		field[0] = (byte) zoneOffset;

		// DST Offset
		int dstOffset = time.get(Calendar.DST_OFFSET) / HALF_HOUR_MILLIS; // 30 minute intervals
		field[1] = getDstOffsetCode(dstOffset);

		return field;
	}

	/* Bluetooth Weekday Codes */
	private static final byte DAY_UNKNOWN = 0;
	private static final byte DAY_MONDAY = 1;
	private static final byte DAY_TUESDAY = 2;
	private static final byte DAY_WEDNESDAY = 3;
	private static final byte DAY_THURSDAY = 4;
	private static final byte DAY_FRIDAY = 5;
	private static final byte DAY_SATURDAY = 6;
	private static final byte DAY_SUNDAY = 7;

	/**
	 * Convert a {@link Calendar} weekday value to the corresponding
	 * Bluetooth weekday code.
	 */
	private static byte getDayOfWeekCode(int dayOfWeek)
	{
		switch (dayOfWeek)
		{
			case Calendar.MONDAY:
				return DAY_MONDAY;
			case Calendar.TUESDAY:
				return DAY_TUESDAY;
			case Calendar.WEDNESDAY:
				return DAY_WEDNESDAY;
			case Calendar.THURSDAY:
				return DAY_THURSDAY;
			case Calendar.FRIDAY:
				return DAY_FRIDAY;
			case Calendar.SATURDAY:
				return DAY_SATURDAY;
			case Calendar.SUNDAY:
				return DAY_SUNDAY;
			default:
				return DAY_UNKNOWN;
		}
	}

	/* Bluetooth DST Offset Codes */
	private static final byte DST_STANDARD = 0x0;
	private static final byte DST_HALF = 0x2;
	private static final byte DST_SINGLE = 0x4;
	private static final byte DST_DOUBLE = 0x8;
	private static final byte DST_UNKNOWN = (byte) 0xFF;

	/**
	 * Convert a raw DST offset (in 30 minute intervals) to the
	 * corresponding Bluetooth DST offset code.
	 */
	private static byte getDstOffsetCode(int rawOffset)
	{
		switch (rawOffset)
		{
			case 0:
				return DST_STANDARD;
			case 1:
				return DST_HALF;
			case 2:
				return DST_SINGLE;
			case 4:
				return DST_DOUBLE;
			default:
				return DST_UNKNOWN;
		}
	}
}
