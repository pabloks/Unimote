package com.test.unimote;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;

public class HomeActivity extends Activity implements Runnable {

  private static final String TAG = "HomeActivity";

  private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

  private static final int MESSAGE_READ = 1;

  private boolean mPermissionRequestPending;
  private UsbManager mUsbManager;
  private TextView mErrorView;
  private UsbAccessory mAccessory;
  private FileInputStream mInputStream;
  private FileOutputStream mOutputStream;

  private ParcelFileDescriptor mFileDescriptor;
  private PendingIntent mPermissionIntent;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    mErrorView = (TextView) findViewById(R.id.main_error);

    mUsbManager = UsbManager.getInstance(this);
    mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
        ACTION_USB_PERMISSION), 0);
    IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
    filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
    registerReceiver(mUsbReceiver, filter);

    if (getLastNonConfigurationInstance() != null) {
      mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
      openAccessory(mAccessory);
    }

  }

  @Override
  public Object onRetainNonConfigurationInstance() {
    if (mAccessory != null) {
      return mAccessory;
    } else {
      return super.onRetainNonConfigurationInstance();
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    Intent intent = getIntent();
    if (mInputStream != null && mOutputStream != null) {
      return;
    }

    UsbAccessory[] accessories = mUsbManager.getAccessoryList();
    UsbAccessory accessory = (accessories == null ? null
        : accessories[0]);
    if (accessory != null) {
      if (mUsbManager.hasPermission(accessory)) {
        openAccessory(accessory);
      } else {
        synchronized (mUsbReceiver) {
          if (!mPermissionRequestPending) {
            mUsbManager.requestPermission(accessory, mPermissionIntent);
            mPermissionRequestPending = true;
          }
        }
      }
    } else {
      Log.d(TAG, "mAccessory is null");
    }
  }

  public void readRemoteClick(View v) {
    startActivity(new Intent(this, ReadRemoteActivity.class));
  }

  public void useRemoteClick(View v) {
    startActivity(new Intent(this, UseRemoteActivity.class));
  }

  @Override
  public void onPause() {
    super.onPause();
    closeAccessory();
  }

  @Override
  public void onDestroy() {
    unregisterReceiver(mUsbReceiver);
    super.onDestroy();
  }

  private void closeAccessory() {
    try {
      if (mFileDescriptor != null) {
        mFileDescriptor.close();
      }
    } catch (IOException e) {
    } finally {
      mFileDescriptor = null;
      mAccessory = null;
    }
  }

  private void openAccessory(UsbAccessory accessory) {
    mFileDescriptor = mUsbManager.openAccessory(accessory);
    if (mFileDescriptor != null) {
      mAccessory = accessory;
      FileDescriptor fd = mFileDescriptor.getFileDescriptor();
      mInputStream = new FileInputStream(fd);
      mOutputStream = new FileOutputStream(fd);
      Thread thread = new Thread(null, this, "Unimote");
      thread.start();
      Log.d(TAG, "accessory opened");
    } else {
      Log.d(TAG, "accessory open fail");
    }
  }

  private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (ACTION_USB_PERMISSION.equals(action)) {
        synchronized (this) {
          UsbAccessory accessory = UsbManager.getAccessory(intent);
          if (intent.getBooleanExtra(
              UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            openAccessory(accessory);
          } else {
            Log.d(TAG, "permission denied for accessory " + accessory);
          }
          mPermissionRequestPending = false;
        }
      } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED
          .equals(action)) {
        UsbAccessory accessory = UsbManager.getAccessory(intent);
        if (accessory != null && accessory.equals(mAccessory)) {
          closeAccessory();
        }
      }
    }
  };

  public void checkClick(View v) {
    UsbAccessory[] accessoryList = mUsbManager.getAccessoryList();

    if (accessoryList == null) {
      mErrorView.setText("No accesory available");
      return;
    }

    for (UsbAccessory accesory : accessoryList) {
      StringBuilder sb = new StringBuilder();
      sb.append("accesory: ");
      sb.append(accesory.toString());
      mErrorView.setText(sb.toString());

      Log.d("TAG", "accesory: " + accesory.toString());
      Log.d("TAG", "manufacturer: " + accesory.getManufacturer());
      Log.d("TAG", "model: " + accesory.getModel());
      Log.d("TAG", "version: " + accesory.getVersion());
    }
  }

  Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
      case MESSAGE_READ:
        Toast.makeText(HomeActivity.this, "MESSAGE_READ",
            Toast.LENGTH_SHORT).show();
      }
    }
  };

  protected class ReadMsg {
    private byte sw;
    private byte state;

    public ReadMsg(byte sw, byte state) {
      this.sw = sw;
      this.state = state;
    }

    public byte getSw() {
      return sw;
    }

    public byte getState() {
      return state;
    }
  }

  @Override
  public void run() {
    int ret = 0;
    byte[] buffer = new byte[16384];
    int i;

    while (ret >= 0) {
      try {
        ret = mInputStream.read(buffer);
      } catch (IOException e) {
        break;
      }

      i = 0;
      while (i < ret) {
        int len = ret - i;

        switch (buffer[i]) {
        case 0x1:
          if (len >= 3) {
            Message m = Message.obtain(mHandler, MESSAGE_READ);
            m.obj = new ReadMsg(buffer[i + 1], buffer[i + 2]);
            mHandler.sendMessage(m);
          }
          i += 3;
          break;
        
        }
      }
    }
  }
}