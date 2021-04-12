package cz.fim.uhk.thesis.libraryforp2pcommunication;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import cz.fim.uhk.thesis.libraryforp2pcommunication.communication.ClientRole;
import cz.fim.uhk.thesis.libraryforp2pcommunication.communication.ServerRole;
import cz.fim.uhk.thesis.libraryforp2pcommunication.receiver.WifiDirectBroadcastReceiver;

/**
 * @author Bc. Ondřej Schneider - FIM UHK
 * @version 1.0
 * @since 2021-04-06
 * Knihovna realizující role P2P serveru a P2P klienta v konceptu hybridního klienta
 * Především pro p2p komunikaci a předávání zpráv, jejichž podoba je dána právě rolí, kterou klient zastává
 * hlavní (řídící) třída knihovny
 */
public class MainClass extends AppCompatActivity implements LibraryLoaderInterface {
    // TAG pro logování
    private static final String TAG = "P2PLibrary/MainClass";
    // cesta k descriptor souboru
    private static final String PATH_TO_DESC = "/LibraryForOfflineMode/descriptor.txt";
    // konstanty reprezentující hodnoty módu spuštění aplikace
    private static final int RUN_LIBRARY_AS_CLIENT_VALUE = 0;
    private static final int RUN_LIBRARY_AS_SERVER_VALUE = 1;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    // označení pro příchozí zprávy od zařízení v roli klienta
    private static final int MESSAGE_READ = 1;
    // pojmenování události a objektu pro předání výsledku knihovny do aplikace (zachycení v aplikaci)
    private static final String RECEIVE_CLIENTS_ACTION_NAME =
            "cz.fim.uhk.thesis.hybridClient.receiveClientsAction";
    private static final String RECEIVED_CLIENTS_EXTRA_NAME =
            "cz.uhk.fim.thesis.hybridClient.receivedClients";
    private static final String RECEIVED_CLIENT_INFO_ACTION_NAME =
            "cz.fim.uhk.thesis.hybridClient.receiveClientInfoAction";
    private static final String RECEIVED_CLIENT_INFO_EXTRA_NAME =
            "cz.uhk.fim.thesis.hybridClient.receivedClientInfo";

    // proměnné pro kontext aplikace
    private Context context;
    private String libraryPathInApp;

    // u p2p knihovny navíc potřeba instance hlavní aktivity a mód, ve kterém má být knihovna spuštěna
    private Activity mainActivity;
    // 0 -> knihovna je spuštěna jako p2p klient
    // 1 -> knihovna je spuštěna jako p2p server - host
    private int mode;
    // u p2p knihovny navíc potřeba získat data z aplikace pro odeslání v podobě byte[]
    byte[] messageToSend;

    // informace o úspěšnosti zavedení knihovny - slouží k nastavení návratové hodnoty metody start()
    // informace o úspěšnosti se dá rovněž předat v Intent objektu pomocí Broadcast
    // zde jen pro ukázku -> metoda start() vrací úspěšnost zavedení, nikoliv správnou funkci - viz. text práce
    private boolean isLibraryLoadedSuccessfully;

    // nastavení a sledování stavu Wi-Fi
    private WifiManager wifiManager;
    // správa Wi-Fi peer-to-peer připojení
    private WifiP2pManager p2pManager;
    // kanál pojící aplikaci s Wi-Fi p2p frameworkem
    private WifiP2pManager.Channel channel;
    // BroadcastReceiver pro obsluhu událostí Wi-Fi P2P komunikace
    private BroadcastReceiver p2pReceiver;
    // IntentFilter pro naslouchání a zachytávání událostí Wi-Fi P2P komunikace
    private IntentFilter intentFilter;
    // Seznam dostupných P2P zařízení (peers)
    private final List<WifiP2pDevice> peers = new ArrayList<>();
    private WifiP2pDevice[] peersArray;

    // instance tříd pro komunikaci mezi rolí klienta a serveru
    private ServerRole serverRole;
    private ClientRole clientRole;

    @Override
    public int start(String path, Context context) {
        Log.d(TAG, "Knihovna startuje");
        this.context = context;
        this.libraryPathInApp = path;

        try {
            // init WifiManager objektu
            wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            // kontrola stavu Wi-Fi
            if (!wifiManager.isWifiEnabled()) {
                // pokud není Wi-Fi zapnuta -> zapneme
                wifiManager.setWifiEnabled(true);
            }
            // init WiFi p2p manažera, kanálu, receiveru a intent filteru
            p2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
            channel = p2pManager.initialize(context, context.getMainLooper(), null);
            p2pReceiver = new WifiDirectBroadcastReceiver(p2pManager, channel, this);
            intentFilter = new IntentFilter();

            // definici událostí, kterým má aplikace naslouchat pomocí Intent filteru
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

            // registrace Receiveru
            startListening();

            // start procesu vykonávání P2P funkcí knihovny - začíná vyhledáním zařízení (peers)
            discoverPeers();
            Log.d(TAG, "Knihovna běží");
            return 0;
        } catch (Exception ex) {
            Log.e(TAG, "Nepodařilo se zavést knihovnu metodou start(): ");
            ex.printStackTrace();
            return 1;
        }
    }

    // metoda pro vyhledání zařízení (peers)
    public void discoverPeers() {
        // metoda discoverPeers() žádá povolení k získání polohy - ACCESS_FINE_LOCATION
        // kontrola zažádání povolení k získání polohy
        if (checkLocationPermission()) {
            // inicializace celého procesu vyhledávání a získání seznamu potenciálních zařízení-klientů
            // (peers) pro připojení
            p2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    // podařilo se zahájit vyhledávání
                    Log.d(TAG, "Vyhledávání peers zahájeno");
                }

                @Override
                public void onFailure(int reason) {
                    // nepodařilo se zahájit vyhledávání
                    isLibraryLoadedSuccessfully = false;
                    Log.e(TAG, "Nepodařilo se vyhledat dostupné peers. Důvod:" + reason);
                }
            });
        }

    }

    // metoda pro připojení k vyhledaným zařízení
    private void connectToPeers() {
        // metoda connect() opět vyžaduje povolení k poloze -> v této fázi by již mělo být přiděleno vždy
        if (ActivityCompat.checkSelfPermission(context, MainClass.getFineLocation())
                == PackageManager.PERMISSION_GRANTED) {
            // inicializace procesu připojení k vyhledaným zařízením (seznam vyhledaných peers)
            for (WifiP2pDevice device : peersArray) {
                // objekt konfigurace Wi-Fi P2P připojení k zařízení
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;

                // nastavení pravděpodobnosti, že pojící se zařízení bude group owner dle
                // groupOwnerIntent -> čím vyšší hodnota -> tím vyšší pravděpodobnost
                if (mode == RUN_LIBRARY_AS_CLIENT_VALUE) {
                    // jedná-li se o klienta -> mamá adresu serveru -> chceme aby byl group owner
                    config.groupOwnerIntent = 15;
                } else {
                    // naopak chceme, aby klient nebyl group owner
                    config.groupOwnerIntent = 0;
                }
                // init procesu připojení k zařízení
                p2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        // úspěšné připojení
                        isLibraryLoadedSuccessfully = true;
                        Log.d(TAG, "Připojení k zařízení " + device.deviceAddress + "bylo úspěšné");
                    }

                    @Override
                    public void onFailure(int reason) {
                        // neúspěšné připojení
                        isLibraryLoadedSuccessfully = false;
                        Log.e(TAG, "Připojení k zařízení" + device.deviceAddress + "se nezdařilo");
                    }
                });
            }
        }
    }

    private boolean checkLocationPermission() {
        String[] locationPermission = {FINE_LOCATION};
        if (ActivityCompat.checkSelfPermission(context, FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(mainActivity, locationPermission,
                    LOCATION_PERMISSION_REQUEST_CODE);
            return false;
        } else {
            // povolení již uděleno -> lze pokračovat
            //locationPermissionGranted = true;
            return true;
        }
    }

    // metoda pro definici chování po udělení povolení uživatelem
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // kontrola výsledku zažádání o povolení
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0) {
                // cyklus pro případ zvýšení počtu povolení (v tuhle chvíli pouze jedno)
                for (int grantResult : grantResults) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(context, "Uživatel zamítl povolení k poloze:" +
                                        " není možné spustit funkci P2P komunikace",
                                Toast.LENGTH_LONG).show();
                        isLibraryLoadedSuccessfully = false;
                        return;
                    }
                }
                // povolení uděleno -> lze znovu zahájit proces vykonávání P2P funkcí knihovny
                discoverPeers();
            }
        }
    }

    // listener naslouchájící metodě requestPeers(), která je aktivována při každé změně v seznamu
    // dostupných zařízení skrze Wi-Fi P2P (peers)
    // slouží pro získání tohoto (nového) seznamu
    public WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            if (!peerList.getDeviceList().equals(peers)) {
                Log.d(TAG, "změna v seznamu zařízení peers");
                // došlo k updatu seznamu
                peers.clear();
                peers.addAll(peerList.getDeviceList());

                peersArray = new WifiP2pDevice[peerList.getDeviceList().size()];
                int index = 0;
                for (WifiP2pDevice device : peerList.getDeviceList()) {
                    peersArray[index] = device;
                    index++;
                }
                // zahájení procesu připojení vždy při změně seznamu peers, pokud není seznam prázdný
                // a připojení iniciuje zařízení s rolí serveru TODO ASI takhle ma byt
                if(peers.size() != 0 && mode == RUN_LIBRARY_AS_SERVER_VALUE) connectToPeers();
                //if (peers.size() != 0) connectToPeers();
            }

            // kontrola zda je seznam peers prázdný
            if (peers.size() == 0) {
                Toast.makeText(context, "Nebyla nalezena žádná dostupná zařízení",
                        Toast.LENGTH_LONG).show();
            }
        }
    };

    // listener naslouchájící metodě requestConnectionInfo(), která je aktivována při jakékoliv změně
    // připojení zařízení (peers)
    // slouží k definici funkcí obou rolí (server-klient) po úspěšném připojení
    public WifiP2pManager.ConnectionInfoListener connectionInfoListener = info -> {
        final InetAddress groupOwnerAddress = info.groupOwnerAddress;
        // definice funkcí dle rolí v P2P připojení (groupOwner == server role)
        Log.d(TAG, "info groupFormed null?: " + info.groupFormed + " groupOwner?: " + info.isGroupOwner);
        if (info.groupFormed && info.isGroupOwner) {
            // host - server role
            Log.d(TAG, "Zařízení připojeno v roli serveru");
            Toast.makeText(context, "Zařízení připojeno v roli serveru", Toast.LENGTH_SHORT)
                    .show();
            serverRole = new ServerRole(this);
            serverRole.start();
        } else if (info.groupFormed) {
            // klient role
            Log.d(TAG, "Zařízení připojeno v roli klienta");
            Toast.makeText(context, "Zařízení připojeno v roli klienta", Toast.LENGTH_SHORT)
                    .show();
            clientRole = new ClientRole(groupOwnerAddress, this);
            clientRole.start();
        }
    };

    // handler pro zpracovávní příchozích zpráv po získání zprávy pomocí readMessage() v novém vlákně
    // - třída SendReceive
    public Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (msg.what == MESSAGE_READ) {
                if (mode == RUN_LIBRARY_AS_SERVER_VALUE) {
                    // informace ze senzorů a info o přerušení komunikace od zařízení v roli klienta
                    // pro zaslání do aplikace
                    byte[] receivedClientInformation = (byte[]) msg.obj;
                    // předání výsledku do aplikace skrze Intent
                    Intent intent = new Intent(RECEIVED_CLIENT_INFO_ACTION_NAME);
                    intent.putExtra(RECEIVED_CLIENT_INFO_EXTRA_NAME, receivedClientInformation);
                    mainActivity.getApplicationContext().sendBroadcast(intent);
                    Log.d(TAG, "Knihovna úspěšně běží v roli serveru");
                } else {
                    // obdržený seznam klientů od zařízení v roli serveru v byte[] podobě
                    // pro zaslání do aplikace
                    byte[] receivedClients = (byte[]) msg.obj;
                    // předání výsledku do aplikace skrze Intent
                    Intent intent = new Intent(RECEIVE_CLIENTS_ACTION_NAME);
                    intent.putExtra(RECEIVED_CLIENTS_EXTRA_NAME, receivedClients);
                    mainActivity.getApplicationContext().sendBroadcast(intent);
                    Log.d(TAG, "Knihovna úspěšně běží v roli klienta");
                }
            }
            stopListening(); // zprávy odeslány -> můžeme zastavit vykonávání knihovny
            return true;
        }
    });


    @Override
    public int stop() {
        // Tady se nám náramně hodí metody stop() a resume(), neboť lze metody použít v onResume()
        // a onPause() MainActivity bez řešení další dodatečné implementace
        try {
            // odpojení Receiveru a přerušení vláken
            try {
                stopListening();
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "P2P Receiver již byl odregistrován");
            }
            // přerušení vláken
            if (serverRole != null && serverRole.isAlive()) serverRole.interrupt();
            if (clientRole != null && clientRole.isAlive()) clientRole.interrupt();

            return 0;
        } catch (Exception ex) {
            Log.e(TAG, "Při zastavení běhu knihovny došlo k chybě: ");
            ex.printStackTrace();
            return 1;
        }
    }

    @Override
    public int resume(String path, Context context) {
        // znovu obnovení chodu knihovny
        // metoda velmi podobná metodě start(), tedy prvnotnímu zavedení knihovny
        Log.d(TAG, "Knihovna znovu startuje");
        this.context = context;
        this.libraryPathInApp = path;

        try {
            // init WifiManager objektu
            wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            // kontrola stavu Wi-Fi
            if (!wifiManager.isWifiEnabled()) {
                // pokud není Wi-Fi zapnuta -> zapneme
                wifiManager.setWifiEnabled(true);
            }
            // init WiFi p2p manažera, kanálu, receiveru a intent filteru
            p2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
            channel = p2pManager.initialize(context, context.getMainLooper(), null);
            p2pReceiver = new WifiDirectBroadcastReceiver(p2pManager, channel, this);
            intentFilter = new IntentFilter();

            // definici událostí, kterým má aplikace naslouchat pomocí Intent filteru
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

            // registrace Receiveru
            startListening();

            // start procesu vykonávání P2P funkcí knihovny - začíná vyhledáním zařízení (peers)
            discoverPeers();
            Log.d(TAG, "Knihovna znovu běží");
            return 0;
        } catch (Exception ex) {
            Log.e(TAG, "Nepodařilo se znovu zavést knihovnu metodou resume(): ");
            ex.printStackTrace();
            return 1;
        }
    }

    @Override
    public int exit() {
        try {
            // odpojení Receiveru
            try {
                mainActivity.unregisterReceiver(p2pReceiver);
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "P2P Receiver již byl odregistrován");
            }
            // ukončení běhu vláken p2p serveru
            if (mode == RUN_LIBRARY_AS_SERVER_VALUE) {
                if (serverRole!= null && serverRole.isAlive()) {
                    serverRole.interrupt(); // klasický request na ukončení vlákna
                    serverRole.join(500); // čeká na ukončení vlákna 500 milisekund
                }
            }
            // stejné u p2p klienta
            if (mode == RUN_LIBRARY_AS_CLIENT_VALUE) {
                if (clientRole!= null && clientRole.isAlive()) {
                    clientRole.interrupt(); // klasický request na ukončení vlákna
                    clientRole.join(500); // čeká na ukončení vlákna 500 milisekund
                }
            }
            return 0;
        } catch (Exception ex) {
            Log.e(TAG, "Při uvolnění knihovny došlo k chybě");
            ex.printStackTrace();
            return 1;
        }
    }

    @Override
    public String getDescription() {
        // pro získání popisu knihovny z jejího deskriptoru umístěném v uložišti klienta
        List<String> data = new ArrayList<>();
        // získání dat z txt
        String pathToFile = libraryPathInApp + PATH_TO_DESC;
        Log.d(TAG, "Cesta k descriptor.txt: " + pathToFile);
        Scanner myReader = null;
        try {
            myReader = new Scanner(new File(pathToFile));
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Nepodařilo se získat popis z descriptor.txt, chyba: " + e.getMessage());
        }
        String line;
        while (myReader.hasNextLine()) {
            line = myReader.nextLine();
            String[] split = line.split(":");
            data.add(split[1]);
        }
        return data.get(4);
    }

    // inicializační metoda předání důležitých proměnných z aplikace do knihovny
    // nutné zavolat před zavedením knihovny pomocí start() metody!!
    public void initialize(Activity activity, int mode, byte[] messageToSend) {
        this.mainActivity = activity;
        this.messageToSend = messageToSend;
        this.mode = mode;
    }

    // metody pro zahájení/ukončení naslouchání změnám, které jsou vyvolány funkcemi p2p knihovny
    // inicializují a ukončují vykonávání knihovny
    public void startListening() {
        // registrace Receiveru
        mainActivity.registerReceiver(p2pReceiver, intentFilter);
    }
    public void stopListening() {
        // odregitrování Receiveru
        mainActivity.unregisterReceiver(p2pReceiver);
    }

    public static String getFineLocation() {
        return FINE_LOCATION;
    }

    public static int getMessageRead() {
        return MESSAGE_READ;
    }

    public byte[] getMessageToSend() {
        return messageToSend;
    }
}