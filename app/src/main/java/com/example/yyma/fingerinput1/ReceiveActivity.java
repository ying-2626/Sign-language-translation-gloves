package com.example.yyma.fingerinput1;

import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.os.Environment.getExternalStorageDirectory;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.yyma.fingerinput1.Sentence;
import com.example.yyma.fingerinput1.WordBank;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ReceiveActivity extends Activity {
    private boolean loadComplete= false;
    Timer timer = null;
    boolean ready4trans = false;
    StringBuffer transferBuffer = new StringBuffer("");
    private WordBank wordBank = null;
    private final static String TAG = ReceiveActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    public static int BLE_MSG_SEND_INTERVAL = 100;
    public static int BLE_MSG_BUFFER_LEN = 20;//18

    private TextView isSerial;
    private TextView returnText;
    private TextView returnTextNow;
    private ScrollView scrollView;
    private String mDeviceName, mDeviceAddress;

    private BluetoothLeService mBluetoothLeService;
    private BluetoothGattCharacteristic characteristicTX, characteristicRX;
    private boolean mConnected = false, characteristicReady = false;

    private Button back = null;
    private MediaPlayer player;
    private String sdcard;
    private Sentence sentence;


    /**
     * 判断外置sdcard是否可以正常使用
     *
     * @return
     */
    public static Boolean existsSdcard() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !Environment.isExternalStorageRemovable();
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
                return;
            }
            
            // 确保设备地址不为空再尝试连接
            if (mDeviceAddress != null && !mDeviceAddress.isEmpty()) {
                // Automatically connects to the device upon successful start-up initialization.
                mBluetoothLeService.connect(mDeviceAddress);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState("连接就绪");
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState("等待连接……");
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "Service discovery completed, now finding serial communication service...");
                
                // 动态查找可用的服务和特征值
                BluetoothGattService gattService = mBluetoothLeService.getSoftSerialService();
                if (gattService == null) {
                    Log.e(TAG, "No suitable serial service found on the device");
                    Toast.makeText(ReceiveActivity.this, "没有找到串口服务", Toast.LENGTH_SHORT).show();
                    // 尝试从所有服务中查找可用的特征值
                    findAnyUsableCharacteristic();
                    return;
                }

                // 获取串口通信的特征值
                BluetoothGattCharacteristic serialCharacteristic = mBluetoothLeService.getSerialCharacteristic(gattService);
                if (serialCharacteristic == null) {
                    Log.e(TAG, "No suitable serial characteristic found in the service");
                    Toast.makeText(ReceiveActivity.this, "未找到可用的通信特征值", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 设置全局变量用于后续通信
                characteristicTX = serialCharacteristic;
                characteristicRX = serialCharacteristic;

                if (characteristicTX != null) {
                    Log.d(TAG, "Setting up notification for characteristic: " + characteristicTX.getUuid().toString());
                    // 启用通知，这样当设备发送数据时，我们能立即收到
                    mBluetoothLeService.setCharacteristicNotification(characteristicTX, true);
                    isSerial.setText("连接就绪 - 服务: " + gattService.getUuid().toString());
                    updateReadyState(0);
                } else {
                    isSerial.setText("连接失败");
                    Toast.makeText(ReceiveActivity.this, "特征值无效", Toast.LENGTH_SHORT).show();
                }

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                // 添加标记方便筛选日志
                Log.d("BLUETOOTH_DATA", "Received data from Bluetooth device");
                String receivedData = intent.getStringExtra(mBluetoothLeService.EXTRA_DATA);
                Log.d("BLUETOOTH_DATA", "Data content: " + receivedData);
                Toast.makeText(ReceiveActivity.this, "收到蓝牙消息: " + receivedData, Toast.LENGTH_SHORT).show();

                // 确保receivedData不为null再处理
                if (receivedData != null) {
                    displayData(receivedData);
                    Thread mThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if(timer==null) {
                                timer = new Timer();
                                TimerTask task = new TimerTask() {
                                    public void run() {
                                        //Thread tThread = new Thread(new Runnable() {
                                           // @Override
                                           // public void run() {
                                                if(loadComplete && sentence != null) {
                                                    //transferBuffer.deleteCharAt(transferBuffer.length() - 1);
                                                    System.out.println(transferBuffer.toString());
                                                    sentence.redim(transferBuffer.toString());
                                                    sentence.transfer();
                                                    System.out.println("Translate Completed");
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            returnText.append(sentence.getRet());
                                                        }
                                                    });
                                                    transferBuffer.setLength(0);
                                               // }
                                           // }
                                        }//);
                                        //tThread.start();
                                        System.out.println(4);
                                    }
                                };
                                timer.schedule(task, 3000);
                                System.out.println(3);
                            }else {
                                if (timer != null) {
                                    timer.cancel();
                                    timer = null;
                                    timer = new Timer();
                                    TimerTask task = new TimerTask() {
                                        public void run() {
                                            //Thread tThread = new Thread(new Runnable() {
                                               // @Override
                                               // public void run() {
                                                    if(loadComplete && sentence != null) {
                                                        //transferBuffer.deleteCharAt(transferBuffer.length() - 1);
                                                        System.out.println("trb: "+transferBuffer.toString());
                                                        sentence.redim(transferBuffer.toString());
                                                        sentence.transfer();
                                                        System.out.println(sentence.getRet());
                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                returnText.append(transferBuffer+" :\n");
                                                                returnText.append(sentence.getRet()+"\n");
                                                            }
                                                        });
                                                        transferBuffer.setLength(0);
                                                   // }
                                               // }
                                            }//);
                                            //tThread.start();
                                        }
                                    };
                                    timer.schedule(task, 3000);
                                }
                            }
                        }
                    });
                    mThread.start();
                } else {
                    Log.w("BLUETOOTH_DATA", "Received null data from Bluetooth device");
                }
            }
        }

        /**
         * 当无法找到标准服务时，尝试从所有服务中查找可用的特征值
         */
        private void findAnyUsableCharacteristic() {
            List<BluetoothGattService> services = mBluetoothLeService.getSupportedGattServices();
            if (services == null || services.isEmpty()) {
                Log.d(TAG, "No services found");
                return;
            }

            Log.d(TAG, "Searching for any usable characteristic in all services...");

            // 遍历所有服务查找可用的特征值
            for (BluetoothGattService service : services) {
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                if (characteristics != null && !characteristics.isEmpty()) {
                    // 尝试找到合适的特征值
                    for (BluetoothGattCharacteristic characteristic : characteristics) {
                        int properties = characteristic.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                            (properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 ||
                            (properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0 ||
                            (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                            (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                            // 找到了可能的串口通信特征值
                            characteristicTX = characteristic;
                            characteristicRX = characteristicTX;
                            Log.d(TAG, "Found usable characteristic: " + characteristicTX.getUuid().toString());

                            // 设置通知
                            if (characteristicTX != null) {
                                mBluetoothLeService.setCharacteristicNotification(characteristicTX, true);
                                isSerial.setText("连接就绪 - 服务: " + service.getUuid().toString());
                                updateReadyState(0);
                            }
                            return;
                        }
                    }
                }
            }

            // 如果还是找不到特征值
            isSerial.setText("连接失败");
            Toast.makeText(ReceiveActivity.this, "未找到任何可用的特征值", Toast.LENGTH_SHORT).show();
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.receive_activity);
        
        // 初始化媒体播放器和sdcard路径
        player = new MediaPlayer();
        sdcard = getExternalStorageDirectory().getAbsolutePath();
        sentence = new Sentence("", "");
        
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        
        isSerial = (TextView) findViewById(R.id.isSerial);
        returnText = (TextView) findViewById(R.id.receive_text);
        returnTextNow = (TextView) findViewById(R.id.receive_text_now);
        scrollView = (ScrollView) findViewById(R.id.scrollView);
        back = (Button)findViewById(R.id.back);
        
        // 检查权限
        List<String> permissionsNeeded = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 及以上版本
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            // Android 12 以下版本
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.BLUETOOTH);
            }
        }
        
        // 检查存储权限（仅适用于Android 10及以下版本）
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ReceiveActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });
        
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]), 1);
        } else {
            // 如果已经拥有所有权限，则绑定蓝牙服务
            Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                boolean allPermissionsGranted = true;
                for (int result : grantResults) {
                    if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false;
                        break;
                    }
                }
                
                if (allPermissionsGranted) {
                    // 权限已授予，绑定蓝牙服务
                    Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
                    bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                } else {
                    // 权限被拒绝
                    Toast.makeText(this, "权限被拒绝，部分功能可能无法正常工作", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter(), RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        }
        // 只有在服务已绑定且设备地址有效时才尝试连接
        if (mBluetoothLeService != null && mDeviceAddress != null && !mDeviceAddress.isEmpty()) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        
        // 释放MediaPlayer资源
        if (player != null) {
            player.release();
            player = null;
        }
        
        // 释放Timer资源
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_connect) {
            if (mBluetoothLeService != null && mDeviceAddress != null && !mDeviceAddress.isEmpty()) {
                mBluetoothLeService.connect(mDeviceAddress);
            }
            return true;
        } else if (itemId == R.id.menu_disconnect) {
            if (mBluetoothLeService != null) {
                mBluetoothLeService.disconnect();
            }
            return true;
        } else if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateReadyState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                wait_ble(2000);
                characteristicReady = true;
            }
        });
    }

    private void updateConnectionState(final String resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                isSerial.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            if(data.charAt(data.length()-1)==0){
                ;//忽略错误值
            }else {
                //returnText.append(data);
                //System.out.println(data.contains("zing"));
                if(data.contains("zing")) data=data.replace('z','y');
                //System.out.println(data);
                soundPlay(data);
                data = data.replace("\r\n", "");
                returnTextNow.setText(data);
                transferBuffer.append(data+"\u007C");
                //returnText.setText(data);
                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }
        }
    }

    private void soundPlay(String info) {
        // 确保info不为null
        if (info == null) {
            Log.w(TAG, "soundPlay received null info");
            return;
        }
        
        Log.d(TAG, "soundPlay called with info: " + info);
        
        String path = info.replace(" ","");
        // 对于"ba1"这样的数据，我们需要提取中间的部分作为文件名
        // 移除最后两个字符(\r\n)，然后提取有效的文件名
        if (path.length() >= 3) {  // 确保有足够的字符
            // 移除\r\n
            if (path.endsWith("\r\n")) {
                path = path.substring(0, path.length()-2);
            }
            
            // 检查是否以数字结尾
            char lastChar = path.charAt(path.length() - 1);
            if (lastChar >= '1' && lastChar <= '5') {
                try {
                    // 获取资源ID
                    int audioResourceId = getResources().getIdentifier(path, "raw", getPackageName());
                    
                    // 检查资源是否存在
                    if (audioResourceId == 0) {
                        Log.w(TAG, "音频资源不存在: " + path);
                        Toast.makeText(this, "音频资源不存在: " + path, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    Log.d(TAG, "Found audio resource ID: " + audioResourceId);
                    
                    // 停止当前正在播放的音频（如果有）
                    if (player != null) {
                        try {
                            player.stop();
                            player.release();
                        } catch (Exception e) {
                            // 忽略异常
                        }
                    }
                    
                    // 创建新的MediaPlayer实例
                    player = MediaPlayer.create(this, audioResourceId);
                    if (player != null) {
                        player.start();
                        Log.d(TAG, "Started playing audio: " + path);
                        
                        // 设置播放完成监听器以释放资源
                        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                try {
                                    mp.release();
                                } catch (Exception e) {
                                    // 忽略异常
                                }
                                Log.d(TAG, "Released MediaPlayer resources");
                            }
                        });
                    } else {
                        Log.e(TAG, "Failed to create MediaPlayer for: " + path);
                        Toast.makeText(this, "无法创建音频播放器: " + path, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "播放音频失败: " + e.getMessage());
                    Toast.makeText(this, "播放音频失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "Skipping audio play, last character is not 1-5: " + lastChar);
            }
        } else {
            Log.d(TAG, "Skipping audio play, data too short: " + path);
        }
    }

    public void wait_ble(int i) {
        try {
            Thread.sleep(i);
        } catch (Exception e) {
            // ignore
        }
    }

    private void sendMessage(String msg) {
        int msglen = msg.length();
        Log.d(TAG, "Sending Result=" + msglen + ":" + msg);

        if (characteristicReady && (mBluetoothLeService != null)
                && (characteristicTX != null) && (characteristicRX != null)) {

            for (int offset = 0; offset < msglen; offset += BLE_MSG_BUFFER_LEN) {
                characteristicTX.setValue(msg.substring(offset, Math.min(offset + BLE_MSG_BUFFER_LEN, msglen)));
                mBluetoothLeService.writeCharacteristic(characteristicTX);
                wait_ble(BLE_MSG_SEND_INTERVAL);
            }
        } else {
            Toast.makeText(ReceiveActivity.this, "已断开连接", Toast.LENGTH_SHORT).show();
        }
    }
}