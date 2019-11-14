package edu.unsj.idei.btletimeserver;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.os.Handler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Find and holds the nearest btle devices.
 */
final class NearestScanner
{
	private BluetoothLeScanner btleScanner;
	private HashMap<String, BTLeDevice> deviceHashMap = new HashMap<>();
	private Handler _handler;
	private static NearestScanner _instance;
	private NearestCallback _callback;

	private ScanCallback callback = new ScanCallback()
	{
		@Override
		public void onScanResult(int callbackType, ScanResult result)
		{
			String name = result.getDevice().getName();

			if (name != null)
			{
				BTLeDevice device;
				if (!deviceHashMap.containsKey(name))
				{
					device = new BTLeDevice(name);
					deviceHashMap.put(name, device);
					_callback.found(device);
				} else
				{
					device = deviceHashMap.get(name);
				}

				if (device != null)
				{
					device.setLastScan(System.currentTimeMillis());
					device.setAddress(result.getDevice().getAddress());
					device.setRssi(result.getRssi());
					_callback.update(device);
				}
			}
		}
	};

	private NearestScanner(NearestCallback callback)
	{
		_handler = new Handler();
		_callback = callback;
		scanLostDevices();
	}

	private void scanLostDevices()
	{
		_handler.postDelayed(new Runnable()
		{
			@Override
			public void run()
			{
				Iterator<Map.Entry<String, BTLeDevice>> it = deviceHashMap.entrySet().iterator();
				long current = System.currentTimeMillis();

				while (it.hasNext())
				{
					Map.Entry<String, BTLeDevice> pair = it.next();
					BTLeDevice dev = pair.getValue();
					if (current - dev.getLastScan() > 10000)
					{
						it.remove();
						_callback.lost(dev);
					}
				}

				scanLostDevices();
			}
		}, 500);
	}

	static NearestScanner getInstance(BluetoothLeScanner scanner, NearestCallback callback)
	{
		if (_instance == null)
		{
			_instance = new NearestScanner(callback);
			_instance.btleScanner = scanner;
		}
		return _instance;
	}

	void start()
	{
		btleScanner.startScan(callback);
	}

	void stop()
	{
		btleScanner.stopScan(callback);
	}

	static class BTLeDevice
	{
		private String name;
		private String address;
		private int rssi;
		private long lastScan;

		BTLeDevice(String name)
		{
			this.name = name;
		}

		String getName()
		{
			return name;
		}

		void setAddress(String address)
		{
			this.address = address;
		}

		String getAddress()
		{
			return this.address;
		}

		int getRssi()
		{
			return rssi;
		}

		void setRssi(int rssi)
		{
			this.rssi = rssi;
		}

		long getLastScan()
		{
			return lastScan;
		}

		void setLastScan(long lastScan)
		{
			this.lastScan = lastScan;
		}
	}

	static abstract class NearestCallback
	{
		abstract void found(BTLeDevice device);

		abstract void lost(BTLeDevice device);

		abstract void update(BTLeDevice device);
	}
}
