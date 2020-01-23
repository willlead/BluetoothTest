package com.example.bluetoothtest;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.IpSecManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

//사물인터넷을 품은 아두이노 교재 25장 소스
public class MainActivity extends AppCompatActivity {

    ImageView ivConnect;
    Button btnBluetoothChk;
    BluetoothAdapter bluetoothAdapter;

    static final int REQUEST_ENABLE_BT = 10;
    int mPairedDeviceCount = 0;
    Set<BluetoothDevice> mDevices;
    BluetoothDevice mRemoteDevice;
    BluetoothSocket mSocket = null;
    OutputStream mOutputStream = null;
    InputStream mInputStream = null;
    Thread mWorkerThread = null;
    String mStrDelimiter = "\n";
    char mCharDelimiter = '\n';
    byte[] readBuffer;
    int readBufferPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivConnect = (ImageView) findViewById(R.id.ivConnect);
        btnBluetoothChk = (Button) findViewById(R.id.btnBluetoothChk);


        btnBluetoothChk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkBluetooth();
            }
        });
    }

    //블루투스 연결 체크
    void checkBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            showToast("블루투스를 지원하지 않습니다.");
            //finish();
        } else {
            //블루투스가 활성화가 되어있는지 확인
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
            } else {

            }
        }
    }

    //블루투스 장치 선택 메서드
    void selectDevice() {
        mDevices = bluetoothAdapter.getBondedDevices();
        mPairedDeviceCount = mDevices.size();
        if (mPairedDeviceCount == 0) {
            showToast("연결할 블루투스 장치가 없습니다.");
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("블루투스 장치 선택");
            List<String> listItems = new ArrayList<String>();
            for (BluetoothDevice device : mDevices) {
                listItems.add(device.getName());
            }
            listItems.add("취소");

            //아이템 항목을 선택하면 이벤트를 수행한다.
            final CharSequence items[] = listItems.toArray(new CharSequence[listItems.size()]);
            builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == mPairedDeviceCount) {
                        showToast("취소를 선택했습니다.");
                    } else {
                        connectToSelectDevice(items[which].toString());
                    }
                }
            });
            builder.setCancelable(false);
            builder.show();
        }
    }

    //선택 된 장치 연결
    void connectToSelectDevice(String selectedDeviceName) {
        mRemoteDevice = getDeviceFromBonedeList(selectedDeviceName);
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        try {
            mSocket = mRemoteDevice.createRfcommSocketToServiceRecord(uuid);
            mSocket.connect();
            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();

            beginListenForData();
            ivConnect.setImageResource(R.drawable.bluetooth_icon);

        }catch (Exception e) {
            showToast("현재 장치와 연결 중 오류가 발생했습니다.");
            ivConnect.setImageResource(R.drawable.bluetooth_grayicon);
        }
    }

    //페어링된 블루투스 장치를 이름을 찾는 부분
    BluetoothDevice getDeviceFromBonedeList(String name) {
        BluetoothDevice selectDevice = null;
        for (BluetoothDevice device : mDevices) {
            if (name.equals(device.getName())) {
                selectDevice = device;
                break;
            }
        }
        return selectDevice;
    }

    //데이터 수신 준비 및 처리
    void beginListenForData(){
        final Handler handler = new Handler();
        readBuffer = new byte[1024];
        readBufferPosition = 0;
        mWorkerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()){
                    try {
                        int bytesAvailable = mInputStream.available();
                        if(bytesAvailable > 0) {
                            byte paketBytes[] = new byte[bytesAvailable];
                            mInputStream.read(paketBytes);
                            for(int i = 0; i<bytesAvailable; i++) {
                                byte b = paketBytes[i];
                                if(b == mCharDelimiter){
                                    byte encodeBytes[] = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0 , encodeBytes, 0,encodeBytes.length);
                                    final  String data = new String(encodeBytes, "US-ASCII");
                                    readBufferPosition = 0;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            //수신된 문자열 데이터 처리 작업
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] =b;
                                }
                            }
                        }
                    } catch (IOException e){
                        showToast("데이터 수신 중 오류가 발생하였습니다.");
                    }
                }
            }
        });
        mWorkerThread.start();
    }

    //데이터 송신
    void sendData(String msg){
        msg += mStrDelimiter;
        try {
            mOutputStream.write(msg.getBytes());
        }catch (Exception e) {
            showToast("데이터 전송 중 오류가 발생했습니다.");
        }
    }

    @Override
    protected void onDestroy() {
        try {
            mWorkerThread.interrupt(); // 데이터 수신 스레드 종료
            mInputStream.close();
            mOutputStream.close();
            mSocket.close();
        } catch (Exception e) {

        }

        super.onDestroy();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    selectDevice();
                } else if (resultCode == RESULT_CANCELED) {
                    showToast("블루투스 연결을 취소했습니다.");
                }
                break;
        }
    }

    void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
