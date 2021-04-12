package cz.fim.uhk.thesis.libraryforp2pcommunication.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import cz.fim.uhk.thesis.libraryforp2pcommunication.MainClass;

/**
 * @author Bc. Ondřej Schneider - FIM UHK
 * @version 1.0
 * @since 2021-04-06
 * Hlavní Receiver pro naslouchání všem důležitým událostem v souvislosti s p2p komunikací,
 * který vyvolává (inciuje) všechny důležité p2p operace
 */
public class WifiDirectBroadcastReceiver extends BroadcastReceiver {
    private final WifiP2pManager p2pManager;
    private final WifiP2pManager.Channel channel;
    private final MainClass mainClass;

    // TAG pro logování
    private static final String TAG = "P2PLibrary/P2PReceiver";

    public WifiDirectBroadcastReceiver(WifiP2pManager p2pManager, WifiP2pManager.Channel channel,
                                       MainClass mainClass) {
        this.p2pManager = p2pManager;
        this.channel = channel;
        this.mainClass = mainClass;

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // vytažení akce z intentu
        String action = intent.getAction();
        // obsluha akcí/událostí, které mohou nastat při p2p komunikaci
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // indikuje stav Wi-Fi P2P - zda je zapnuta/vypnuta
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Toast.makeText(context, "Wi-Fi P2P zapnuta", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "Wi-Fi P2P vypnuta", Toast.LENGTH_LONG).show();
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            // indikuje změny v seznamu dostupných zařízení (peers)
            if (p2pManager != null) {
                // dojde-li ke změně v seznamu peers po zavolání metody discoverPeers()
                // lze zažádat o získání tohoto seznamu dostupných zařízení
                // opět nutné povolení získání polohy -> v této fázi by již mělo být přiděleno vždy
                if (ActivityCompat.checkSelfPermission(context, MainClass.getFineLocation())
                        == PackageManager.PERMISSION_GRANTED) {
                    p2pManager.requestPeers(channel, mainClass.peerListListener);
                }
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // indikuje změny v stavu Wi-Fi P2P připojení - změny v připojení zařízení (peerů)
            if (p2pManager == null) return;

            // informace o připojení
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            if (networkInfo.isConnected()) {
                // dojde-li ke změně v připojení zařízení (peers) po zavolání metody connect()
                // lze žádat informaci o stavu připojení a pomocí ní definovat chování obou rolí
                p2pManager.requestConnectionInfo(channel, mainClass.connectionInfoListener);
            } else {
                Toast.makeText(context, "Zařízení odpojeno: " + networkInfo.getReason(), Toast.LENGTH_LONG).show();
            }

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            // indikuje změny v konfiguraci tohoto zařízení - např. změna názvu zařízení apod.
            Log.d(TAG, "došlo ke změně v konfiguraci zařízení");
        }
    }
}
