package edu.unsj.idei.btletimeserver;

import android.Manifest;
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
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class NearestPeersActivity extends AppCompatActivity implements PeerFragment.OnFragmentInteractionListener
{
	private static final String TAG = NearestPeersActivity.class.getSimpleName();
	/* Bluetooth API */
	private BluetoothManager mBluetoothManager;
	private BluetoothGattServer mBluetoothGattServer;
	private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
	private BluetoothAdapter mBluetoothAdapter;
	private boolean mScanning;
	private Handler mHandler;
	/* Collection of notification subscribers */
	private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();
	private static final int REQUEST_ENABLE_BT = 1;
	// Stops scanning after 10 seconds.
	private static final long SCAN_PERIOD = 120000;
	private LinearLayout peer_list;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		// Devices with a display should not go to sleep
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mHandler = new Handler();

		setContentView(R.layout.activity_nearest_peers);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		FloatingActionButton fab = findViewById(R.id.fab);
		fab.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
						.setAction("Action", null).show();
			}
		});

		// Use this check to determine whether BLE is supported on the device.  Then you can
		// selectively disable BLE-related features.
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
		{
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
			finish();
		}

		int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
		if (permissionCheck != PackageManager.PERMISSION_GRANTED)
		{
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION))
			{
				Toast.makeText(this, R.string.location_permission_required, Toast.LENGTH_SHORT).show();
			} else
			{
				requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
						Manifest.permission.ACCESS_FINE_LOCATION}, 1);
			}
		} else
		{
			Toast.makeText(this, R.string.location_permission_granted, Toast.LENGTH_SHORT).show();
		}

		mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);

		// Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager =
				(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		assert bluetoothManager != null;
		mBluetoothAdapter = bluetoothManager.getAdapter();

		// Checks if Bluetooth is supported on the device.
		if (mBluetoothAdapter == null)
		{
			Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
			finish();
		}

		scanLeDevice(true);
		startAdvertising();
		startServer();
	}

	private ArrayList<String> devices = new ArrayList<>();

	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback =
			new BluetoothAdapter.LeScanCallback()
			{

				@Override
				public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord)
				{
					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							String peerName = device.getName();
							String peerAddress = device.getAddress();

							if (peerName != null && !devices.contains(peerName))
							{
								if (device.getUuids() != null)
								{
									for (ParcelUuid uuids : device.getUuids())
									{
										Log.i(TAG, uuids.getUuid().toString());
									}
								}
								devices.add(peerName);
								fill(peerName);
							}
						}
					});
				}
			};

	private void scanLeDevice(final boolean enable)
	{
		if (enable)
		{
			// Stops scanning after a pre-defined scan period.
			mHandler.postDelayed(new Runnable()
			{
				@Override
				public void run()
				{
					mScanning = false;
					mBluetoothAdapter.stopLeScan(mLeScanCallback);
				}
			}, SCAN_PERIOD);

			mScanning = true;
			mBluetoothAdapter.startLeScan(mLeScanCallback);
			Log.i(TAG, "inicia");
		} else
		{
			mScanning = false;
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
		}
	}

	private void fill(String peerName)
	{
		peer_list = findViewById(R.id.list);
		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentTransaction transaction = fragmentManager.beginTransaction();
		PeerFragment peerFragment = PeerFragment.newInstance(peerName, "b");
		transaction.add(R.id.list, peerFragment);
		transaction.commit();
	}

	@Override
	public void onFragmentInteraction(Uri uri)
	{

	}

	/**
	 * Begin advertising over Bluetooth that this device is connectable and supports the Message
	 * Service.
	 */
	private void startAdvertising()
	{
		String userName = getString(R.string.user_prefix, new Random().nextInt(100));
		BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
		bluetoothAdapter.setName(userName);
		TextView userNameView = findViewById(R.id.user_name);
		userNameView.setText(userName);
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
				.addServiceUuid(new ParcelUuid(ChatProfile.MESSAGE_SERVICE))
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

		// mBluetoothGattServer.addService(TimeProfile.createOrientationService());
		mBluetoothGattServer.addService(ChatProfile.createChatService());
		// Initialize the local UI
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
				Log.i(TAG, "Device CONNECTED: " + device);
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED)
			{
				Log.i(TAG, "Device DISCONNECTED: " + device);
				//Remove device from any active subscriptions
				mRegisteredDevices.remove(device);
			}
		}

		@Override
		public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
		                                         BluetoothGattCharacteristic characteristic,
		                                         boolean preparedWrite, boolean responseNeeded,
		                                         int offset, byte[] value)
		{
			String txt = new String(value);

			if (ChatProfile.PRIVATE_MESSAGE_CHARACTERISTIC.equals(characteristic.getUuid()))
			{
				Log.i(TAG, String.format("Private from %s: '%s' (%d)", device.getAddress(), txt, requestId));
			} else
			{
				if (mRegisteredDevices.size() > 0)
				{
					Log.i(TAG, String.format("Broadcast from %s: '%s' (%d)", device.getAddress(), txt, requestId));
				} else
				{
					Log.i(TAG, "No registered listeners");
				}
				for (BluetoothDevice dev : mRegisteredDevices)
				{
					BluetoothGattCharacteristic broadcastCharacteristic = mBluetoothGattServer
							.getService(ChatProfile.MESSAGE_SERVICE)
							.getCharacteristic(ChatProfile.NOTIFICATION_MESSAGE_CHARACTERISTIC);
					broadcastCharacteristic.setValue(value);
					mBluetoothGattServer.notifyCharacteristicChanged(dev, broadcastCharacteristic, false);
				}
			}

			if (responseNeeded)
			{
				mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
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
			*/
		}

		@Override
		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
		                                    BluetoothGattDescriptor descriptor)
		{
			if (ChatProfile.CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR.equals(descriptor.getUuid()))
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
			if (ChatProfile.CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR.equals(descriptor.getUuid()))
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

}
