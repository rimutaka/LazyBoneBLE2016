package com.tinysine.lazyboneble;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.tinysine.lazyboneble.service.BluetoothLeService;
import com.tinysine.lazyboneble.util.LogUtil;
import com.tinysine.lazyboneble.util.Util;

@SuppressLint("InflateParams")
public class Bluemd extends Activity {

	private ImageView iv_connect_status;
	private Button btn_connect_name;
	private Button btn_status;

	private boolean isOn = false;

	private boolean isModeConnectSuccessed = false;
	private ModeThread modeThread = null;

	private BluetoothLeService mBluetoothLeService;

	private final ServiceConnection mServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
					.getService();
			if (!mBluetoothLeService.initialize()) {
				LogUtil.e("Unable to initialize Bluetooth");
				finish();
			}
			LogUtil.e("mBluetoothLeService is okay");
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	private boolean isNeedPassword = false;
	private boolean isVerify = false;

	private void resetDefault() {
		isConnected = false;
		isOn = false;
		isNeedPassword = false;
		isModeConnectSuccessed = false;
		iv_connect_status.setImageResource(R.drawable.im_disconnect);
		btn_status.setBackgroundResource(R.drawable.btn_normal);
		btn_connect_name.setText("");
		if (progressDialog != null) {
			progressDialog.dismiss();
		}
		if (pDialog != null) {
			pDialog.cancel();
		}
		if (updateThread != null) {
			updateThread.stopThread();
		}
		if (modeThread != null) {
			modeThread.modeStop();
			modeThread = null;
		}
		if (firstTimeThread != null) {
			firstTimeThread.StopThread();
		}
		if (isConnected && mBluetoothLeService != null) {
			isConnected = false;
			mBluetoothLeService.disconnect();
		}
	}

	private void setButtonStatus() {
		if (isOn) {
			btn_status.setBackgroundResource(R.drawable.btn_selected);
		} else {
			btn_status.setBackgroundResource(R.drawable.btn_normal);
		}
	}

	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				iv_connect_status.setImageResource(R.drawable.im_connecting);
				isConnected = true;
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED
					.equals(action)) {
				resetDefault();
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {
				isConnected = true;
				if (progressDialog != null) {
					progressDialog.dismiss();
				}
				iv_connect_status.setImageResource(R.drawable.im_conneted);
				setConnectName();
				askMode();
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				byte[] datas = intent
						.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
				if (status == Util.BT_DATA_MODE) {
					if (datas != null && datas.length == 7) {
						askMode();
						return;
					}
					if (datas == null || datas.length != 1) {
						askMode();
						return;
					}
					isModeConnectSuccessed = true;
					int value = datas[0] & 0xff;
					if (value == 17) {
						isNeedPassword = true;
						popEditPassword();
					} else {
						isNeedPassword = false;
						sendStatus();
					}
				}
				if (status == Util.BT_DATA_STATUS) {
					if (datas == null || datas.length != 1) {
						return;
					}
					if (progressDialog != null) {
						progressDialog.dismiss();
					}
					int value = datas[0] & 0xff;
					isOn = value == 1 ? true : false;
					setButtonStatus();
				} else if (status == Util.BT_DATA_PASSWORD) {
					if (datas == null || datas.length != 1) {
						return;
					}
					int password = datas[0] & 0xff;
					if (pDialog != null) {
						pDialog.cancel();
					}
					if (password == 1) {
						isVerify = true;
						sendStatus();
						Toast.makeText(Bluemd.this, "Verify successful!",
								Toast.LENGTH_SHORT).show();
						progressDialog = ProgressDialog.show(Bluemd.this,
								"请稍候...", "正在更新状态，请稍候...", true, true);
					} else {
						Toast.makeText(
								Bluemd.this,
								"Verify failed,password incrrect! Please reset your board!",
								Toast.LENGTH_SHORT).show();
						isVerify = false;
						popEditPassword();
					}
				}
			}
		}
	};

	private void askMode() {
		if (!isModeConnectSuccessed) {
			status = Util.BT_DATA_MODE;
			liveData("3C");
			if (modeThread != null) {
				modeThread.modeStop();
				modeThread = null;
			}
			modeThread = new ModeThread();
			modeThread.start();
		}
	}

	public String convertStringToHex(String str) {
		char[] chars = str.toCharArray();
		StringBuffer hex = new StringBuffer();
		for (int i = 0; i < chars.length; i++) {
			hex.append(Integer.toHexString((int) chars[i]));
		}
		return hex.toString();
	}

	private ProgressDialog progressDialog;
	private FirstTimeThread firstTimeThread;
	private boolean isConnected = false;
	public static final String PREFS_NAME = "MyPrefsFile";

	private SharedPreferences preferences;
	private SharedPreferences defalutPreferences;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.ac_main);
		iv_connect_status = (ImageView) findViewById(R.id.iv_connect_status);
		btn_connect_name = (Button) findViewById(R.id.btn_connect_name);
		btn_status = (Button) findViewById(R.id.btn_status);

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			finishDialogNoBluetooth();
			return;
		}
		defalutPreferences = PreferenceManager
				.getDefaultSharedPreferences(this);
		preferences = getSharedPreferences(PREFS_NAME, 0);
		btn_status.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				resetAutoDisconnect();
				boolean isMomentMode = defalutPreferences.getBoolean(
						ModeSettingActivity.KEY_MOMENT_MODE, false);
				if (isMomentMode) {
					if (isOn) {
						liveData("6f");
					} else {
						int value = defalutPreferences.getInt(
								ModeSettingActivity.KEY_FULSE_TIME, 100) / 100;
						String hex = Integer.toHexString(value);
						hex = hex.length() == 1 ? "0" + hex : hex;
						liveData("63" + hex);
					}
				} else {
					if (isOn) {
						liveData("6f");
					} else {
						liveData("65");
					}
				}
			}
		});

		pDialog = new ProgressDialog(this);
		pDialog.setMessage("Verifying...");
		pDialog.setCancelable(false);

		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		LogUtil.e("Try to bindService="
				+ bindService(gattServiceIntent, mServiceConnection,
						BIND_AUTO_CREATE));

		LinearLayout ll_bgLayout = (LinearLayout) findViewById(R.id.ll_bg);
		ll_bgLayout.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				openOptionsMenu();
			}
		});
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter
				.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		intentFilter.addAction(BluetoothDevice.ACTION_UUID);
		return intentFilter;
	}

	private int status = Util.BT_DATA_PASSWORD;

	private void sendStatus() {
		status = Util.BT_DATA_STATUS;
		if (firstTimeThread != null) {
			firstTimeThread.StopThread();
		}
		firstTimeThread = new FirstTimeThread();
		firstTimeThread.start();
	}

	private class FirstTimeThread extends Thread {
		boolean flag = true;

		@Override
		public void run() {
			while (flag) {
				if (isConnected) {
					if (mBluetoothLeService != null) {
						byte[] datas = { 0x5B };
						mBluetoothLeService.WriteBytes(datas);
					}
				}
				try {
					sleep(500);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
				}
			}
		}

		public void StopThread() {
			flag = false;
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		mEnablingBT = false;
	}

	private BluetoothAdapter mBluetoothAdapter = null;
	private boolean mEnablingBT = false;

	private static final int REQUEST_ENABLE_BT = 2;

	@Override
	public synchronized void onResume() {
		super.onResume();
		if (!mEnablingBT) {
			if ((mBluetoothAdapter != null) && (!mBluetoothAdapter.isEnabled())) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(R.string.alert_dialog_turn_on_bt)
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setTitle(R.string.alert_dialog_warning_title)
						.setCancelable(false)
						.setPositiveButton(R.string.alert_dialog_yes,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										mEnablingBT = true;
										Intent enableIntent = new Intent(
												BluetoothAdapter.ACTION_REQUEST_ENABLE);
										startActivityForResult(enableIntent,
												REQUEST_ENABLE_BT);
									}
								})
						.setNegativeButton(R.string.alert_dialog_no,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										finishDialogNoBluetooth();
									}
								});
				AlertDialog alert = builder.create();
				alert.show();
			}
		}
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
	}

	private void finishDialogNoBluetooth() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.alert_dialog_no_bt)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(R.string.app_name)
				.setCancelable(false)
				.setPositiveButton(R.string.alert_dialog_ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								finish();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		// TODO Auto-generated method stub
		menu.clear();
		MenuInflater inflater = getMenuInflater();
		if (isNeedPassword) {
			inflater.inflate(R.menu.option_menu, menu);
		} else {
			inflater.inflate(R.menu.menu_nopass, menu);
		}
		return true;
	}

	private static final int REQUEST_CONNECT_DEVICE = 1;

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.connect: {
			resetDefault();
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
		}
			return true;
		case R.id.preferences: {
			Bluemd.this.finish();
		}
			return true;
		case R.id.mode: {
			if (!isConnected) {
				showText("Information", "Please connect the device first!");
				break;
			}
			Intent intent = new Intent(Bluemd.this, ModeSettingActivity.class);
			startActivity(intent);
		}
			return true;
		case R.id.changepassword: {
			if (!isConnected) {
				showText("Information", "Please connect the device first!");
				break;
			}
			if (!isVerify) {
				showText("Information", "Please verify the password first!");
				break;
			}
			LayoutInflater layoutInflater = LayoutInflater.from(this);
			final View myLoginView = layoutInflater.inflate(
					R.layout.ac_password, null);
			AlertDialog.Builder dlgChgPsd = new AlertDialog.Builder(this);
			AlertDialog dialog = dlgChgPsd
					.setTitle("Change Password")
					.setIcon(android.R.drawable.ic_dialog_info)
					.setView(myLoginView)
					.setCancelable(false)
					.setPositiveButton("OK",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface arg0,
										int arg1) {
									// TODO Auto-generated method stub
									isVerify = true;
									String strOrgPsd = ((EditText) (myLoginView
											.findViewById(R.id.orgpsd)))
											.getText().toString();
									String strNewPsd = ((EditText) (myLoginView
											.findViewById(R.id.newpsd)))
											.getText().toString();
									String strNewPsd2 = ((EditText) (myLoginView
											.findViewById(R.id.newpsd2)))
											.getText().toString();
									if (!strOrgPsd.equals(strInputPsd)) {
										showText("Information",
												"The original password is not correct!");
									} else if (strNewPsd.equals("")) {
										showText("Information",
												"The password cannot be empty!");
									} else if (strNewPsd.length() != 6) {
										showText("Information",
												"The password must be 6 digits!");
									} else {
										if (!strNewPsd.equals(strNewPsd2)) {
											showText("Information",
													"The passwords are not same!");
										} else {
											resetPassword("40"
													+ int2Byte(strNewPsd));
											showText("Information",
													"The password has been changed successfully!");
										}
									}
								}
							})
					.setNegativeButton("Quit",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface arg0,
										int arg1) {
									// TODO Auto-generated method stub
									isVerify = true;
								}
							}).create();
			dialog.show();
			return true;
		}
		}
		return false;
	}

	private void showText(String title, String message) {
		AlertDialog.Builder builder = new Builder(Bluemd.this);
		builder.setIcon(android.R.drawable.ic_dialog_info);
		builder.setTitle(title);
		builder.setMessage(message);
		builder.setPositiveButton("OK", null);
		builder.show();
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			if (resultCode == RESULT_OK) {
				mConnectedDeviceAddress = data
						.getStringExtra(DeviceListActivity.DEVICE_ADDRESS);
				live(mConnectedDeviceAddress);
			}
			break;
		case REQUEST_ENABLE_BT:
			if ((mBluetoothAdapter != null) && (!mBluetoothAdapter.isEnabled())) {
				finishDialogNoBluetooth();
			}
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (firstTimeThread != null) {
			firstTimeThread.StopThread();
		}
		unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		unregisterReceiver(mGattUpdateReceiver);
	}

	private String mConnectedDeviceAddress = null;

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Do you exit program?")
					.setIcon(android.R.drawable.ic_dialog_info)
					.setTitle(R.string.app_name)
					.setCancelable(false)
					.setPositiveButton("Yes",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									android.os.Process
											.killProcess(android.os.Process
													.myPid());
									Bluemd.this.finish();
								}
							})
					.setNegativeButton("No",
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
										int which) {
								}

							});
			AlertDialog alert = builder.create();
			alert.show();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	private void sendData(String hexValue) {
		if (!isConnected)
			return;
		if (mBluetoothLeService != null) {
			mBluetoothLeService.WriteString(hexValue);
		}
	}

	private void liveData(String hexValue) {
		if (!isConnected) {
			resetDefault();
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
		} else {
			sendData(hexValue);
		}
	}

	private void live(String address) {
		mBluetoothLeService.connect(address);
		progressDialog = ProgressDialog.show(this, "Tip", "Connecting...");
	}

	private void sendPassword(String pasword) {
		status = Util.BT_DATA_PASSWORD;
		sendData(pasword);
	}

	private void resetPassword(String pasword) {
		sendData(pasword);
	}

	private ProgressDialog pDialog;
	public static String strInputPsd = "";

	private void popEditPassword() {
		final EditText eText = new EditText(this);
		InputFilter[] filters = { new InputFilter.LengthFilter(6) };
		eText.setFilters(filters);
		eText.setInputType(InputType.TYPE_CLASS_NUMBER);
		eText.setTransformationMethod(PasswordTransformationMethod
				.getInstance());

		new AlertDialog.Builder(this)
				.setTitle("Please input the password!")
				.setIcon(android.R.drawable.ic_dialog_info)
				.setView(eText)
				.setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						// TODO Auto-generated method stub
						String pa = eText.getText().toString();
						if (!pa.equals("") && pa.length() == 6) {
							pDialog.show();
							strInputPsd = pa;
							String paString = "3F" + int2Byte(pa);
							sendPassword(paString);
						} else {
							Toast.makeText(Bluemd.this,
									"Password is 6 digits!", Toast.LENGTH_SHORT)
									.show();
							popEditPassword();
						}
					}
				})
				.setNegativeButton("Quit",
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface arg0, int arg1) {
								// TODO Auto-generated method stub
								resetDefault();
							}
						}).create().show();
	}

	private String int2Byte(String value) {
		int va = Integer.parseInt(value);
		String hex = Integer.toHexString(va);
		int len = hex.length();
		String data = "000000";
		data = data.substring(0, 6 - len) + hex;
		String result = data.substring(4, 6) + data.substring(2, 4)
				+ data.substring(0, 2);

		return result;
	}

	private void setConnectName() {
		String connectName = preferences.getString(mConnectedDeviceAddress, "");
		btn_connect_name.setText(connectName);
	}

	private void resetAutoDisconnect() {
		boolean isAutoMode = defalutPreferences.getBoolean(
				ModeSettingActivity.KEY_AUTO_MODE, false);
		if (isAutoMode) {
			if (updateThread == null) {
				updateThread = new UpdateThread();
				updateThread.start();
			}
			updateThread.reset();
		}
	}

	private UpdateThread updateThread = null;

	private Handler mHandler = new Handler();

	private class UpdateThread extends Thread {
		boolean flag = true;
		int count = 0;

		@Override
		public void run() {
			// TODO Auto-generated method stub
			super.run();
			while (flag) {
				int value = defalutPreferences.getInt(
						ModeSettingActivity.KEY_AUTO_TIME, 1);
				if (count == value) {
					mHandler.post(new Runnable() {
						@Override
						public void run() {
							// TODO Auto-generated method stub
							boolean isAutoMode = defalutPreferences.getBoolean(
									ModeSettingActivity.KEY_AUTO_MODE, false);
							if (isAutoMode) {
								resetDefault();
							}
						}
					});
				}
				count++;
				try {
					sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
				}
			}
		}

		public void reset() {
			count = 0;
		}

		public void stopThread() {
			flag = false;
			count = 0;
		}
	}

	public void onModifyName(View v) {
		final EditText eText = new EditText(this);
		new AlertDialog.Builder(this).setTitle("Please input new name")
				.setIcon(android.R.drawable.ic_dialog_info).setView(eText)
				.setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						// TODO Auto-generated method stub
						String newName = eText.getText().toString();
						if (!newName.equals("")) {
							Editor editor = preferences.edit();
							editor.putString(mConnectedDeviceAddress, newName);
							editor.commit();
							setConnectName();
						}
					}
				}).setNegativeButton("Cancel", null).create().show();
	}

	private class ModeThread extends Thread {

		private boolean isStop = false;

		@Override
		public void run() {
			// TODO Auto-generated method stub
			super.run();
			try {
				sleep(3000);
				if (!isStop && !isModeConnectSuccessed) {
					sendStatus();
				}
			} catch (Exception e) {
				// TODO: handle exception
			}
		}

		public void modeStop() {
			isStop = true;
		}

	}

}