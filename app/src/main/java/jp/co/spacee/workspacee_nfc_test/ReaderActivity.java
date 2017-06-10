package jp.co.spacee.workspacee_nfc_test;

import java.io.UnsupportedEncodingException;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.acs.bluetooth.Acr1255uj1Reader;
import com.acs.bluetooth.Acr1255uj1Reader.OnBatteryLevelAvailableListener;
import com.acs.bluetooth.Acr1255uj1Reader.OnBatteryLevelChangeListener;
import com.acs.bluetooth.BluetoothReader;
import com.acs.bluetooth.BluetoothReader.OnAtrAvailableListener;
import com.acs.bluetooth.BluetoothReader.OnAuthenticationCompleteListener;
import com.acs.bluetooth.BluetoothReader.OnCardPowerOffCompleteListener;
import com.acs.bluetooth.BluetoothReader.OnCardStatusAvailableListener;
import com.acs.bluetooth.BluetoothReader.OnCardStatusChangeListener;
import com.acs.bluetooth.BluetoothReader.OnDeviceInfoAvailableListener;
import com.acs.bluetooth.BluetoothReader.OnEnableNotificationCompleteListener;
import com.acs.bluetooth.BluetoothReader.OnEscapeResponseAvailableListener;
import com.acs.bluetooth.BluetoothReader.OnResponseApduAvailableListener;
import com.acs.bluetooth.BluetoothReaderGattCallback;
import com.acs.bluetooth.BluetoothReaderGattCallback.OnConnectionStateChangeListener;
import com.acs.bluetooth.BluetoothReaderManager;
import com.acs.bluetooth.BluetoothReaderManager.OnReaderDetectionListener;

public class ReaderActivity extends Activity
{
    public static final String TAG                              = ReaderActivity.class.getSimpleName();
    public static final String EXTRAS_DEVICE_NAME             = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS          = "DEVICE_ADDRESS";

    /* Default master key. */
    private static final String DEFAULT_1255_MASTER_KEY      = "ACR1255U-J1 Auth";

    /* Read 16 bytes from the binary block 0x04 (MIFARE 1K or 4K). */
//    private static final String DEFAULT_1255_APDU_COMMAND   = "FF B0 00 04 01";
    private static final String DEFAULT_1255_APDU_COMMAND   = "FF CA 00 00 00";
    /* Get firmware version escape command. */
    private static final String DEFAULT_1255_ESCAPE_COMMAND = "E0 00 00 18 00";

    private static final byte[] AUTO_POLLING_START = { (byte) 0xE0, 0x00, 0x00, 0x40, 0x01 };
    private static final byte[] AUTO_POLLING_STOP  = { (byte) 0xE0, 0x00, 0x00, 0x40, 0x00 };

    /* String keys for save/restore instance state. */
    private static final String ATR_STRING               = "atr";
    private static final String AUTHENTICATION_KEY      = "authenticatinKey";
    private static final String APDU_COMMAND             = "apduCommand";
    private static final String RESPONSE_APDU            = "responseApdu";
    private static final String ESCAPE_COMMAND          = "escapeCommand";
    private static final String ESCAPE_RESPONSE         = "escapeResponse";

    private static final String SLOT_STATUS_STRING = "cardStatus";
    private static final String SLOT_STATUS_2_STRING = "cardStatus2";

    /* Reader to be connected. */
    private String mDeviceName;
    private String mDeviceAddress;
    private int mConnectState = BluetoothReader.STATE_DISCONNECTED;

    /* UI control */
    private Button mClear;
    private Button mAuthentication;
    private Button mStartPolling;
    private Button mStopPolling;
    private Button mPowerOn;
    private Button mPowerOff;
    private Button mTransmitApdu;
    private Button mTransmitEscape;
    private Button mGetCardStatus;

    private                 boolean        status_AutoDetect         = false;
    private                 boolean        status_CardPower          = false;
    private                 Button          mStartAutoDetect          = null;
    private                 Button          mStopAutoDetect           = null;
    private                 TextView        mTxtIDm1                   = null;
    private                 TextView        mTxtIDm2                   = null;
    private                 TextView        mTxtIDm3                   = null;
    private                 TextView        mTxtIDm4                   = null;
    private                 Handler         mHandler                   = new Handler();

    private TextView mTxtConnectionState;
    private TextView mTxtAuthentication;
    private TextView mTxtATR;
    private TextView mTxtSlotStatus;
    private TextView mTxtResponseApdu;
    private TextView mTxtEscapeResponse;
    private TextView mTxtCardStatus;

    private EditText mEditMasterKey;
    private EditText mEditApdu;
    private EditText mEditEscape;

    /* Detected reader. */
    private BluetoothReader mBluetoothReader;
    /* ACS Bluetooth reader library. */
    private BluetoothReaderManager mBluetoothReaderManager;
    private BluetoothReaderGattCallback mGattCallback;

    private ProgressDialog mProgressDialog;

    /* Bluetooth GATT client. */
    private BluetoothGatt mBluetoothGatt;

    /*
     * Listen to Bluetooth bond status change event. And turns on reader's
     * notifications once the card reader is bonded.
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
    {

        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void onReceive(Context context, Intent intent) {
            BluetoothAdapter bluetoothAdapter = null;
            BluetoothManager bluetoothManager = null;
            final String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                Log.i(TAG, "ACTION_BOND_STATE_CHANGED");

                /* Get bond (pairing) state */
                if (mBluetoothReaderManager == null) {
                    Log.w(TAG, "Unable to initialize BluetoothReaderManager.");
                    return;
                }

                bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                if (bluetoothManager == null) {
                    Log.w(TAG, "Unable to initialize BluetoothManager.");
                    return;
                }

                bluetoothAdapter = bluetoothManager.getAdapter();
                if (bluetoothAdapter == null) {
                    Log.w(TAG, "Unable to initialize BluetoothAdapter.");
                    return;
                }

                final BluetoothDevice device = bluetoothAdapter
                        .getRemoteDevice(mDeviceAddress);

                if (device == null) {
                    return;
                }

                final int bondState = device.getBondState();

                // TODO: remove log message
                Log.i(TAG, "BroadcastReceiver - getBondState. state = "
                        + getBondingStatusString(bondState));

                /* Progress Dialog */
                if (bondState == BluetoothDevice.BOND_BONDING) {
                    mProgressDialog = ProgressDialog.show(context,
                            "ACR3901U-S1", "Bonding...");
                } else {
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }
                }

                /*
                 * Update bond status and show in the connection status field.
                 */
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTxtConnectionState
                                .setText(getBondingStatusString(bondState));
                    }
                });
            }
        }

    };

    /* Clear the Card reader's response and notification fields. */
    private void clearAllUi() {
        /* Clear notification fields. */
        mTxtCardStatus.setText(R.string.noData);
        mTxtAuthentication.setText(R.string.noData);

        /* Clear card reader's response fields. */
        clearResponseUi();
    }

    /* Clear the Card reader's Response field. */
    private void clearResponseUi() {
        mTxtAuthentication.setText(R.string.noData);
        mTxtATR.setText(R.string.noData);
        mTxtResponseApdu.setText(R.string.noData);
        mTxtEscapeResponse.setText(R.string.noData);
        mTxtSlotStatus.setText(R.string.noData);

        mAuthentication.setEnabled(true);
    }



    private void findUiViews()
    {
        mAuthentication = (Button) findViewById(R.id.button_Authenticate);
        mStartPolling = (Button) findViewById(R.id.button_StartPolling);
        mStopPolling = (Button) findViewById(R.id.button_StopPolling);

        mPowerOn = (Button) findViewById(R.id.button_PowerOn);
        mPowerOff = (Button) findViewById(R.id.button_power_off_card);
        mTransmitApdu = (Button) findViewById(R.id.button_TransmitADPU);
        mTransmitEscape = (Button) findViewById(R.id.button_TransmitEscapeCommand);
        mClear = (Button) findViewById(R.id.button_Clear);
        mGetCardStatus = (Button) findViewById(R.id.button_GetCardStatus);

        mTxtConnectionState = (TextView) findViewById(R.id.textView_ReaderState);
        mTxtCardStatus = (TextView) findViewById(R.id.textView_IccState);
        mTxtAuthentication = (TextView) findViewById(R.id.textView_Authentication);
        mTxtATR = (TextView) findViewById(R.id.textView_ATR);
        mTxtSlotStatus = (TextView) findViewById(R.id.textView_SlotStatus);
        mTxtResponseApdu = (TextView) findViewById(R.id.textView_Response);
        mTxtEscapeResponse = (TextView) findViewById(R.id.textView_EscapeResponse);

        mEditMasterKey = (EditText) findViewById(R.id.editText_Master_Key);
        mEditApdu = (EditText) findViewById(R.id.editText_ADPU);
        mEditEscape = (EditText) findViewById(R.id.editText_Escape);

        mStartAutoDetect = (Button) findViewById(R.id.button_StartAutoDetect);
        mStopAutoDetect  = (Button) findViewById(R.id.button_StopAutoDetect);
        mTxtIDm1          = (TextView) findViewById(R.id.IDm1);
        mTxtIDm2          = (TextView) findViewById(R.id.IDm2);
        mTxtIDm3          = (TextView) findViewById(R.id.IDm3);
        mTxtIDm4          = (TextView) findViewById(R.id.IDm4);
    }

    /*
     * Update listener
     */
    private void setListener(BluetoothReader reader)
    {
        if (mBluetoothReader instanceof Acr1255uj1Reader)
          {
            ((Acr1255uj1Reader) mBluetoothReader).setOnBatteryLevelChangeListener(new OnBatteryLevelChangeListener()
            {
                @Override
                public void onBatteryLevelChange(BluetoothReader bluetoothReader, final int batteryLevel)
                {
                    Log.i(TAG, "mBatteryLevelListener data: " + batteryLevel);

                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
//                            mTxtBatteryLevel.setText(getBatteryLevelString(batteryLevel));
                        }
                    });
                }
            });
          }


        mBluetoothReader.setOnCardStatusChangeListener(new OnCardStatusChangeListener()
        {
                    @Override
                    public void onCardStatusChange(BluetoothReader bluetoothReader, final int sta) {

                        Log.i(TAG, "mCardStatusListener sta: " + sta);

                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                mTxtCardStatus.setText(getCardStatusString(sta));

                                if (status_AutoDetect == true)
                                {
                                    if ((sta == 2) && (status_CardPower == false))
                                      {
                                        status_CardPower = true;

                                        new Handler().postDelayed(new Runnable() {
                                            @Override
                                            public void run()
                                            {
                                                mBluetoothReader.powerOnCard();
                                            }
                                        }, 100);
                                      }
                                    else if (sta == 255)
                                      {
                                        new Handler().postDelayed(new Runnable() {
                                            @Override
                                            public void run()
                                            {
                                                mBluetoothReader.powerOnCard();
                                            }
                                        }, 100);
                                      }
                                    else
                                      {
                                        mTxtIDm1.setText("");
                                        mTxtIDm2.setText("");
                                        mTxtIDm3.setText("");
                                        mTxtIDm4.setText("");
                                      }
                                }
                            }
                        });
                    }

                });

        /* Wait for authentication completed. */
        mBluetoothReader.setOnAuthenticationCompleteListener(new OnAuthenticationCompleteListener() {

                    @Override
                    public void onAuthenticationComplete(
                            BluetoothReader bluetoothReader, final int errorCode) {

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (errorCode == BluetoothReader.ERROR_SUCCESS) {
                                    mTxtAuthentication.setText("Authentication Success!");
                                    mAuthentication.setEnabled(false);
                                } else {
                                    mTxtAuthentication.setText("Authentication Failed!");
                                }

                                if (status_AutoDetect == true)
                                  {
                                    new Handler().postDelayed(new Runnable() {
                                        @Override
                                        public void run()
                                        {
                                            mBluetoothReader.transmitEscapeCommand(AUTO_POLLING_START);
                                        }
                                    }, 100);
                                  }
                            }
                        });
                    }
                });

        /* Wait for receiving ATR string. */
        mBluetoothReader.setOnAtrAvailableListener(new OnAtrAvailableListener() {

                    @Override
                    public void onAtrAvailable(BluetoothReader bluetoothReader,
                            final byte[] atr, final int errorCode) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (atr == null) {
                                    mTxtATR.setText(getErrorString(errorCode));
                                } else {
                                    mTxtATR.setText(Utils.toHexString(atr));
                                }

                                if ((atr != null) && (status_AutoDetect == true))
                                {
                                    if ((atr[13] == 0x00) && (atr[14] == 0x3B))         //  felica Card
                                      {
                                        new Handler().postDelayed(new Runnable() {
                                            @Override
                                            public void run()
                                            {
                                                byte apduCommand[] = Utils.getEditTextinHexBytes(mEditApdu);
                                                mBluetoothReader.transmitApdu(apduCommand);
                                            }
                                        }, 100);
                                      }
                                }



                            }
                        });
                    }

                });

        /* Wait for power off response. */
        mBluetoothReader.setOnCardPowerOffCompleteListener(new OnCardPowerOffCompleteListener() {

                    @Override
                    public void onCardPowerOffComplete(
                            BluetoothReader bluetoothReader, final int result) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTxtATR.setText(getErrorString(result));
                            }
                        });
                    }

                });

        /* Wait for response APDU. */
        mBluetoothReader.setOnResponseApduAvailableListener(new OnResponseApduAvailableListener() {

                    @Override
                    public void onResponseApduAvailable(
                            BluetoothReader bluetoothReader, final byte[] apdu,
                            final int errorCode) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTxtResponseApdu.setText(getResponseString(apdu, errorCode));

                                if (status_AutoDetect == true)
                                  {
                                    new Handler().postDelayed(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            mBluetoothReader.powerOffCard();
                                            status_CardPower = false;
                                        }
                                    }, 100);

                                    String  wStr = getResponseString(apdu, errorCode);
                                    if (wStr.length() >= 22)
                                      {
                                        mTxtIDm1.setText(wStr.substring( 0,  2) + wStr.substring( 3,  5));
                                        mTxtIDm2.setText(wStr.substring( 6,  8) + wStr.substring( 9, 11));
                                        mTxtIDm3.setText(wStr.substring(12, 14) + wStr.substring(15, 17));
                                        mTxtIDm4.setText(wStr.substring(18, 20) + wStr.substring(21, 23));
                                      }
                                  }
                            }
                        });
                    }

                });

        /* Wait for escape command response. */
        mBluetoothReader.setOnEscapeResponseAvailableListener(new OnEscapeResponseAvailableListener() {

                    @Override
                    public void onEscapeResponseAvailable(
                            BluetoothReader bluetoothReader,
                            final byte[] response, final int errorCode) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTxtEscapeResponse.setText(getResponseString(
                                        response, errorCode));
                            }
                        });
                    }
                });

        /* Wait for device info available. */
        mBluetoothReader.setOnDeviceInfoAvailableListener(new OnDeviceInfoAvailableListener() {

                    @Override
                    public void onDeviceInfoAvailable(
                            BluetoothReader bluetoothReader, final int infoId,
                            final Object o, final int status) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (status != BluetoothGatt.GATT_SUCCESS) {
                                    Toast.makeText(ReaderActivity.this,
                                            "Failed to read device info!",
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                            }
                        });
                    }

                });

        /* Wait for battery level available. */
        if (mBluetoothReader instanceof Acr1255uj1Reader) {
            ((Acr1255uj1Reader) mBluetoothReader).setOnBatteryLevelAvailableListener(new OnBatteryLevelAvailableListener() {

                        @Override
                        public void onBatteryLevelAvailable(
                                BluetoothReader bluetoothReader,
                                final int batteryLevel, int status) {
                            Log.i(TAG, "mBatteryLevelListener data: "
                                    + batteryLevel);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
///                                    mTxtBatteryLevel2.setText(getBatteryLevelString(batteryLevel));
                                }
                            });
                        }

                    });
        }

        /* Handle on slot status available. */
        mBluetoothReader.setOnCardStatusAvailableListener(new OnCardStatusAvailableListener() {

                    @Override
                    public void onCardStatusAvailable(
                            BluetoothReader bluetoothReader,
                            final int cardStatus, final int errorCode) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (errorCode != BluetoothReader.ERROR_SUCCESS) {
                                    mTxtSlotStatus.setText(getErrorString(errorCode));
                                } else {
                                    mTxtSlotStatus.setText(getCardStatusString(cardStatus));
                                }
                            }
                        });
                    }

                });


        mBluetoothReader.setOnEnableNotificationCompleteListener(new OnEnableNotificationCompleteListener() {

                    @Override
                    public void onEnableNotificationComplete(
                            BluetoothReader bluetoothReader, final int result) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (result != BluetoothGatt.GATT_SUCCESS) {
                                    /* Fail */
                                    Toast.makeText(
                                            ReaderActivity.this,
                                            "The device is unable to set notification!",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(ReaderActivity.this,
                                            "The device is ready to use!",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }

                });
    }

    /* Set Button onClick() events. */
    private void setOnClickListener() {
        /*
         * Update onClick listener.
         */

        /* Clear UI text. */
        mClear.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                clearResponseUi();
            }
        });

        /* Authentication function, authenticate the connected card reader. */
        mAuthentication.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mBluetoothReader == null) {
                    mTxtAuthentication.setText(R.string.card_reader_not_ready);
                    return;
                }

                /* Retrieve master key from edit box. */
                byte masterKey[] = Utils.getEditTextinHexBytes(mEditMasterKey);

                if (masterKey != null && masterKey.length > 0) {
                    /* Clear response field for the result of authentication. */
                    mTxtAuthentication.setText(R.string.noData);

                    /* Start authentication. */
                    if (!mBluetoothReader.authenticate(masterKey)) {
                        mTxtAuthentication
                                .setText(R.string.card_reader_not_ready);
                    } else {
                        mTxtAuthentication.setText("Authenticating...");
                    }
                } else {
                    mTxtAuthentication.setText("Character format error!");
                }
            }
        });

        /* Start polling card. */
        mStartPolling.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mBluetoothReader == null) {
                    mTxtATR.setText(R.string.card_reader_not_ready);
                    return;
                }
                if (!mBluetoothReader.transmitEscapeCommand(AUTO_POLLING_START)) {
                    mTxtATR.setText(R.string.card_reader_not_ready);
                }
            }
        });

        /* Stop polling card. */
        mStopPolling.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mBluetoothReader == null) {
                    mTxtATR.setText(R.string.card_reader_not_ready);
                    return;
                }
                if (!mBluetoothReader.transmitEscapeCommand(AUTO_POLLING_STOP)) {
                    mTxtATR.setText(R.string.card_reader_not_ready);
                }
            }
        });

        /* Power on the card. */
        mPowerOn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mBluetoothReader == null) {
                    mTxtATR.setText(R.string.card_reader_not_ready);
                    return;
                }
                if (!mBluetoothReader.powerOnCard()) {
                    mTxtATR.setText(R.string.card_reader_not_ready);
                }
            }
        });

        /* Power off the card. */
        mPowerOff.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mBluetoothReader == null) {
                    mTxtATR.setText(R.string.card_reader_not_ready);
                    return;
                }
                if (!mBluetoothReader.powerOffCard()) {
                    mTxtATR.setText(R.string.card_reader_not_ready);
                }
            }
        });

        /* Transmit ADPU. */
        mTransmitApdu.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                /* Check for detected reader. */
                if (mBluetoothReader == null) {
                    mTxtResponseApdu.setText(R.string.card_reader_not_ready);
                    return;
                }

                /* Retrieve APDU command from edit box. */
                byte apduCommand[] = Utils.getEditTextinHexBytes(mEditApdu);

                if (apduCommand != null && apduCommand.length > 0) {
                    /* Clear response field for result of APDU. */
                    mTxtResponseApdu.setText(R.string.noData);

                    /* Transmit APDU command. */
                    if (!mBluetoothReader.transmitApdu(apduCommand)) {
                        mTxtResponseApdu
                                .setText(R.string.card_reader_not_ready);
                    }
                } else {
                    mTxtResponseApdu.setText("Character format error!");
                }
            }
        });

        /* Transmit escape command. */
        mTransmitEscape.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                /* Check for detected reader. */
                if (mBluetoothReader == null) {
                    mTxtEscapeResponse.setText(R.string.card_reader_not_ready);
                    return;
                }

                /* Retrieve escape command from edit box. */
                byte escapeCommand[] = Utils.getEditTextinHexBytes(mEditEscape);

                if (escapeCommand != null && escapeCommand.length > 0) {
                    /* Clear response field for result of escape command. */
                    mTxtEscapeResponse.setText(R.string.noData);

                    /* Transmit escape command. */
                    if (!mBluetoothReader.transmitEscapeCommand(escapeCommand)) {
                        mTxtEscapeResponse
                                .setText(R.string.card_reader_not_ready);
                    }
                } else {
                    mTxtEscapeResponse.setText("Character format error!");
                }
            }
        });

        /* Get the card status. */
        mGetCardStatus.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mBluetoothReader == null) {
                    mTxtSlotStatus.setText(R.string.card_reader_not_ready);
                    return;
                }
                if (!mBluetoothReader.getCardStatus()) {
                    mTxtSlotStatus.setText(R.string.card_reader_not_ready);
                }
            }
        });

        mStartAutoDetect.setOnClickListener(new OnClickListener()
        {
            public void onClick(View v)
            {
                status_AutoDetect = true;

                if (mAuthentication.isEnabled() == true) {
                    mAuthentication.performClick();
                } else {
                    mBluetoothReader.transmitEscapeCommand(AUTO_POLLING_START);
                }

                mClear.setEnabled(false);
                mAuthentication.setEnabled(false);
                mStartPolling.setEnabled(false);
                mStopPolling.setEnabled(false);
                mPowerOn.setEnabled(false);
                mPowerOff.setEnabled(false);
                mTransmitApdu.setEnabled(false);
                mTransmitEscape.setEnabled(false);
                mGetCardStatus.setEnabled(false);
                mEditMasterKey.setEnabled(false);
                mEditApdu.setEnabled(false);
                mEditEscape.setEnabled(false);

                mStartAutoDetect.setEnabled(false);
                mStopAutoDetect.setEnabled(true);
            }
        });

        mStopAutoDetect.setOnClickListener(new OnClickListener()
        {
            public void onClick(View v)
            {
                status_AutoDetect = false;

                mClear.setEnabled(true);
                mAuthentication.setEnabled(true);
                mStartPolling.setEnabled(true);
                mStopPolling.setEnabled(true);
                mPowerOn.setEnabled(true);
                mPowerOff.setEnabled(true);
                mTransmitApdu.setEnabled(true);
                mTransmitEscape.setEnabled(true);
                mGetCardStatus.setEnabled(true);
                mEditMasterKey.setEnabled(true);
                mEditApdu.setEnabled(true);
                mEditEscape.setEnabled(true);

                mStartAutoDetect.setEnabled(true);
                mStopAutoDetect.setEnabled(false);
            }
        });
    }

    /* Start the process to enable the reader's notifications. */
    private void activateReader(BluetoothReader reader) {
        if (reader == null) {
            return;
        }

        if (mBluetoothReader instanceof Acr1255uj1Reader) {
            /* Enable notification. */
            mBluetoothReader.enableNotification(true);
        }
    }


    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reader);
        final Intent intent = getIntent();
        mDeviceName    = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        /* Update UI. */
        findUiViews();
        updateUi(null);

        /* Set the onClick() event handlers. */
        setOnClickListener();

        /* Initialize BluetoothReaderGattCallback. */
        mGattCallback = new BluetoothReaderGattCallback();

        /* Register BluetoothReaderGattCallback's listeners */
        mGattCallback.setOnConnectionStateChangeListener(new OnConnectionStateChangeListener() {

                    @Override
                    public void onConnectionStateChange(
                            final BluetoothGatt gatt, final int state,
                            final int newState) {

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (state != BluetoothGatt.GATT_SUCCESS) {
                                    /*
                                     * Show the message on fail to
                                     * connect/disconnect.
                                     */
                                    mConnectState = BluetoothReader.STATE_DISCONNECTED;

                                    if (newState == BluetoothReader.STATE_CONNECTED) {
                                        mTxtConnectionState
                                                .setText(R.string.connect_fail);
                                    } else if (newState == BluetoothReader.STATE_DISCONNECTED) {
                                        mTxtConnectionState
                                                .setText(R.string.disconnect_fail);
                                    }
                                    clearAllUi();
                                    updateUi(null);
                                    invalidateOptionsMenu();
                                    return;
                                }

                                updateConnectionState(newState);

                                if (newState == BluetoothProfile.STATE_CONNECTED) {
                                    /* Detect the connected reader. */
                                    if (mBluetoothReaderManager != null) {
                                        mBluetoothReaderManager.detectReader(gatt, mGattCallback);
                                    }
                                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                    mBluetoothReader = null;
                                    /*
                                     * Release resources occupied by Bluetooth
                                     * GATT client.
                                     */
                                    if (mBluetoothGatt != null) {
                                        mBluetoothGatt.close();
                                        mBluetoothGatt = null;
                                    }
                                }
                            }
                        });
                    }
                });

        /* Initialize mBluetoothReaderManager. */
        mBluetoothReaderManager = new BluetoothReaderManager();

        /* Register BluetoothReaderManager's listeners */
        mBluetoothReaderManager.setOnReaderDetectionListener(new OnReaderDetectionListener() {

                    @Override
                    public void onReaderDetection(BluetoothReader reader) {
                        updateUi(reader);

                        if (reader instanceof Acr1255uj1Reader) {
                            /* The connected reader is ACR1255U-J1 reader. */
                            Log.v(TAG, "On Acr1255uj1Reader Detected.");
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(ReaderActivity.this,
                                            "The device is not supported!",
                                            Toast.LENGTH_SHORT).show();

                                    /* Disconnect Bluetooth reader */
                                    Log.v(TAG, "Disconnect reader!!!");
                                    disconnectReader();
                                    updateConnectionState(BluetoothReader.STATE_DISCONNECTED);
                                }
                            });
                            return;
                        }

                        mBluetoothReader = reader;
                        setListener(reader);
                        activateReader(reader);
                    }
                });

        /* Connect the reader. */
        connectReader();

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
//        getActionBar().setTitle(mDeviceName);
//        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume()");
        super.onResume();

        final IntentFilter intentFilter = new IntentFilter();

        /* Start to monitor bond state change */
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBroadcastReceiver, intentFilter);

        /* Clear unused dialog. */
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause()");
        super.onPause();

        /* Stop to monitor bond state change */
        unregisterReceiver(mBroadcastReceiver);

        /* Disconnect Bluetooth reader */
        disconnectReader();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        /* Save the current state. */
        savedInstanceState.putString(SLOT_STATUS_STRING, mTxtSlotStatus.getText().toString());
        savedInstanceState.putString(SLOT_STATUS_2_STRING, mTxtCardStatus.getText().toString());

        savedInstanceState.putString(AUTHENTICATION_KEY, mEditMasterKey.getText().toString());
        savedInstanceState.putString(ATR_STRING, mTxtATR.getText().toString());
        savedInstanceState.putString(APDU_COMMAND, mEditApdu.getText().toString());
        savedInstanceState.putString(RESPONSE_APDU, mTxtResponseApdu.getText().toString());
        savedInstanceState.putString(ESCAPE_COMMAND, mEditEscape.getText().toString());
        savedInstanceState.putString(ESCAPE_RESPONSE, mTxtEscapeResponse.getText().toString());

        /* Always call the superclass so it can save the view hierarchy state. */
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        /* Always call the superclass so it can restore the view hierarchy. */
        super.onRestoreInstanceState(savedInstanceState);

        /* Restore state members from saved instance. */
        mTxtSlotStatus.setText(savedInstanceState.getString(SLOT_STATUS_STRING));
        mTxtCardStatus.setText(savedInstanceState.getString(SLOT_STATUS_2_STRING));

        mEditMasterKey.setText(savedInstanceState.getString(AUTHENTICATION_KEY));
        mTxtATR.setText(savedInstanceState.getString(ATR_STRING));
        mEditApdu.setText(savedInstanceState.getString(APDU_COMMAND));
        mTxtResponseApdu.setText(savedInstanceState.getString(RESPONSE_APDU));
        mEditEscape.setText(savedInstanceState.getString(ESCAPE_COMMAND));
        mTxtEscapeResponse.setText(savedInstanceState.getString(ESCAPE_RESPONSE));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        /* Inflate the menu; this adds items to the action bar if it is present. */
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        return super.onOptionsItemSelected(item);
    }

    /* Show and hide UI resources and set the default master key and commands. */
    private void updateUi(final BluetoothReader bluetoothReader) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (bluetoothReader instanceof Acr1255uj1Reader) {
                    /* The connected reader is ACR1255U-J1 reader. */
                    if (mEditMasterKey.getText().length() == 0) {
                        try {
                            mEditMasterKey.setText(Utils.toHexString(DEFAULT_1255_MASTER_KEY
                                            .getBytes("UTF-8")));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                    if (mEditApdu.getText().length() == 0) {
                        mEditApdu.setText(DEFAULT_1255_APDU_COMMAND);
                    }
                    if (mEditEscape.getText().length() == 0) {
                        mEditEscape.setText(DEFAULT_1255_ESCAPE_COMMAND);
                    }
                    mClear.setEnabled(true);
                    mAuthentication.setEnabled(true);
                    mStartPolling.setEnabled(true);
                    mStopPolling.setEnabled(true);
                    mPowerOn.setEnabled(true);
                    mPowerOff.setEnabled(true);
                    mTransmitApdu.setEnabled(true);
                    mTransmitEscape.setEnabled(true);
                    mGetCardStatus.setEnabled(true);
                    mEditMasterKey.setEnabled(true);
                    mEditApdu.setEnabled(true);
                    mEditEscape.setEnabled(true);

                    mStartAutoDetect.setEnabled(true);
                    mStopAutoDetect.setEnabled(false);
                } else {
                    mEditApdu.setText(R.string.noData);
                    mEditEscape.setText(R.string.noData);
                    mClear.setEnabled(true);
                    mAuthentication.setEnabled(false);
                    mStartPolling.setEnabled(false);
                    mStopPolling.setEnabled(false);
                    mPowerOn.setEnabled(false);
                    mPowerOff.setEnabled(false);
                    mTransmitApdu.setEnabled(false);
                    mTransmitEscape.setEnabled(false);
                    mGetCardStatus.setEnabled(false);
                    mEditMasterKey.setEnabled(false);
                    mEditApdu.setEnabled(false);
                    mEditEscape.setEnabled(false);

                    mStartAutoDetect.setEnabled(false);
                    mStopAutoDetect.setEnabled(false);
                }
            }
        });
    }

    /*
     * Create a GATT connection with the reader. And detect the connected reader
     * once service list is available.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean connectReader() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.w(TAG, "Unable to initialize BluetoothManager.");
            updateConnectionState(BluetoothReader.STATE_DISCONNECTED);
            return false;
        }

        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Unable to obtain a BluetoothAdapter.");
            updateConnectionState(BluetoothReader.STATE_DISCONNECTED);
            return false;
        }

        /*
         * Connect Device.
         */
        /* Clear old GATT connection. */
        if (mBluetoothGatt != null) {
            Log.i(TAG, "Clear old GATT connection");
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        /* Create a new connection. */
        if (bluetoothAdapter != null) {
            final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mDeviceAddress);

            if (device == null) {
                Log.w(TAG, "Device not found. Unable to connect.");
                return false;
            }

            /* Connect to GATT server. */
            updateConnectionState(BluetoothReader.STATE_CONNECTING);
            mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
            return true;
        } else {
            Log.w(TAG, "bluetoothAdapter is null. <<<<<<<<<<<<<<<<<<<<<<<");
            return false;
        }
    }

    /* Disconnects an established connection. */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void disconnectReader() {
        if (mBluetoothGatt == null) {
            updateConnectionState(BluetoothReader.STATE_DISCONNECTED);
            return;
        }
        updateConnectionState(BluetoothReader.STATE_DISCONNECTING);
        mBluetoothGatt.disconnect();
    }

    /* Get the Bonding status string. */
    private String getBondingStatusString(int bondingStatus) {
        if (bondingStatus == BluetoothDevice.BOND_BONDED) {
            return "BOND BONDED";
        } else if (bondingStatus == BluetoothDevice.BOND_NONE) {
            return "BOND NONE";
        } else if (bondingStatus == BluetoothDevice.BOND_BONDING) {
            return "BOND BONDING";
        }
        return "BOND UNKNOWN.";
    }

    /* Get the Card status string. */
    private String getCardStatusString(int cardStatus) {
        if (cardStatus == BluetoothReader.CARD_STATUS_ABSENT) {
            return "Absent.";
        } else if (cardStatus == BluetoothReader.CARD_STATUS_PRESENT) {
            return "Present.";
        } else if (cardStatus == BluetoothReader.CARD_STATUS_POWERED) {
            return "Powered.";
        } else if (cardStatus == BluetoothReader.CARD_STATUS_POWER_SAVING_MODE) {
            return "Power saving mode.";
        }
        return "The card status is unknown.";
    }

    /* Get the Error string. */
    private String getErrorString(int errorCode) {
        if (errorCode == BluetoothReader.ERROR_SUCCESS) {
            return "";
        } else if (errorCode == BluetoothReader.ERROR_INVALID_CHECKSUM) {
            return "The checksum is invalid.";
        } else if (errorCode == BluetoothReader.ERROR_INVALID_DATA_LENGTH) {
            return "The data length is invalid.";
        } else if (errorCode == BluetoothReader.ERROR_INVALID_COMMAND) {
            return "The command is invalid.";
        } else if (errorCode == BluetoothReader.ERROR_UNKNOWN_COMMAND_ID) {
            return "The command ID is unknown.";
        } else if (errorCode == BluetoothReader.ERROR_CARD_OPERATION) {
            return "The card operation failed.";
        } else if (errorCode == BluetoothReader.ERROR_AUTHENTICATION_REQUIRED) {
            return "Authentication is required.";
        } else if (errorCode == BluetoothReader.ERROR_CHARACTERISTIC_NOT_FOUND) {
            return "Error characteristic is not found.";
        } else if (errorCode == BluetoothReader.ERROR_WRITE_DATA) {
            return "Write command to reader is failed.";
        } else if (errorCode == BluetoothReader.ERROR_TIMEOUT) {
            return "Timeout.";
        } else if (errorCode == BluetoothReader.ERROR_AUTHENTICATION_FAILED) {
            return "Authentication is failed.";
        } else if (errorCode == BluetoothReader.ERROR_UNDEFINED) {
            return "Undefined error.";
        } else if (errorCode == BluetoothReader.ERROR_INVALID_DATA) {
            return "Received data error.";
        }
        return "Unknown error.";
    }

    /* Get the Response string. */
    private String getResponseString(byte[] response, int errorCode) {
        if (errorCode == BluetoothReader.ERROR_SUCCESS) {
            if (response != null && response.length > 0) {
                return Utils.toHexString(response);
            }
            return "";
        }
        return getErrorString(errorCode);
    }

    /* Update the display of Connection status string. */
    private void updateConnectionState(final int connectState) {

        mConnectState = connectState;

        if (connectState == BluetoothReader.STATE_CONNECTING) {
            mTxtConnectionState.setText(R.string.connecting);
        } else if (connectState == BluetoothReader.STATE_CONNECTED) {
            mTxtConnectionState.setText(R.string.connected);
        } else if (connectState == BluetoothReader.STATE_DISCONNECTING) {
            mTxtConnectionState.setText(R.string.disconnecting);
        } else {
            mTxtConnectionState.setText(R.string.disconnected);
            clearAllUi();
            updateUi(null);
        }
        invalidateOptionsMenu();
    }
}
