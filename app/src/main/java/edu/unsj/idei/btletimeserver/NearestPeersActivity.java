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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class NearestPeersActivity extends AppCompatActivity implements PeerFragment.OnFragmentInteractionListener
{
	public static final String TAG = NearestPeersActivity.class.getSimpleName();
	/* Bluetooth API */
	private BluetoothManager mBluetoothManager;
	private BluetoothGattServer mBluetoothGattServer;
	private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
	/* Collection of notification subscribers */
	private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();
	// Stops scanning after 10 seconds.

	private HashMap<String, PeerFragment> _peersView = new HashMap<>();

	private NearestScanner scanner;

	private NearestScanner.NearestCallback _nearestCallback = new NearestScanner.NearestCallback()
	{
		@Override
		void found(NearestScanner.BTLeDevice device)
		{
			// Log.w(TAG, "Found " + device.getName() + " " + device.getTxPower());
			addView(device);
		}

		@Override
		void lost(NearestScanner.BTLeDevice device)
		{
			// Log.w(TAG, "Lost " + device.getName());
			removeView(device);
		}

		@Override
		void update(NearestScanner.BTLeDevice device)
		{
			updateView(device);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		// Devices with a display should not go to sleep
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
		// Register for system Bluetooth events
		IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
		registerReceiver(mBluetoothReceiver, filter);
		// Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager =
				(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		assert bluetoothManager != null;
		BluetoothAdapter _mBTAdapter = bluetoothManager.getAdapter();

		// Checks if Bluetooth is supported on the device.
		if (_mBTAdapter == null)
		{
			Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
			finish();
		} else
		{

			scanner = NearestScanner.getInstance(_mBTAdapter.getBluetoothLeScanner(), _nearestCallback);
			scanner.start();
			startAdvertising();
			startServer();
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
		if (bluetoothAdapter.isEnabled())
		{
			scanner.stop();
			stopServer();
			stopAdvertising();
		}

		unregisterReceiver(mBluetoothReceiver);
	}

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

	private void addView(NearestScanner.BTLeDevice device)
	{
		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentTransaction transaction = fragmentManager.beginTransaction();
		PeerFragment peerFragment = PeerFragment.newInstance(device.getName(),
				device.getAddress(), device.getRssi());
		_peersView.put(device.getName(), peerFragment);
		transaction.add(R.id.list, peerFragment);
		transaction.commit();
	}

	private void removeView(NearestScanner.BTLeDevice device)
	{
		try
		{
			PeerFragment peerFragment = _peersView.get(device.getName());
			assert peerFragment != null;
			FragmentManager fragmentManager = getSupportFragmentManager();
			FragmentTransaction transaction = fragmentManager.beginTransaction();
			transaction.remove(peerFragment);
			transaction.commit();
		} catch (IllegalStateException ignore)
		{
			// can ocurr after onSaveInstanceState
		}
	}

	private void updateView(NearestScanner.BTLeDevice device)
	{
		PeerFragment peerFragment = _peersView.get(device.getName());
		assert peerFragment != null;
		peerFragment.setArgAddress(device.getAddress());
		peerFragment.setArgRssi(device.getRssi());
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
