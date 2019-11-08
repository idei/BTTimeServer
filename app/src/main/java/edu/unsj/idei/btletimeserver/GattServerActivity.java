package edu.unsj.idei.btletimeserver;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class GattServerActivity extends Activity implements SensorEventListener
{
	private static final String TAG = GattServerActivity.class.getSimpleName();

	/* Local UI */
	private TextView mLocalTimeView;
	private TextView RotationView;
	/* Bluetooth API */
	private BluetoothManager mBluetoothManager;
	private BluetoothGattServer mBluetoothGattServer;
	private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
	/* Collection of notification subscribers */
	private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();

	private SensorManager sensorManager;
	private final float[] accelerometerReading = new float[3];
	private final float[] magnetometerReading = new float[3];

	private final float[] rotationMatrix = new float[9];
	private final float[] orientationAngles = new float[3];

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.another_activity);

		mLocalTimeView = findViewById(R.id.text_time);
		RotationView = findViewById(R.id.rotation);

		// Devices with a display should not go to sleep
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
		assert mBluetoothManager != null;
		BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
		// We can't continue without proper Bluetooth support
		if (!checkBluetoothSupport(bluetoothAdapter))
		{
			finish();
		}

		// Register for system Bluetooth events
		IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
		registerReceiver(mBluetoothReceiver, filter);
		if (!bluetoothAdapter.isEnabled())
		{
			Log.d(TAG, "Bluetooth is currently disabled...enabling");
			bluetoothAdapter.enable();
		} else
		{
			Log.d(TAG, "Bluetooth enabled...starting services");
			startAdvertising();
			startServer();
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		// Get updates from the accelerometer and magnetometer at a constant rate.
		// To make batch operations more efficient and reduce power consumption,
		// provide support for delaying updates to the application.
		//
		// In this example, the sensor reporting delay is small enough such that
		// the application receives an update before the system checks the sensor
		// readings again.
		Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if (accelerometer != null)
		{
			sensorManager.registerListener(this, accelerometer,
					SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
		}
		Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		if (magneticField != null)
		{
			sensorManager.registerListener(this, magneticField,
					SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();

		// Don't receive any more updates from either sensor.
		sensorManager.unregisterListener(this);
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		// Register for system clock events
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_TIME_TICK);
		filter.addAction(Intent.ACTION_TIME_CHANGED);
		filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
		registerReceiver(mTimeReceiver, filter);
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		unregisterReceiver(mTimeReceiver);
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
		if (bluetoothAdapter.isEnabled())
		{
			stopServer();
			stopAdvertising();
		}

		unregisterReceiver(mBluetoothReceiver);
	}

	/**
	 * Verify the level of Bluetooth support provided by the hardware.
	 *
	 * @param bluetoothAdapter System {@link BluetoothAdapter}.
	 * @return true if Bluetooth is properly supported, false otherwise.
	 */
	private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter)
	{

		if (bluetoothAdapter == null)
		{
			Log.w(TAG, "Bluetooth is not supported");
			return false;
		}

		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
		{
			Log.w(TAG, "Bluetooth LE is not supported");
			return false;
		}

		return true;
	}

	/**
	 * Listens for system time changes and triggers a notification to
	 * Bluetooth subscribers.
	 */
	private BroadcastReceiver mTimeReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			/*
			byte adjustReason;
			switch (Objects.requireNonNull(intent.getAction()))
			{
				case Intent.ACTION_TIME_CHANGED:
					adjustReason = TimeProfile.ADJUST_MANUAL;
					break;
				case Intent.ACTION_TIMEZONE_CHANGED:
					adjustReason = TimeProfile.ADJUST_TIMEZONE;
					break;
				default:
				case Intent.ACTION_TIME_TICK:
					adjustReason = TimeProfile.ADJUST_NONE;
					break;
			}
			 */
			long now = System.currentTimeMillis();
			// notifyRegisteredDevices(now, adjustReason);
			updateLocalUi(now);
		}
	};

	/**
	 * Listens for Bluetooth adapter events to enable/disable
	 * advertising and server functionality.
	 */
	private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

			switch (state)
			{
				case BluetoothAdapter.STATE_ON:
					Log.w(TAG, "Bluetooth ON");
					startAdvertising();
					startServer();
					break;
				case BluetoothAdapter.STATE_OFF:
					Log.w(TAG, "Bluetooth OFF");
					stopServer();
					stopAdvertising();
					break;
				default:
					// Do nothing
			}

		}
	};

	/**
	 * Begin advertising over Bluetooth that this device is connectable
	 * and supports the Current Time Service.
	 */
	private void startAdvertising()
	{
		BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
		mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
		if (mBluetoothLeAdvertiser == null)
		{
			Log.w(TAG, "Failed to create advertiser");
			return;
		}

		AdvertiseSettings settings = new AdvertiseSettings.Builder()
				.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
				.setConnectable(true)
				.setTimeout(0)
				.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
				.build();

		AdvertiseData data = new AdvertiseData.Builder()
				.setIncludeDeviceName(true)
				.setIncludeTxPowerLevel(true)
				//.addServiceUuid(new ParcelUuid(TimeProfile.TIME_SERVICE))
				.addServiceUuid(new ParcelUuid(TimeProfile.ORIENTATION_SERVICE))
				.build();

		mBluetoothLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);
	}

	/**
	 * Stop Bluetooth advertisements.
	 */
	private void stopAdvertising()
	{
		if (mBluetoothLeAdvertiser == null) return;

		mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
	}

	/**
	 * Initialize the GATT server instance with the services/characteristics
	 * from the Time Profile.
	 */
	private void startServer()
	{
		mBluetoothGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
		if (mBluetoothGattServer == null)
		{
			Log.w(TAG, "Unable to create GATT server");
			return;
		}

		//mBluetoothGattServer.addService(TimeProfile.createTimeService());
		mBluetoothGattServer.addService(TimeProfile.createOrientationService());

		// Initialize the local UI
		updateLocalUi(System.currentTimeMillis());
	}

	/**
	 * Shut down the GATT server.
	 */
	private void stopServer()
	{
		if (mBluetoothGattServer == null) return;

		mBluetoothGattServer.close();
	}

	/**
	 * Callback to receive information about the advertisement process.
	 */
	private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback()
	{
		@Override
		public void onStartSuccess(AdvertiseSettings settingsInEffect)
		{
			Log.i(TAG, "LE Advertise Started.");
		}

		@Override
		public void onStartFailure(int errorCode)
		{
			Log.w(TAG, "LE Advertise Failed: " + errorCode);
		}
	};

	/*
	 * Send a time service notification to any devices that are subscribed
	 * to the characteristic.
	 */
	/*
	private void notifyRegisteredDevices(long timestamp, byte adjustReason)
	{
		if (mRegisteredDevices.isEmpty())
		{
			Log.i(TAG, "No subscribers registered");
			return;
		}
		byte[] exactTime = TimeProfile.getExactTime(timestamp, adjustReason);

		Log.i(TAG, "Sending update to " + mRegisteredDevices.size() + " subscribers");
		for (BluetoothDevice device : mRegisteredDevices)
		{
			//BluetoothGattCharacteristic timeCharacteristic = mBluetoothGattServer
			//		.getService(TimeProfile.TIME_SERVICE)
			//		.getCharacteristic(TimeProfile.CURRENT_TIME);
			BluetoothGattCharacteristic timeCharacteristic = mBluetoothGattServer
					.getService(TimeProfile.ORIENTATION_SERVICE)
					.getCharacteristic(TimeProfile.ORIENTATION_DATA);
			timeCharacteristic.setValue(exactTime);
			mBluetoothGattServer.notifyCharacteristicChanged(device, timeCharacteristic, false);
		}
	}
	*/

	/**
	 * Update graphical UI on devices that support it with the current time.
	 */
	private void updateLocalUi(long timestamp)
	{
		Date date = new Date(timestamp);
		String displayDate = DateFormat.getMediumDateFormat(this).format(date)
				+ "\n"
				+ DateFormat.getTimeFormat(this).format(date);
		mLocalTimeView.setText(displayDate);
	}

	/**
	 * Callback to handle incoming requests to the GATT server.
	 * All read/write requests for characteristics and descriptors are handled here.
	 */
	private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback()
	{
		@Override
		public void onConnectionStateChange(BluetoothDevice device, int status, int newState)
		{
			if (newState == BluetoothProfile.STATE_CONNECTED)
			{
				Log.i(TAG, "BluetoothDevice CONNECTED: " + device);
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED)
			{
				Log.i(TAG, "BluetoothDevice DISCONNECTED: " + device);
				//Remove device from any active subscriptions
				mRegisteredDevices.remove(device);
			}
		}

		@Override
		public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
		                                        BluetoothGattCharacteristic characteristic)
		{
			/*
			long now = System.currentTimeMillis();
			if (TimeProfile.CURRENT_TIME.equals(characteristic.getUuid()))
			{
				Log.i(TAG, "Read CurrentTime");
				mBluetoothGattServer.sendResponse(device,
						requestId,
						BluetoothGatt.GATT_SUCCESS,
						0,
						TimeProfile.getExactTime(now, TimeProfile.ADJUST_NONE));
			} else if (TimeProfile.LOCAL_TIME_INFO.equals(characteristic.getUuid()))
			{
				Log.i(TAG, "Read LocalTimeInfo");
				mBluetoothGattServer.sendResponse(device,
						requestId,
						BluetoothGatt.GATT_SUCCESS,
						0,
						TimeProfile.getLocalTimeInfo(now));
			} else
			 */
			if (TimeProfile.ORIENTATION_DATA.equals(characteristic.getUuid()))
			{
				Log.i(TAG, "Read device orientation");
				mBluetoothGattServer.sendResponse(device,
						requestId,
						BluetoothGatt.GATT_SUCCESS,
						0,
						TimeProfile.getDeviceOrientation(new short[6]));
			} else
			{
				// Invalid characteristic
				Log.w(TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
				mBluetoothGattServer.sendResponse(device,
						requestId,
						BluetoothGatt.GATT_FAILURE,
						0,
						null);
			}
		}

		@Override
		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
		                                    BluetoothGattDescriptor descriptor)
		{
			if (TimeProfile.CLIENT_CONFIG.equals(descriptor.getUuid()))
			{
				Log.d(TAG, "Config descriptor read");
				byte[] returnValue;
				if (mRegisteredDevices.contains(device))
				{
					returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
				} else
				{
					returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
				}
				mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0,
						returnValue);
			} else
			{
				Log.w(TAG, "Unknown descriptor read request");
				mBluetoothGattServer.sendResponse(device,
						requestId,
						BluetoothGatt.GATT_FAILURE,
						0,
						null);
			}
		}

		@Override
		public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
		                                     BluetoothGattDescriptor descriptor,
		                                     boolean preparedWrite, boolean responseNeeded,
		                                     int offset, byte[] value)
		{
			if (TimeProfile.CLIENT_CONFIG.equals(descriptor.getUuid()))
			{
				if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value))
				{
					Log.d(TAG, "Subscribe device to notifications: " + device);
					mRegisteredDevices.add(device);
				} else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value))
				{
					Log.d(TAG, "Unsubscribe device from notifications: " + device);
					mRegisteredDevices.remove(device);
				}

				if (responseNeeded)
				{
					mBluetoothGattServer.sendResponse(device,
							requestId,
							BluetoothGatt.GATT_SUCCESS,
							0,
							null);
				}
			} else
			{
				Log.w(TAG, "Unknown descriptor write request");
				if (responseNeeded)
				{
					mBluetoothGattServer.sendResponse(device,
							requestId,
							BluetoothGatt.GATT_FAILURE,
							0,
							null);
				}
			}
		}
	};

	private long curTime;
	private short[] sumOrientation = new short[3];
	private short sumOrientationQty = 0;

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
		{
			System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.length);
		} else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
		{
			System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.length);
		}

		/*
		 * Compute the three orientation angles based on the most recent readings from the device's
		 * accelerometer and magnetometer.
		 */
		// Update rotation matrix, which is needed to update orientation angles.
		SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
		// "mRotationMatrix" now has up-to-date information.
		SensorManager.getOrientation(rotationMatrix, orientationAngles);
		// "mOrientationAngles" now has up-to-date information.

		sumOrientation[0] += convert(orientationAngles[0]);
		sumOrientation[1] += convert(orientationAngles[1]);
		sumOrientation[2] += convert(orientationAngles[2]);
		sumOrientationQty++;

		long cur = System.currentTimeMillis();

		if ((cur - curTime) > 500)
		{
			curTime = cur;

			short[] orientation = new short[3];
			orientation[0] = (short) (sumOrientation[0] / sumOrientationQty);
			orientation[1] = (short) (sumOrientation[1] / sumOrientationQty);
			orientation[2] = (short) (sumOrientation[1] / sumOrientationQty);
			sumOrientationQty = 0;
			sumOrientation[0] = 0;
			sumOrientation[1] = 0;
			sumOrientation[2] = 0;

			String info = String.format(Locale.getDefault(), "Sent (%3d,%3d,%3d) to %d subscribers",
					orientation[0], orientation[1], orientation[2], mRegisteredDevices.size());

			//Log.i(TAG, info);
			RotationView.setText(info);

			if (mRegisteredDevices.size() > 0)
			{
				Log.i(TAG, "Sending " + info + " to " + mRegisteredDevices.size() + " subscribers");
			}
			for (BluetoothDevice device : mRegisteredDevices)
			{
				BluetoothGattCharacteristic timeCharacteristic = mBluetoothGattServer
						.getService(TimeProfile.ORIENTATION_SERVICE)
						.getCharacteristic(TimeProfile.ORIENTATION_DATA);
				timeCharacteristic.setValue(TimeProfile.getDeviceOrientation(orientation));
				mBluetoothGattServer.notifyCharacteristicChanged(device, timeCharacteristic, false);
			}
		}
	}

	private short convert(float value)
	{
		double deg = Math.ceil(Math.toDegrees(value));
		if (deg < 0)
		{
			deg += 360;
		}

		return (short) Math.abs(deg);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{

	}
}
