package jp.co.spacee.workspacee_nfc_test;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public  class  ReceiptMain  extends  Activity
{
	private						LeDeviceListAdapter		mLeDeviceListAdapter		= null;
	private static				BluetoothLeScanner		mBTLeScanner				= null;
	private 					int						VerBLE						= 0;

	private						boolean					mScanning;
	private						Handler					mHandler					= new Handler();

	private 					BluetoothDevice			mBTDev						= null;
	private						String					mBTName						= "";
	private						String					mBTAddr						= "";


	@Override
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		// ステータスバー非表示
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		// タイトルバー非表示
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.activity_receipt_main);


		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
				VerBLE = 50;
		  else	VerBLE = 44;


		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		ReceiptTabApplication.mBluetoothAdapter = bluetoothManager.getAdapter();
		if (ReceiptTabApplication.mBluetoothAdapter == null)
		  {
			Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
			finish();
			return;
		  }
	}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	protected void onResume()
	{
		super.onResume();

		if (!ReceiptTabApplication.mBluetoothAdapter.isEnabled())
		  {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, 1);
		  }
		 else
		  {
			if (VerBLE == 50)
			  {
				mBTLeScanner = ReceiptTabApplication.mBluetoothAdapter.getBluetoothLeScanner();
				if (mBTLeScanner != null)
				  {
//					BleScannerStartScan();
					mBTLeScanner.startScan(scanCallback);
				  }
			  }
			 else
			  {
				ReceiptTabApplication.mBluetoothAdapter.startLeScan(mLeScanCallback);
			  }
		  }
	}


	private  void  callReaderActivity()
	{
		Intent  intent = new Intent();
		intent.setClass(ReceiptMain.this, ReaderActivity.class);
		intent.putExtra("DEVICE_NAME", 	mBTDev);
		intent.putExtra("DEVICE_ADDRESS", mBTAddr);
		startActivityForResult(intent, 2);
	}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /* User chose not to enable Bluetooth. */
		if		(requestCode == 1)
		  {
			if (resultCode == Activity.RESULT_CANCELED)
			  {
				finish();
				return;
			  }
		  }
		else if (requestCode == 2)
		  {
			finish();
			return;
		  }

		super.onActivityResult(requestCode, resultCode, data);
	}


	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private synchronized void scanLeDevice(final boolean enable) {
		if (enable) {
            /* Stops scanning after a pre-defined scan period. */
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (mScanning) {
						mScanning = false;
						ReceiptTabApplication.mBluetoothAdapter.stopLeScan(mLeScanCallback);
					}
					invalidateOptionsMenu();
				}
			}, 3000);

			mScanning = true;
			ReceiptTabApplication.mBluetoothAdapter.startLeScan(mLeScanCallback);
///			invalidateOptionsMenu();
		} else if (mScanning) {
			mScanning = false;
			ReceiptTabApplication.mBluetoothAdapter.stopLeScan(mLeScanCallback);
///			invalidateOptionsMenu();
		}
	}


	/* Adapter for holding devices found through scanning. */
	private class LeDeviceListAdapter extends BaseAdapter
	{
		private ArrayList<BluetoothDevice> mLeDevices;
		private LayoutInflater mInflator;

		public LeDeviceListAdapter() {
			super();
			mLeDevices = new ArrayList<BluetoothDevice>();
			mInflator  = ReceiptMain.this.getLayoutInflater();
		}

		public void addDevice(BluetoothDevice device) {
			if (!mLeDevices.contains(device)) {
				mLeDevices.add(device);
			}
		}

		public BluetoothDevice getDevice(int position) {
			return mLeDevices.get(position);
		}

		public void clear() {
			mLeDevices.clear();
		}

		@Override
		public int getCount() {
			return mLeDevices.size();
		}

		@Override
		public Object getItem(int i) {
			return mLeDevices.get(i);
		}

		@Override
		public long getItemId(int i) {
			return i;
		}

		@Override
		public View getView(int i, View view, ViewGroup viewGroup)
		{
/*
			ViewHolder viewHolder;
            / * General ListView optimization code. * /
			if (view == null) {
				view = mInflator.inflate(R.layout.listitem_device, null);
				viewHolder = new ViewHolder();
				viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
				viewHolder.deviceName    = (TextView) view.findViewById(R.id.device_name);
				view.setTag(viewHolder);
			} else {
				viewHolder = (ViewHolder) view.getTag();
			}

			BluetoothDevice device = mLeDevices.get(i);
			final String deviceName = device.getName();
			if (deviceName != null && deviceName.length() > 0)
				viewHolder.deviceName.setText(deviceName);
			else
				viewHolder.deviceName.setText(R.string.unknown_device);
			viewHolder.deviceAddress.setText(device.getAddress());
*/
			return view;
		}
	}


	/* Device scan callback. */
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback()
	{
		@Override
		public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord)
		{
			runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					mBTDev  = device;
					mBTName = mBTDev.getName();
					mBTAddr = mBTDev.getAddress();

					callReaderActivity();
				}
			});
		}
	};



////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
/*
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private  void  BleScannerStartScan()
	{
		mBTLeScanner.startScan(new ScanCallback()
		{
			@Override
			public void onScanResult(int callbackType, ScanResult result)
			{
				super.onScanResult(callbackType, result);

				if ((result != null) && (result.getDevice() != null))
				{
					mBTDev  = result.getDevice();
					mBTName = mBTDev.getName();
					mBTAddr = mBTDev.getAddress();

//					BleScannerStopScan();				//	stop scan first
					mBTLeScanner.stopScan(scanCallback);

//					callReaderActivity();

				}
				else
				{
					Toast.makeText(getApplicationContext(), "Name="+result.getDevice(), Toast.LENGTH_SHORT).show();
				}
			}
		});
	}
*/

	ScanCallback scanCallback = new ScanCallback()
	{
		@Override
		public void onScanResult(int callbackType, ScanResult result)
		{
			super.onScanResult(callbackType, result);

			if ((result != null) && (result.getDevice() != null))
			  {
				mBTDev  = result.getDevice();
				mBTName = mBTDev.getName();
				mBTAddr = mBTDev.getAddress();

//					BleScannerStopScan();				//	stop scan first
				mBTLeScanner.stopScan(scanCallback);

				callReaderActivity();
			  }
		}

		@Override
		public void onBatchScanResults(List<ScanResult> results)
		{
			super.onBatchScanResults(results);
		}

		@Override
		public void onScanFailed(int errorCode)
		{
			super.onScanFailed(errorCode);
		}
	};

}
