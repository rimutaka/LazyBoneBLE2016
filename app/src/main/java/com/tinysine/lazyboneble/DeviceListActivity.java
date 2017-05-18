/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tinysine.lazyboneble;

import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.tinysine.lazyboneble.adapter.LeDeviceListAdapter;
import com.tinysine.lazyboneble.util.BLEDevice;

public class DeviceListActivity extends Activity {

	private BluetoothAdapter mBluetoothAdapter;
	private boolean mScanning;

	private static final long SCAN_PERIOD = 10000;

	public static String DEVICE_ADDRESS = "device_address";
	public static String DEVICE_NAME = "device_name";

	private LeDeviceListAdapter mPairedDevicesArrayAdapter;
	private LeDeviceListAdapter mNewDevicesArrayAdapter;

	private SharedPreferences preferences;
	private Set<BluetoothDevice> pairedDevices;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.device_list);
		preferences = getSharedPreferences(Bluemd.PREFS_NAME, 0);
		setResult(Activity.RESULT_CANCELED);
		Button scanButton = (Button) findViewById(R.id.button_scan);
		scanButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				doDiscovery();
				v.setVisibility(View.GONE);
			}
		});
		mPairedDevicesArrayAdapter = new LeDeviceListAdapter(this);
		mNewDevicesArrayAdapter = new LeDeviceListAdapter(this);
		ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
		pairedListView.setAdapter(mPairedDevicesArrayAdapter);
		pairedListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				// TODO Auto-generated method stub
				mBluetoothAdapter.stopLeScan(mLeScanCallback);
				BLEDevice de = mPairedDevicesArrayAdapter.getDevice(arg2);
				String address = de.getAddress();
				if (address == null || address.equals("")) {
					return;
				}
				Intent intent = new Intent();
				intent.putExtra(DEVICE_NAME, de.getName());
				intent.putExtra(DEVICE_ADDRESS, address);
				setResult(RESULT_OK, intent);
				finish();
			}
		});

		ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
		newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
		newDevicesListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				// TODO Auto-generated method stub
				mBluetoothAdapter.stopLeScan(mLeScanCallback);
				BLEDevice de = mNewDevicesArrayAdapter.getDevice(arg2);
				String address = de.getAddress();
				if (address == null || address.equals("")) {
					return;
				}
				Intent intent = new Intent();
				intent.putExtra(DEVICE_NAME, de.getName());
				intent.putExtra(DEVICE_ADDRESS, address);
				setResult(RESULT_OK, intent);
				finish();
			}
		});

		if (!getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, "Bluetooth not supported.", Toast.LENGTH_SHORT)
					.show();
			finish();
		}

		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		pairedDevices = mBluetoothAdapter.getBondedDevices();

		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth not supported.", Toast.LENGTH_SHORT)
					.show();
			finish();
			return;
		}
		if (pairedDevices.size() > 0) {
			findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
			for (BluetoothDevice device : pairedDevices) {
				String nameString = device.getName();
				String address = device.getAddress();
				nameString = preferences.getString(address, nameString);
				Editor editor = preferences.edit();
				editor.putString(address, nameString);
				editor.commit();
				BLEDevice de = new BLEDevice();
				de.setName(nameString);
				de.setAddress(address);
				mPairedDevicesArrayAdapter.addDevice(de);
			}
		} else {
			String noDevices = getResources().getText(R.string.none_paired)
					.toString();
			BLEDevice de = new BLEDevice(noDevices, "");
			mPairedDevicesArrayAdapter.addDevice(de);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		stopFind();
	}

	/**
	 * Start device discover with the BluetoothAdapter
	 */
	private void doDiscovery() {

		// Indicate scanning in the title
		setProgressBarIndeterminateVisibility(true);
		setTitle(R.string.scanning);

		// Turn on sub-title for new devices
		findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

		// If we're already discovering, stop it
		if (mBluetoothAdapter.isDiscovering()) {
			mBluetoothAdapter.cancelDiscovery();
		}

		scanLeDevice(true);
	}

	private Handler mHandler = new Handler();

	private void scanLeDevice(final boolean enable) {
		if (enable) {
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (mScanning) {
						stopFind();
					}
				}
			}, SCAN_PERIOD);
			mScanning = true;
			mBluetoothAdapter.startLeScan(mLeScanCallback);
		} else {
			stopFind();
		}
	}

	private void stopFind() {
		mScanning = false;
		mBluetoothAdapter.stopLeScan(mLeScanCallback);
		setProgressBarIndeterminateVisibility(false);
		setTitle(R.string.select_device);
		if (mNewDevicesArrayAdapter.getCount() == 0) {
			String noDevices = getResources().getText(R.string.none_found)
					.toString();
			BLEDevice de = new BLEDevice(noDevices, "");
			mNewDevicesArrayAdapter.addDevice(de);
		}
	}

	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi,
				final byte[] scanRecord) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
						String nameString = device.getName();
						String address = device.getAddress();
						nameString = preferences.getString(address, nameString);
						Editor editor = preferences.edit();
						editor.putString(address, nameString);
						editor.commit();
						BLEDevice de = new BLEDevice();
						de.setName(nameString);
						de.setAddress(address);
						mPairedDevicesArrayAdapter.addDevice(de);
					}
				}
			});
		}
	};

}
