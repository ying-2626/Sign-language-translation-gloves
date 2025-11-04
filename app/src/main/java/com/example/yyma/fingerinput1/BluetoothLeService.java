package com.example.yyma.fingerinput1;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private boolean isRunning = false;
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    public final static String ACTION_GATT_CONNECTED =
            "bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_SOFT_SERIAL_SERVICE = UUID.fromString(SampleGattAttributes.SOFT_SERIAL_SERVICE);
    public final static UUID UUID_SPP_SERIAL_SERVICE = UUID.fromString(SampleGattAttributes.SPP_SERIAL_SERVICE); // 添加标准SPP服务UUID
    public final static UUID UUID_CUSTOM_SERVICE = UUID.fromString(SampleGattAttributes.CUSTOM_SERVICE); // 您设备的实际服务UUID
    public final static UUID UUID_CUSTOM_CHARACTERISTIC = UUID.fromString(SampleGattAttributes.CUSTOM_CHARACTERISTIC); // 您设备的实际特征值UUID
    public final static UUID UUID_MD_RX_TX = UUID.fromString(SampleGattAttributes.MD_RX_TX);
    public final static UUID UUID_ETOH_RX_TX = UUID.fromString(SampleGattAttributes.ETOH_RX_TX);

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;


    static final byte[] DISABLE = {0x00};
    static final byte[] ENABLE = {0x01};

    private final IBinder mBinder = new LocalBinder();
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
                // 即使服务发现失败，也发送广播，让用户界面可以处理
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         final BluetoothGattCharacteristic characteristic,
                                         int status) {
            System.out.println(isRunning);
            if (status == BluetoothGatt.GATT_SUCCESS&!isRunning) {
                final Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        isRunning =true;
                        broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                        try {
                            Thread.sleep(500);
                        }catch (InterruptedException e){
                            e.printStackTrace();
                        }
                        isRunning =false;
                    }
                });
                thread.run();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            final BluetoothGattCharacteristic characteristic) {
            System.out.println(isRunning);
            if (!isRunning) {
                final Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        isRunning =true;
                        broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                        try {
                            Thread.sleep(500);
                        }catch (InterruptedException e){
                            e.printStackTrace();
                        }
                        isRunning =false;
                    }
                });
                thread.run();
            }else{
                ;
            }
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // For all other profiles, writes the data formatted in HEX.
        final byte[] data = characteristic.getValue();
        Log.i(TAG, "data: " + characteristic.getValue());

        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            Log.d(TAG, String.format("%s", new String(data)));
            // getting cut off when longer, need to push on new line, 0A
            intent.putExtra(EXTRA_DATA, String.format("%s", new String(data)));
        }
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
                mBluetoothDeviceAddress = address;
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Write to a given char
     *
     * @param characteristic The characteristic to write to
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to RX_TX.
        if (UUID_MD_RX_TX.equals(characteristic.getUuid())
                || UUID_ETOH_RX_TX.equals(characteristic.getUuid())
                || UUID_CUSTOM_CHARACTERISTIC.equals(characteristic.getUuid())) { // 添加对自定义特征值的支持
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            // 检查descriptor是否为null
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(descriptor);
            } else {
                Log.w(TAG, "Descriptor is null for characteristic: " + characteristic.getUuid().toString());
            }
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    public BluetoothGattService getSoftSerialService() {
        // 首先尝试查找自定义服务
        BluetoothGattService _service = mBluetoothGatt.getService(UUID_SOFT_SERIAL_SERVICE);
        if (_service == null) {
            // 如果找不到自定义服务，尝试查找标准SPP服务
            Log.d(TAG, "Soft Serial Service not found, trying SPP service...");
            _service = mBluetoothGatt.getService(UUID_SPP_SERIAL_SERVICE);
            if (_service == null) {
                // 如果标准SPP服务也找不到，尝试查找您设备的自定义服务
                Log.d(TAG, "SPP Serial Service not found, trying custom service...");
                _service = mBluetoothGatt.getService(UUID_CUSTOM_SERVICE);
                if (_service == null) {
                    Log.d(TAG, "Custom Serial Service not found!");
                    // 如果所有预定义服务都找不到，尝试遍历所有服务查找任何可能的串口服务
                    List<BluetoothGattService> services = getSupportedGattServices();
                    if (services != null) {
                        Log.d(TAG, "Available services:");
                        for (BluetoothGattService service : services) {
                            Log.d(TAG, "- Service UUID: " + service.getUuid().toString());
                            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                            for (BluetoothGattCharacteristic characteristic : characteristics) {
                                Log.d(TAG, "  Characteristic UUID: " + characteristic.getUuid().toString());
                            }
                        }
                    }
                    return null;
                } else {
                    Log.d(TAG, "Custom Serial Service found!");
                }
            } else {
                Log.d(TAG, "SPP Serial Service found!");
            }
        } else {
            Log.d(TAG, "Soft Serial Service found!");
        }
        return _service;
    }

//    public void enableSoftSerialService(boolean enable) {
//        BluetoothGattService _service = mBluetoothGatt.getService(UUID_SOFT_SERIAL_SERVICE);
//        if (_service == null) {
//            Log.d(TAG, "Soft Serial Service not found!");
//            return;
//        }
//
//        Log.d(TAG, "Soft Serial Service found!");
//
//        BluetoothGattCharacteristic _nc = _service.getCharacteristic(UUID_HM_RX_TX);
//        if (_nc != null) {
//            if (enable)
//                _nc.setValue(ENABLE);
//            else
//                _nc.setValue(DISABLE);
//            mBluetoothGatt.writeCharacteristic(_nc);
//        }
//
//        Log.d(TAG, "RX TX ready!");
//    }
}