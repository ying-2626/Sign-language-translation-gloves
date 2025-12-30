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

    // 标准SPP服务UUID - 用于串口通信
    public final static UUID UUID_SPP_SERIAL_SERVICE = UUID.fromString(SampleGattAttributes.SPP_SERIAL_SERVICE); // 00001101-0000-1000-8000-00805F9B34FB
    // Microduino服务UUID - 一种常用的串口服务
    public final static UUID UUID_SOFT_SERIAL_SERVICE = UUID.fromString(SampleGattAttributes.SOFT_SERIAL_SERVICE); // 0000fff0-0000-1000-8000-00805F9B34FB
    // 其他常见串口服务
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

    // GATT回调接口 - 处理各种蓝牙事件
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                
                // 连接成功后，开始发现服务 - 这是关键步骤
                // 设备会返回它支持的所有服务列表
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
                // 服务发现成功！现在我们可以查看设备提供的所有服务
                Log.i(TAG, "Services discovered successfully. Starting to analyze services...");
                
                // 打印所有发现的服务及其特征值
                logAllServicesAndCharacteristics();
                
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

    // 记录所有服务和特征值的日志，帮助理解设备结构
    private void logAllServicesAndCharacteristics() {
        List<BluetoothGattService> services = getSupportedGattServices();
        if (services == null) {
            Log.w(TAG, "No services available.");
            return;
        }

        Log.i(TAG, "=== DEVICE SERVICES ANALYSIS ===");
        for (BluetoothGattService service : services) {
            UUID serviceUUID = service.getUuid();
            String serviceName = SampleGattAttributes.lookup(serviceUUID.toString(), "Unknown Service");
            Log.i(TAG, "Service: " + serviceUUID.toString() + " (" + serviceName + ")");

            // 遍历该服务下的所有特征值
            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                UUID charUUID = characteristic.getUuid();
                String charName = SampleGattAttributes.lookup(charUUID.toString(), "Unknown Characteristic");
                int properties = characteristic.getProperties();
                
                // 分析特征值的属性
                String propDesc = analyzeCharacteristicProperties(properties);
                
                Log.i(TAG, "  -> Char: " + charUUID.toString() + " (" + charName + ")");
                Log.i(TAG, "     Properties: " + propDesc);
                
                // 检查描述符
                List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
                for (BluetoothGattDescriptor descriptor : descriptors) {
                    Log.i(TAG, "     Descriptor: " + descriptor.getUuid().toString());
                }
            }
        }
        Log.i(TAG, "=== END DEVICE SERVICES ANALYSIS ===");
    }

    // 分析特征值属性
    private String analyzeCharacteristicProperties(int properties) {
        StringBuilder sb = new StringBuilder();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0) sb.append("READ ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) sb.append("WRITE ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) sb.append("WRITE_NR ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) sb.append("NOTIFY ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) sb.append("INDICATE ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_BROADCAST) != 0) sb.append("BROADCAST ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0) sb.append("SIGNED_WRITE ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) != 0) sb.append("EXTENDED ");
        return sb.toString().trim();
    }

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

        // 为串口通信特征值启用通知
        if (UUID_SPP_SERIAL_SERVICE.equals(characteristic.getService().getUuid()) ||
            UUID_SOFT_SERIAL_SERVICE.equals(characteristic.getService().getUuid()) ||
            UUID_MD_RX_TX.equals(characteristic.getUuid()) ||
            UUID_ETOH_RX_TX.equals(characteristic.getUuid()) ||
            isReadableOrWritableCharacteristic(characteristic)) {
            
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            // 检查descriptor是否为null
            if (descriptor != null) {
                // 启用通知 - 这是接收实时数据的关键
                descriptor.setValue(enabled ? 
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : 
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(descriptor);
            } else {
                Log.w(TAG, "Descriptor is null for characteristic: " + characteristic.getUuid().toString());
            }
        }
    }

    /**
     * 检查特征值是否是可读或可写的串口特征值
     * @param characteristic 特征值
     * @return 是否为串口特征值
     */
    private boolean isReadableOrWritableCharacteristic(BluetoothGattCharacteristic characteristic) {
        int properties = characteristic.getProperties();
        return (properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0 ||
               (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
               (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ||
               (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
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

    /**
     * 尝试获取适合串口通信的服务
     * 这是软硬件协同的关键环节
     */
    public BluetoothGattService getSoftSerialService() {
        Log.d(TAG, "Starting service discovery process...");
        
        // 1. 首先尝试查找标准SPP服务 (最通用的串口服务)
        BluetoothGattService _service = mBluetoothGatt.getService(UUID_SPP_SERIAL_SERVICE);
        if (_service != null) {
            Log.d(TAG, "Found standard SPP Serial Service: " + UUID_SPP_SERIAL_SERVICE.toString());
            return _service;
        }
        
        // 2. 尝试查找Microduino服务 (另一种常用串口服务)
        _service = mBluetoothGatt.getService(UUID_SOFT_SERIAL_SERVICE);
        if (_service != null) {
            Log.d(TAG, "Found Soft Serial Service: " + UUID_SOFT_SERIAL_SERVICE.toString());
            return _service;
        }
        
        // 3. 如果标准服务都找不到，遍历所有服务查找可能的串口服务
        List<BluetoothGattService> services = getSupportedGattServices();
        if (services != null) {
            Log.d(TAG, "Analyzing all available services for serial communication capability...");
            
            for (BluetoothGattService service : services) {
                Log.d(TAG, "Checking service: " + service.getUuid().toString());
                
                // 检查该服务是否有适合串口通信的特征值
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    Log.d(TAG, "  Checking characteristic: " + characteristic.getUuid().toString() + 
                          ", Properties: " + analyzeCharacteristicProperties(characteristic.getProperties()));
                    
                    // 查找具有读写和通知能力的特征值 - 这是串口通信的关键
                    if (isReadableOrWritableCharacteristic(characteristic)) {
                        Log.d(TAG, "Found potential serial service: " + service.getUuid().toString());
                        return service; // 返回第一个有合适特征值的服务
                    }
                }
            }
        }
        
        Log.d(TAG, "No suitable serial service found!");
        return null;
    }

    /**
     * 获取串口通信用的特征值
     * 这是实际进行数据收发的地方
     */
    public BluetoothGattCharacteristic getSerialCharacteristic(BluetoothGattService service) {
        if (service == null) {
            Log.e(TAG, "Service is null, cannot get characteristic");
            return null;
        }
        
        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
        if (characteristics == null || characteristics.isEmpty()) {
            Log.e(TAG, "Service has no characteristics");
            return null;
        }
        
        // 优先查找标准串口特征值
        for (BluetoothGattCharacteristic characteristic : characteristics) {
            UUID uuid = characteristic.getUuid();
            if (UUID_MD_RX_TX.equals(uuid) || UUID_ETOH_RX_TX.equals(uuid)) {
                Log.d(TAG, "Found standard serial characteristic: " + uuid.toString());
                return characteristic;
            }
        }
        
        // 否则返回第一个适合串口通信的特征值
        for (BluetoothGattCharacteristic characteristic : characteristics) {
            if (isReadableOrWritableCharacteristic(characteristic)) {
                Log.d(TAG, "Using generic serial characteristic: " + characteristic.getUuid().toString());
                return characteristic;
            }
        }
        
        Log.e(TAG, "No suitable serial characteristic found in service");
        return null;
    }
}