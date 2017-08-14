package com.developers.paras.droidwatch;


import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.telephony.PhoneStateListener;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static android.content.ContentValues.TAG;
import static android.content.Context.MODE_PRIVATE;


/**
 * A simple {@link Fragment} subclass.
 */
public class DeviceHomeFragment extends Fragment {

    private int CALL_STATE_RING = 0;
    private int MESSAGE_STATE = 0;
    private String INCOMING_NUMBER = null;
    private String INCOMING_MESSAGE_BODY =null;
    private String INCOMING_MESSAGE_NUMBER =null;

    TelephonyManager tmg=null;
    CallStateListener callstate=null;

    View v;
    Context con=null;
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
         con = context;
    }


    public DeviceHomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        v=inflater.inflate(R.layout.fragment_device_home, container, false);
        return v;
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        SharedPreferences sp = con.getSharedPreferences("device_info",MODE_PRIVATE);
        final String address = sp.getString("hardware_address",null);
        final String name = sp.getString("device_name",null);

        final BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);

        //Trying to create tunnel bluetooth device
        final ConnectThread conthread = new ConnectThread(device);


        TelephonyManager  tm = (TelephonyManager) con.getSystemService(Context.TELEPHONY_SERVICE);
           tmg=tm;
        CallStateListener callStateListener = new CallStateListener();
        callstate=callStateListener;
        tm.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        IntentFilter filter = new IntentFilter();

        filter.addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);

        con.registerReceiver(msgReceiver, filter);



        final Button disconnect = (Button) v.findViewById(R.id.disconnect);
        final TextView device_name = (TextView) v.findViewById(R.id.device_name);
        final TextView device_status = (TextView) v.findViewById(R.id.device_status);
        final TextView device_data = (TextView) v.findViewById(R.id.device_data);
        Button reconnect = (Button) v.findViewById(R.id.reconnect);

        if(!address.equals("00:21:13:00:E0:68"))
        {

            Toast.makeText(con, "Please Select Device HC-05 with 00:21:13:00:E0:68 for Droid Watch", Toast.LENGTH_LONG).show();
            //******SENDING CONTROL BACK TO DEVICE LIST*************
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.replace(R.id.device_list_layout, new DeviceListFragment());
            ft.addToBackStack("Stack");
            ft.commit();

        }
        else
        {

            //Trying to connect with bluetooth device
            conthread.run();


            // Setting the name on the TextView
            device_name.setText("Name : "+name);

            // Setting the Status on the TextView
            if(conthread.getStatus()==false)
            {
                device_status.setText("Status : Not Connected");
                Log.d("mybt","Status not connected");


            } else if(conthread.getStatus()==true)
            {
                device_status.setText("Status : Connected");
                Log.d("mybt","Status connected");

                BackgroundTask backgroundtask= new BackgroundTask(device_status,device_data,conthread);
                backgroundtask.execute();

            }


            //Closing the Connection on button click
            reconnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    //******SENDING CONTROL BACK TO DEVICE LIST*************
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.replace(R.id.device_list_layout, new DeviceHomeFragment());
                    ft.commit();
                }
            });

            //Closing the Connection on button click
            disconnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    conthread.cancel();
                    conthread.setStatus(false);
                    device_status.setText("Status : Not Connected");

                    SharedPreferences sp = con.getSharedPreferences("device_info",MODE_PRIVATE);
                    SharedPreferences.Editor et = sp.edit();
                    et.clear();

                    //******SENDING CONTROL BACK TO DEVICE LIST*************
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.replace(R.id.device_list_layout, new DeviceListFragment());
                    ft.commit();
                }
            });

        }


    }

    class BackgroundTask extends AsyncTask<Void, Void, Void> {

        TextView device_status = null;
        TextView device_data=null;
        ConnectThread conthread=null;

        public BackgroundTask(TextView status, TextView data, ConnectThread connect) {
              device_status= status;
              device_data = data;
            conthread = connect;
        }

        @Override
        protected void onPreExecute() {

            device_status.setText("Status : Connected");
            device_data.setText("Data : Sending");

        }

        @Override
        protected void onPostExecute(Void result) {

            device_status.setText("Status : Not Connected");
            device_data.setText("Data : Not Sending");

        }

        @Override
        protected Void doInBackground(Void... params) {

            try {
                Thread.sleep(2000);
                Looper.prepare();
                MyBluetoothService.ConnectedThread connectedThread =
                        new MyBluetoothService.ConnectedThread(conthread.getSocket());

                while (true) {
                    try {
                        Thread.sleep(1000);

                        if(CALL_STATE_RING==1)
                        {
                            Log.d("mybt","Phone is ringing");
                        }

                        byte[] byte_data ;
                        //Retrieving current time
                        java.util.Calendar c = java.util.Calendar.getInstance();
                        int hh = c.get(java.util.Calendar.HOUR);
                        int mm = c.get(java.util.Calendar.MINUTE);
                        int ss = c.get(java.util.Calendar.SECOND);
                        int pm= c.get(java.util.Calendar.AM_PM);
                        if(hh==0&&pm==1)
                        {
                            hh=12;
                        }
                        Log.d("mybt","PM = "+pm);
                        //Retrieving call information
                        String incomingName=getContactName(con,INCOMING_NUMBER);
                        //sending data to the watch
                        String dataUrl = hh+":"+mm+":"+ss+":"+pm+"\0"+CALL_STATE_RING+"\0"+incomingName+"\0"+INCOMING_NUMBER+"\0"+MESSAGE_STATE+"\0"+INCOMING_MESSAGE_NUMBER+"\0"+INCOMING_MESSAGE_BODY+"\0";
                        Log.d("mybt","Call state : "+CALL_STATE_RING+" Name : "+incomingName+" Num : "+INCOMING_NUMBER);
                        Log.d("mybt","Message received :"+MESSAGE_STATE+" "+INCOMING_MESSAGE_NUMBER+" "+INCOMING_MESSAGE_BODY);
                        byte_data = dataUrl.getBytes();
                        connectedThread.write(byte_data);
                        if(MESSAGE_STATE==1)
                        {
                            MESSAGE_STATE=0;
                            INCOMING_MESSAGE_NUMBER=null;
                            INCOMING_MESSAGE_BODY=null;
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if(!connectedThread.getWriteSocket().isConnected())
                    {
                        Log.d("mybt","Disconnected during transmission");
                        break;
                    }

                }
                CALL_STATE_RING = 0;
                MESSAGE_STATE=0;
                INCOMING_NUMBER = null;
                INCOMING_MESSAGE_BODY =null;
                INCOMING_MESSAGE_NUMBER =null;



            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return null;
        }

    }


    public static String getContactName(Context context, String phoneNumber) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor cursor = cr.query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
        if (cursor == null) {
            return null;
        }
        String contactName = "Unknown";
        if(cursor.moveToFirst()) {
            contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
        }

        if(cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return contactName;
    }

    /**
     * Listener to detect incoming calls.
     */
    private class CallStateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    // called when someone is ringing to this phone
                          CALL_STATE_RING=1;
                              INCOMING_NUMBER=incomingNumber;
                    Toast.makeText(con, "Phone is ringing", Toast.LENGTH_SHORT).show();

                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    CALL_STATE_RING =0;
                    break;
            }
        }
    }

    private final BroadcastReceiver msgReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
                for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    String messageBody = smsMessage.getMessageBody();
                    INCOMING_MESSAGE_BODY=messageBody;
                    INCOMING_MESSAGE_NUMBER = smsMessage.getOriginatingAddress();
                    MESSAGE_STATE=1;
                    Toast.makeText(context,"Message state "+MESSAGE_STATE, Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        tmg.listen(callstate, PhoneStateListener.LISTEN_NONE);
        con.unregisterReceiver(msgReceiver);

    }
}


class ConnectThread extends Thread {
    private final BluetoothSocket mmSocket;
    private BluetoothSocket mmSocketFallback;
    private final BluetoothDevice mmDevice;
    private static boolean SUCCESS = false;

    public boolean getStatus() {
        return SUCCESS;
    }
    public void setStatus(boolean value) {
        this.SUCCESS=value;
    }

    public BluetoothSocket getSocket() {
        return mmSocketFallback;
    }


    public ConnectThread(BluetoothDevice device) {
        // Use a temporary object that is later assigned to mmSocket
        // because mmSocket is final.
        BluetoothSocket tmp = null;
        mmDevice = device;

        try {
            // Get a BluetoothSocket to connect with the given BluetoothDevice.
            // MY_UUID is the app's UUID string, also used in the server code.
            // careful for attacks before connection is insecure
            tmp = mmDevice.createRfcommSocketToServiceRecord(UUID.randomUUID());


        } catch (IOException e) {
            Log.d("mybt", "Socket's create() method failed", e);
        }
        mmSocket = tmp;
        mmSocketFallback = tmp;
    }

    public void run() {
        // Cancel discovery because it otherwise slows down the connection.
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothAdapter.cancelDiscovery();
        BluetoothSocket tmp = null;
        tmp=mmSocket;
        try
        {
            Class<?> clazz = tmp.getRemoteDevice().getClass();
            Class<?>[] paramTypes = new Class<?>[] {Integer.TYPE};
            Method m = clazz.getMethod("createRfcommSocket", paramTypes);
            Object[] params = new Object[] {Integer.valueOf(1)};

            mmSocketFallback = (BluetoothSocket) m.invoke(tmp.getRemoteDevice(), params);
        }
        catch (Exception e)
        {
           Log.d("mybt","Fall failed due to : "+ e.getMessage());
        }
        try {
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            mmSocketFallback.connect();
            SUCCESS=true;
        } catch (IOException connectException) {
            // Unable to connect; close the socket and return.
            Log.d("mybt","Closing connection due to : "+connectException);
            try {
                mmSocketFallback.close();
            } catch (IOException closeException) {
                Log.d("mybt", "Could not close the client socket due to : "+closeException, closeException);
            }
            return;
        }

        // The connection attempt succeeded. Perform work associated with
        // the connection in a separate thread.
       

  //      manageMyConnectedSocket(mmSocket);
    }

    // Closes the client socket and causes the thread to finish.
    public void cancel() {
        try {
            mmSocketFallback.close();
            SUCCESS=false;
        } catch (IOException e) {
            Log.d("mybt", "Could not close the client socket", e);
        }
    }


}