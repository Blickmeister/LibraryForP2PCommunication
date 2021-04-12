package cz.fim.uhk.thesis.libraryforp2pcommunication.communication;

import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import cz.fim.uhk.thesis.libraryforp2pcommunication.MainClass;

/**
 * @author Bc. Ondřej Schneider - FIM UHK
 * @version 1.0
 * @since 2021-04-06
 * Třída reprezentující roli P2P serveru
 * Definice komunikace: spojení s P2P klientem, zahájení naslouchání a zasílání zpráv,
 * obsah zasílaných zpráv
 */
public class ServerRole extends Thread {
    private final MainClass mainClass;

    private static final String TAG = "P2PLibrary/ServerRole";
    public static final int SOCKET_PORT_NUMBER = 8888;

    public ServerRole(MainClass mainClass) {
        this.mainClass = mainClass;
    }

    @Override
    public void run() {
        try {
            // vytvoření socketů pro komunikaci
            ServerSocket serverSocket = new ServerSocket(SOCKET_PORT_NUMBER);
            Socket socket = serverSocket.accept();
            // zahájení naslouchání komunikace
            SendReceive sendReceive = new SendReceive(socket, mainClass);
            sendReceive.start();
            // odeslání seznamu klientů na zařízení v roli klienta
            sendReceive.writeMessage(mainClass.getMessageToSend());
            Log.d(TAG, "Role P2P serveru se začíná realizovat....");
        } catch (IOException e) {
            Log.e(TAG, "Nepodařilo se vytvořit komunikační socket: ");
            e.printStackTrace();
        }
    }
}
