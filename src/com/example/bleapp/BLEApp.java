package com.example.bleapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

@SuppressLint("NewApi")
public class BLEApp extends Activity {

	TextView myView;
	BluetoothManager btManager;
	BluetoothAdapter btAdapter;
	BluetoothLeScanner mLescanner;
	private ScanCallback mLeScanCallback;
	
	private boolean toRun = false;
	
	private static final int SCAN_INTERVAL_MS = 1000;

    private Handler scanHandler = new Handler();
    private List<ScanFilter> scanFilters = new ArrayList<ScanFilter>();
    private ScanSettings scanSettings;
    private boolean isScanning = false;
	
    private BluetoothDevice keyFobDevice;
    private BluetoothGatt bluetoothGatt;
    
    //To BUZZ
    UUID PROXServiceUUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
	UUID PROXCharacUUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");
	
	//To read the current level
	UUID KEYServiceUUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
	UUID KEYCharacUUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
	UUID KEYDescriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	
    private static final int NUM_LEVELS = 16-1;//0 to 15
    Handler mainUIHandler;
    
    ProgressDialog pd;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_bleapp);
		
		getActionBar().setTitle("BLE Dimmer");
		
		myView = (TextView)findViewById(R.id.myView);		
		myView.setMovementMethod(new ScrollingMovementMethod());
		
		setConnectionStatus(false);
		
		pd = new ProgressDialog(this);
		pd.setMessage("Operating, Please Wait..");		
		
		Button enable = (Button)findViewById(R.id.button1);
		enable.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				myView.append("\nBeginning the Scan\n");
				beginScanning();
			}
		});
		
		Button buzz = (Button)findViewById(R.id.button3);
		buzz.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				myView.append("\nBUZZing\n");
				
				byte[] data = {0x01};
				try {
					bluetoothGatt.getService(PROXServiceUUID).getCharacteristic(PROXCharacUUID).setValue(data);
					bluetoothGatt.writeCharacteristic(bluetoothGatt.getService(PROXServiceUUID).getCharacteristic(PROXCharacUUID));
				}
				catch(Exception obj) {
					Log.d("#btleGattCallback", "BUZZ exception\n");
				}
			}
		});
		Button UP = (Button)findViewById(R.id.button5);
		UP.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {	        	
				// TODO Auto-generated method stub
				myView.append("\nIncreasing key press\n");
				
		    	Button upButton  = (Button)findViewById(R.id.button5);
		    	Button downButton  = (Button)findViewById(R.id.button4);
		    	upButton.setEnabled(false);
		    	downButton.setEnabled(false);
		    	
		    	pd.show();
		    	
				byte[] data = {(byte)0x02}; //Increase key press
				
				try {
					BluetoothGattDescriptor myDesc =  bluetoothGatt.getService(KEYServiceUUID).getCharacteristic(KEYCharacUUID).getDescriptor(KEYDescriptorUUID);
					myDesc.setValue(data);
					bluetoothGatt.writeDescriptor(myDesc);
				}
				catch(Exception obj) {
					Log.d("#btleGattCallback", "UP exception\n");
				}
			}
		});
		Button DOWN = (Button)findViewById(R.id.button4);
		DOWN.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				myView.append("\nDecreasing key press	\n");
				
				Button upButton  = (Button)findViewById(R.id.button5);
		    	Button downButton  = (Button)findViewById(R.id.button4);
		    	upButton.setEnabled(false);
		    	downButton.setEnabled(false);
		    	
		    	pd.show();
		    	
				byte[] data = {(byte)0x03}; //Decrease key press
				
				try {
					BluetoothGattDescriptor myDesc =  bluetoothGatt.getService(KEYServiceUUID).getCharacteristic(KEYCharacUUID).getDescriptor(KEYDescriptorUUID);
					myDesc.setValue(data);
					bluetoothGatt.writeDescriptor(myDesc);
				}
				catch(Exception obj) {
					Log.d("#btleGattCallback", "DOWN exception\n");
				}
			}
		});
		
		Button disable = (Button)findViewById(R.id.button2);
		disable.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				myView.append("Stopping the Scan\n");
				stopScanning();
			}
		});
		
		btManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
		
		btAdapter = btManager.getAdapter();
		
		mLescanner = btAdapter.getBluetoothLeScanner();
		if(btAdapter != null && !btAdapter.isEnabled()) {
			Log.d("#BLEAppActivity", "BT service not enabled");
			myView.append("\nBT service not enabled\n");
			
			Intent enableBtIntent = new Intent (BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, 1);
		}
		else {
			Log.d("#BLEAppActivity", "BT service is enabled");
			myView.append("\nBT service is enabled\n");
		}
			
			
		mLeScanCallback = new ScanCallback() {
	        @Override
	        public void onScanResult(int callbackType, ScanResult result) {
	        	stopScanning();
	        	keyFobDevice = result.getDevice();
	            runOnUiThread(new Runnable() {
	                @Override
	                public void run() {
	                	myView.append("Found device -"+keyFobDevice.getName()+"\n");
	                	
	                	Log.d("#BLEAppActivity", "\nConnecting to GATT\n");
	                	myView.append("Connecting to GATT\n");
	                	bluetoothGatt = keyFobDevice.connectGatt(getBaseContext(), false, btleGattCallback);
	                }
	            });
	        }

	        @Override
	        public void onBatchScanResults(List<ScanResult> results) {
	        	myView.append("onBatchScanResults");
	        }

	        @Override
	        public void onScanFailed(int errorCode) {
	        	myView.append("onScanFailed");
	        }
	    };
		
	    Set<BluetoothDevice> bondedDevices = btAdapter.getBondedDevices ();
		
		for (BluetoothDevice bluetoothDevice : bondedDevices) {
			Log.d("#BLEAppActivity", "bonded device "+bluetoothDevice.getName()+"\n");
			myView.append("bonded device "+bluetoothDevice.getName()+"\n");
		}
		
		mainUIHandler = new Handler(getMainLooper()) {
	    	@Override
	    	public void handleMessage(Message msg) {
	    		// TODO Auto-generated method stub
	    		super.handleMessage(msg);
	    		Log.d("#mainUIhandler", msg.obj.toString());
	    		if(msg.obj.toString().equals(String.format("#CONNECTION_UPDATE_TRUE#"))) {
	    			Log.d("#mainUIhandler", "setConnectionStatus true");
	    			setConnectionStatus(true);
	    		}
	    		else if(msg.obj.toString().equals(String.format("#CONNECTION_UPDATE_FALSE#"))) {
	    			Log.d("#mainUIhandler", "setConnectionStatus false");
	    			setConnectionStatus(false);
	    		}
	    		else if(msg.obj.toString().equals(String.format("#PERCENTAGE_UPDATE#"))) {
	    			Log.d("#mainUIhandler", "setConnectionStatus false");
	    			refreshView(msg.arg1);
	    		}
	    		else {
	    			myView.append(msg.obj.toString()+"\n");
	    		}
	    	}
	    };
	}
	
	@Override
	public void onActivityReenter(int resultCode, Intent data) {
		// TODO Auto-generated method stub
		super.onActivityReenter(resultCode, data);
		Log.d("#BLEAppActivity", "BT service is enabled");
		myView.append("\nBT service is enabled\n");		
	}
	public void beginScanning() {
        ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
        scanSettingsBuilder.setScanMode(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
        scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        scanSettings = scanSettingsBuilder.build();

        //this.toRun = true;
        
        btAdapter.cancelDiscovery();
        
        //scanHandler.post(scanRunnable);
        
        //Debug
        myView.append("Building a device with MAC-"+"78:C5:E5:9F:F2:48"+"\n");
    	keyFobDevice = btAdapter.getRemoteDevice("78:C5:E5:9F:F2:48");
        
    	if(keyFobDevice!=null) {
    		myView.append("Connecting to GATT\n");
        	bluetoothGatt = keyFobDevice.connectGatt(getBaseContext(), false, btleGattCallback);
    	}
    }
	
	public void stopScanning() {
        this.toRun = false;
        if(bluetoothGatt != null) {
        	myView.append("BT discovery stopped\n");
        	bluetoothGatt.disconnect();
			bluetoothGatt.close();
			setConnectionStatus(false);
        }
    }

    private Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            BluetoothLeScanner scanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
            
            Log.d("#BLEAppActivity", "BT scanning run, toRun-"+toRun);
            if (isScanning) {
            	Log.d("#BLEAppActivity", "BT scanning stopping");
    			//myView.append("BT scanning stopping\n");
                scanner.stopScan(mLeScanCallback);
            } else {
            	Log.d("#BLEAppActivity", "BT scanning started");
    			//myView.append("BT scanning started\n");
                scanner.startScan(scanFilters, scanSettings, mLeScanCallback);
            }

            isScanning = !isScanning;

            if(toRun)
            	scanHandler.postDelayed(this, SCAN_INTERVAL_MS);
        }
    };
    
    private void setConnectionStatus(boolean state) {
    	TextView stateView = (TextView)findViewById(R.id.connStatus);
    	Button upButton  = (Button)findViewById(R.id.button5);
    	Button downButton  = (Button)findViewById(R.id.button4);
    	
    	Button connectButton  = (Button)findViewById(R.id.button1);
    	Button disconnectButton  = (Button)findViewById(R.id.button2);
    	Button buzzButton  = (Button)findViewById(R.id.button3);
    	if(state) {
    		upButton.setEnabled(true);
    		downButton.setEnabled(true);
    		disconnectButton.setEnabled(true);
    		buzzButton.setEnabled(true);
    		
    		connectButton.setEnabled(false);
    		
    		stateView.setText("Status:CONNECTED");
    	}
    	else {
    		upButton.setEnabled(false);
    		downButton.setEnabled(false);
    		disconnectButton.setEnabled(false);
    		buzzButton.setEnabled(false);
    		
    		connectButton.setEnabled(true);
    		stateView.setText("Status:DISCONNECTED");
    	}
    }
    private void refreshView(int slevel) {
    	Button UPb = (Button)findViewById(R.id.button5);
    	Button DOWNb = (Button)findViewById(R.id.button4);
    	
    	if(slevel <= 0) {
    		DOWNb.setEnabled(false);
    	}
    	else {
    		DOWNb.setEnabled(true);
    	}
    	if(slevel >= NUM_LEVELS) {
    		UPb.setEnabled(false);
    	}
    	else {
    		UPb.setEnabled(true);
    	}
    	
    	float percentage = ((float)slevel/(float)NUM_LEVELS) * (float)100;
    	
    	ProgressBar intenityLevel = (ProgressBar)findViewById(R.id.progressBar1);
    	intenityLevel.setProgress((int)percentage);
    	
    	//set battery percentage
    	TextView percentageView = (TextView)findViewById(R.id.percentagetext);
    	percentageView.setText(String.format("Percentage:%.2f", percentage));
    }
    
    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {
    	public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
    		// this will get called anytime you perform a read or write characteristic operation
        	String string = new String();
        	string = string.concat("Rx notification - Val="+gatt.getService(KEYServiceUUID).getCharacteristic(KEYCharacUUID).getDescriptor(KEYDescriptorUUID).getUuid().toString());
        	
        	
        	BluetoothGattCharacteristic myChar = gatt.getService(KEYServiceUUID).getCharacteristic(KEYCharacUUID);
        	
        	string = string.concat("Enabling Notification\n");
        	if(bluetoothGatt.setCharacteristicNotification(myChar, true)) {
        		string = string.concat("Notification enabled\n");
        		
        		string = string.concat("writing enable value\n");
        		BluetoothGattDescriptor clientConfig = myChar.getDescriptor(KEYDescriptorUUID);
        		clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        		// or
        		//clientConfig.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        		bluetoothGatt.writeDescriptor(clientConfig);
        		
        		string = string.concat("key press characterstic read - Set up completed\n");

            	Log.d("#btleGattCallback", "Ready to Operate\n");
    			string = string.concat("\nReady to Operate\n");
        	}
        	else {
        		string = string.concat("Could not enable Notification");
        	}
        	
        	Message msg = new Message();
        	msg.obj = string;
        	mainUIHandler.sendMessage(msg);
    	};
    	
    	public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
    		// this will get called anytime you perform a read or write characteristic operation
        	String string = new String();
        	string = string.concat("onDescriptorWrite\n");
        	string = string.concat("onValue="+descriptor.getValue()[0]+"\n");
        	Message msg = new Message();
        	msg.obj = string;
        	mainUIHandler.sendMessage(msg);
			
    	};
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // this will get called anytime you perform a read or write characteristic operation
        	Log.d("#btleGattCallback", "onCharacteristicChanged");
        	String string = new String();
        	string = string.concat("onCharacteristicChanged\n");
        	
        	string = string.concat("value="+characteristic.getValue()[0]+"\n");
        	//refreshView(characteristic.getValue()[0]);
        	
        	Message msg = new Message();
        	msg.obj = string;
        	mainUIHandler.sendMessage(msg);
        	
        	Message msg1 = new Message();
        	msg1.obj = String.format("#PERCENTAGE_UPDATE#");
        	msg1.arg1 = characteristic.getValue()[0];
        	mainUIHandler.sendMessage(msg1);
        	
        	if(pd.isShowing()) {
        		pd.dismiss();
        	}
        }
     
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
        	String string = new String();
            // this will get called when a device connects or disconnects
        	if(newState == BluetoothProfile.STATE_CONNECTED) {
        		Log.d("#btleGattCallback", "GATT connected\n");
        		string = string.concat("GATT connected\n");
        		
        		Log.d("#btleGattCallback", "Starting service discovery\n");
        		
        		string = string.concat("\nStarting service discovery\n");
        		bluetoothGatt.discoverServices();
        	}
        	else {
        		Log.d("#btleGattCallback", "GATT not connected, state="+newState+"\n");
        		string = string.concat("GATT not connected, state="+newState+"\n");
        		
        		Message msg = new Message();
    			msg.obj = String.format("#CONNECTION_UPDATE_FALSE#");
    			mainUIHandler.sendMessage(msg);
        	}
        	Message msg = new Message();
        	msg.obj = string;
        	mainUIHandler.sendMessage(msg);
        }
     
        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) { 
            // this will get called after the client initiates a            BluetoothGatt.discoverServices() call
        	String string = new String();
        	for (BluetoothGattService service : gatt.getServices()) {
        		Log.d("#btleGattCallback", "Discovered service -"+service.getUuid()+"\n");
        		string = string.concat("\nDiscovered service -"+service.getUuid()+"\n");
        		List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
        		for(BluetoothGattCharacteristic characteristic : characteristics) {
        			Log.d("#btleGattCallback", "Characteristic = "+characteristic.getUuid()+"\n");
        			string = string.concat("Characteristic = "+characteristic.getUuid()+"\n");
        			for(BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
        				Log.d("#btleGattCallback", "descriptor = "+descriptor.getUuid()+"\n");
            			string = string.concat("descriptor = "+descriptor.getUuid()+"\n");
        			}
        		}
        	}
        	Log.d("#btleGattCallback", "Service Discovery complete\n");
			string = string.concat("\nService Discovery complete\n");
			
			Message msg = new Message();
			msg.obj = String.format("#CONNECTION_UPDATE_TRUE#");
			mainUIHandler.sendMessage(msg);
			
			Log.d("#btleGattCallback", "Setting up key press characterstic read\n");
			string = string.concat("Setting up key press characterstic read\n");
			BluetoothGattDescriptor myBtDesc = bluetoothGatt.getService(KEYServiceUUID).getCharacteristic(KEYCharacUUID).getDescriptor(KEYDescriptorUUID);
			bluetoothGatt.readDescriptor(myBtDesc);
			
			Message msg2 = new Message();
        	msg2.obj = string;
        	mainUIHandler.sendMessage(msg2);
        }
        
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        	Log.d("#btleGattCallback", "onCharacteristicWrite\n");
    		//bluetoothGatt.readCharacteristic(bluetoothGatt.getService(PROXServiceUUID).getCharacteristic(PROXCharacUUID));
    		//Log.d("#btleGattCallback", "Reading Key\n");
			//bluetoothGatt.readCharacteristic(bluetoothGatt.getService(KEYServiceUUID).getCharacteristic(KEYCharacUUID));
        	
        	
        	String string = new String();
        	string = string.concat("onCharacteristicWrite\n");
			string = string.concat("onValue="+characteristic.getValue()[0]+"\n");
        	//level = characteristic.getValue()[0];
        	//refreshView(level);
			Message msg = new Message();
        	msg.obj = string;
        	mainUIHandler.sendMessage(msg);
        };
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        	String string = new String();
        	Log.d("#btleGattCallback", "onCharacteristicRead\n");
			string = string.concat("\nonCharacteristicRead "+characteristic.getUuid().toString()+"\n");
        	//level = characteristic.getValue()[0];
        	//refreshView(level);
			Message msg = new Message();
        	msg.obj = string;
        	mainUIHandler.sendMessage(msg);
        };
    };
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.bleapp, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		
		//Stopping device discovery
		//Log.d("#BLEAppActivity", "BT discovery stopped");
		//myView.append("BT discovery stopped\n");
		//mLescanner.stopScan(mLeScanCallback);
	}
	
	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		if(bluetoothGatt != null) {
			myView.append("BT discovery stopped\n");
			bluetoothGatt.disconnect();
			bluetoothGatt.close();
		}
	}
}
