package com.zoltjeppsson.nfcmifarereader;

import java.io.IOException;
import java.util.ArrayList;

import android.app.ListActivity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class NfcMifareReader extends ListActivity {
    private static final String BLOCK_DATA_EXTRA = "data";
    private static final String NEW_BLOCK_INTENT = "com.zoltjeppsson.NEW_BLOCK";

    private static final boolean DEBUG = true;
    private static final String TAG = "NfcMifareReader";

    private ArrayList<ByteArray> mBlockList = new ArrayList<ByteArray>();
    private ArrayAdapter<ByteArray> mBlockAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBlockAdapter = new ArrayAdapter<ByteArray>(this, android.R.layout.simple_list_item_1, mBlockList);
        setListAdapter(mBlockAdapter);

        IntentFilter filter = new IntentFilter();
        filter.addAction(NEW_BLOCK_INTENT);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.i(TAG, "onDestroy()");
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        IntentFilter[] tagFilters = new IntentFilter[] {tagDetected};

        PendingIntent nfcPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        NfcAdapter.getDefaultAdapter(this).enableForegroundDispatch(this, nfcPendingIntent, tagFilters, null);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        if (DEBUG) Log.i(TAG, "onListItemClick() " + position + " " + id);
        ByteArray blockData = mBlockList.get(position);
        blockData.toggleCoding();
        mBlockList.set(position, blockData);
        mBlockAdapter.notifyDataSetChanged();
    }

    @Override
    public void onNewIntent(Intent intent) {
        final Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

        new Thread("nfc_block_reader") {
            public void run() {

                MifareClassic tech =  MifareClassic.get(tag);

                if (tech == null)
                    return;

                try {
                    tech.connect();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

                final int blockCount = tech.getBlockCount();
                Log.i(TAG, "block count: " + blockCount);

                for (int i = 0; i < blockCount; i++) {
                    try {
                        final byte[] dataBlock;
                        if (tech.authenticateSectorWithKeyA(tech.blockToSector(i), MifareClassic.KEY_NFC_FORUM)) {
                            Log.i(TAG, "i: " + i + " auth: KEY_NFC_FORUM");
                            dataBlock = tech.readBlock(i);
                        } else if (tech.authenticateSectorWithKeyA(tech.blockToSector(i), MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY)) {
                            Log.i(TAG, "i: " + i + " auth: KEY_MIFARE_APPLICATION_DIRECTORY");
                            dataBlock = tech.readBlock(i);
                        } else if (tech.authenticateSectorWithKeyA(tech.blockToSector(i), MifareClassic.KEY_DEFAULT)) {
                            Log.i(TAG, "i: " + i + " auth: KEY_DEFAULT");
                            dataBlock = tech.readBlock(i);
                        } else {
                            Log.i(TAG, "i: " + i + " authentication failed");
                            dataBlock = "Authentication failed!".getBytes("UTF-8");
                        }

                        Intent intent = new Intent(NEW_BLOCK_INTENT);
                        intent.putExtra(BLOCK_DATA_EXTRA, dataBlock);
                        sendBroadcast(intent);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (DEBUG) Log.d(TAG, "done.");

                try {
                    tech.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (DEBUG) Log.d(TAG, "receiver thread stopped.");
            }
        }.start();
    }

    BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "onReceive() " + action);

            if (NEW_BLOCK_INTENT.equals(action)) {
                ByteArray dataArray = new ByteArray();
                dataArray.add(intent.getByteArrayExtra(BLOCK_DATA_EXTRA));
                mBlockList.add(dataArray);
                mBlockAdapter.notifyDataSetChanged();
            }
        }
    };
}
