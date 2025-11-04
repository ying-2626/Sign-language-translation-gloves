package com.example.yyma.fingerinput1;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_CODE = 2;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 3;
    
    private BluetoothAdapter mBluetoothAdapter;
    private ListView mListView;
    private ArrayAdapter<String> mArrayAdapter;
    private List<BluetoothDevice> mDevices;
    private Button mScanButton;
    private TextView mStatusText;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        mListView = findViewById(R.id.device_list);
        mArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mListView.setAdapter(mArrayAdapter);
        
        mDevices = new ArrayList<>();
        
        mScanButton = findViewById(R.id.scan_button);
        mScanButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    scanLeDevice(true);
                }
            }
        });
        // 添加列表项点击事件
        mListView.setOnItemClickListener((parent, view, position, id) -> {
            final BluetoothDevice device = mDevices.get(position);
            final Intent intent = new Intent(MainActivity.this, ReceiveActivity.class);
            intent.putExtra(ReceiveActivity.EXTRAS_DEVICE_NAME, device.getName());
            intent.putExtra(ReceiveActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
            startActivity(intent);
        });
        
        mStatusText = findViewById(R.id.status_text);
        
        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 及以上版本
            List<String> permissionsNeeded = new ArrayList<>();
            
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.BLUETOOTH_SCAN);
            }
            
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.BLUETOOTH_CONNECT);
            }
            
            if (!permissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            }
        } else {
            // Android 12 以下版本
            List<String> permissionsNeeded = new ArrayList<>();
            
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.BLUETOOTH);
            }
            
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.BLUETOOTH_ADMIN);
            }
            
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            
            if (!permissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            }
        }
        
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 检查蓝牙是否启用以及必要的权限是否已授予
        if (mBluetoothAdapter.isEnabled()) {
            if (checkPermissions()) {
                scanLeDevice(true);
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            scanLeDevice(true);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                boolean allPermissionsGranted = true;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false;
                        break;
                    }
                }
                
                if (allPermissionsGranted) {
                    // 权限已授予
                } else {
                    // 权限被拒绝
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                }
                break;
            case LOCATION_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 位置权限已授予
                    checkPermissions(); // 继续检查其他权限
                } else {
                    Toast.makeText(this, "Location permission is required for Bluetooth scanning", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }
    
    /**
     * 检查并请求必要的权限
     * @return 如果已有所有权限返回true，否则请求权限并返回false
     */
    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 及以上版本
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.BLUETOOTH_SCAN},
                        PERMISSION_REQUEST_CODE);
                return false;
            }
            
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.BLUETOOTH_CONNECT},
                        PERMISSION_REQUEST_CODE);
                return false;
            }
        } else {
            // Android 12 以下版本
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.BLUETOOTH},
                        PERMISSION_REQUEST_CODE);
                return false;
            }
            
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.BLUETOOTH_ADMIN},
                        PERMISSION_REQUEST_CODE);
                return false;
            }
            
            // 对于Android 10及以上版本，需要位置权限才能扫描蓝牙设备
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                            LOCATION_PERMISSION_REQUEST_CODE);
                    return false;
                }
            } else {
                // Android 9及以下版本
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},
                            LOCATION_PERMISSION_REQUEST_CODE);
                    return false;
                }
            }
        }
        
        // 检查存储权限（仅适用于Android 10及以下版本）
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 
                        PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        
        // 额外检查位置服务是否开启（针对华为、OPPO等品牌手机）
        if (!isLocationEnabled()) {
            showLocationEnableDialog();
            return false;
        }
        
        return true; // 所有权限都已授予
    }
    
    /**
     * 检查位置服务是否开启
     * @return 位置服务是否开启
     */
    private boolean isLocationEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9.0及以上使用新API
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            return locationManager.isLocationEnabled();
        } else {
            // Android 9.0以下使用旧API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    int locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
                    return locationMode != Settings.Secure.LOCATION_MODE_OFF;
                } catch (Settings.SettingNotFoundException e) {
                    e.printStackTrace();
                    return false;
                }
            } else {
                String locationProviders = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
                return !TextUtils.isEmpty(locationProviders);
            }
        }
    }
    
    /**
     * 显示位置服务开启提示对话框
     */
    private void showLocationEnableDialog() {
        new AlertDialog.Builder(this)
                .setTitle("位置服务未开启")
                .setMessage("请开启位置服务以正常使用蓝牙扫描功能")
                .setPositiveButton("去开启", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 跳转到位置服务设置页面
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    private void scanLeDevice(boolean enable) {
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 及以上版本检查蓝牙连接权限
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN)
                        == PackageManager.PERMISSION_GRANTED) {
                    mBluetoothAdapter.startLeScan(mLeScanCallback);
                }
            } else {
                // Android 12 以下版本
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            }
            mStatusText.setText("Scanning...");
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 及以上版本检查蓝牙连接权限
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN)
                        == PackageManager.PERMISSION_GRANTED) {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            } else {
                // Android 12 以下版本
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
            mStatusText.setText("Stopped");
        }
    }
    
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (!mDevices.contains(device)) {
                mDevices.add(device);
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
    };
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_scan) {
            if (checkPermissions()) {
                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    scanLeDevice(true);
                }
            }
            return true;
        } else if (itemId == R.id.menu_stop) {
            scanLeDevice(false);
            return true;
        } else if (itemId == R.id.menu_refresh) {
            mArrayAdapter.clear();
            mDevices.clear();
            if (checkPermissions()) {
                scanLeDevice(true);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
