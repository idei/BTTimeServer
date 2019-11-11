package edu.unsj.idei.btletimeserver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.UUID;

public class ChatProfile
{
	private static final String TAG = ChatProfile.class.getSimpleName();

	/**
	 * The Client Characteristic Configuration descriptor defines how the characteristic may be
	 * configured by a specific client.
	 * This descriptor shall be persistent across connections for bonded devices.
	 * The Client Characteristic Configuration descriptor is unique for each client. A client may
	 * read	and write this descriptor to determine and set the configuration for that client.
	 * Authentication and authorization may be required by the server to write this descriptor.
	 * The default value for the Client Characteristic Configuration descriptor is 0x00.
	 * Upon connection of non-binded clients, this descriptor is set to the default value.
	 */
	static UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	static UUID MESSAGE_SERVICE = UUID.fromString("00001821-0000-1000-8000-00805f9b34fb");

	static UUID NOTIFICATION_MESSAGE_CHARACTERISTIC = UUID.fromString("FF9D6536-1A3A-47A9-AA86-9CDD63A6F0DB");
	static UUID PUBLIC_MESSAGE_CHARACTERISTIC = UUID.fromString("A0E8C677-B42B-43FB-9710-8AC0AB24B3BB");
	static UUID PRIVATE_MESSAGE_CHARACTERISTIC = UUID.fromString("51EAB7A0-F30E-45AF-AF5D-F4D86699310A");

	/**
	 * Return a configured {@link BluetoothGattService} instance for the Chat service.
	 *
	 * @return The new orientation service.
	 */
	static BluetoothGattService createChatService()
	{
		// Start a new primary MESSAGE_SERVICE
		BluetoothGattService service = new BluetoothGattService(
				MESSAGE_SERVICE,
				BluetoothGattService.SERVICE_TYPE_PRIMARY);

		// Configure NOTIFICATION_MESSAGE_CHARACTERISTIC as read-only and for support notifications.
		BluetoothGattCharacteristic notificationMessage = new BluetoothGattCharacteristic(
				NOTIFICATION_MESSAGE_CHARACTERISTIC,
				BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
				BluetoothGattCharacteristic.PERMISSION_READ);

		// Describe the NOTIFICATION_MESSAGE_CHARACTERISTIC with a R/W descriptor
		BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(
				CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR,
				BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
		notificationMessage.addDescriptor(configDescriptor);

		// Configure the PRIVATE_MESSAGE_CHARACTERISTIC as write-only
		BluetoothGattCharacteristic privateMessage = new BluetoothGattCharacteristic(
				PRIVATE_MESSAGE_CHARACTERISTIC,
				BluetoothGattCharacteristic.PROPERTY_WRITE,
				BluetoothGattCharacteristic.PERMISSION_WRITE);

		// Configure the PUBLIC_MESSAGE_CHARACTERISTIC as write-only
		BluetoothGattCharacteristic publicMessage = new BluetoothGattCharacteristic(
				PUBLIC_MESSAGE_CHARACTERISTIC,
				BluetoothGattCharacteristic.PROPERTY_WRITE,
				BluetoothGattCharacteristic.PERMISSION_WRITE);

		service.addCharacteristic(notificationMessage);
		service.addCharacteristic(privateMessage);
		service.addCharacteristic(publicMessage);

		return service;
	}
}
