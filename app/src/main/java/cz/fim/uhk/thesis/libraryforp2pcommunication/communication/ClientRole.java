package cz.fim.uhk.thesis.libraryforp2pcommunication.communication;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import cz.fim.uhk.thesis.libraryforp2pcommunication.MainClass;

public class ClientRole extends Thread {
    private Socket socket;
    private String serverAddress;
    private MainClass mainClass;

    private static final String TAG = "P2PLibrary/ClientRole";
    private static final int CONNECTION_TIMEOUT = 5000;

    public ClientRole(InetAddress serverInetAddress, MainClass mainClass) {
        this.serverAddress = serverInetAddress.getHostAddress();
        this.mainClass = mainClass;
        socket = new Socket();
    }

    @Override
    public void run() {
        try {
            // připojeni klienta pomocí Socketu k serveru
            socket.connect(new InetSocketAddress(serverAddress, ServerRole.SOCKET_PORT_NUMBER), CONNECTION_TIMEOUT);
            // zahájení naslouchání komunikace
            SendReceive sendReceive = new SendReceive(socket, mainClass);
            sendReceive.start();
            // odeslání dat ze senzorů a infa o ukončení spojení na zařízení v roli serveru
            sendReceive.writeMessage(mainClass.getMessageToSend());
            Log.d(TAG, "Client role TOTu");
        } catch (IOException e) {
            Log.e(TAG, "Nepodařilo se připojit k serveru: ");
            e.printStackTrace();
        }
    }
}
