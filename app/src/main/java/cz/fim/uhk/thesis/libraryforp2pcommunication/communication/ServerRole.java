package cz.fim.uhk.thesis.libraryforp2pcommunication.communication;

import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import cz.fim.uhk.thesis.libraryforp2pcommunication.MainClass;

public class ServerRole extends Thread {
    private Socket socket;
    private ServerSocket serverSocket;
    private MainClass mainClass;

    private static final String TAG = "P2PLibrary/ServerRole";
    public static final int SOCKET_PORT_NUMBER = 8888;

    public ServerRole(MainClass mainClass) {
        this.mainClass = mainClass;
    }

    @Override
    public void run() {
        try {
            // vytvoření socketů pro komunikaci
            serverSocket = new ServerSocket(SOCKET_PORT_NUMBER);
            socket = serverSocket.accept();
            // zahájení naslouchání komunikace
            SendReceive sendReceive = new SendReceive(socket, mainClass);
            sendReceive.start();
            // odeslání seznamu klientů na zařízení v roli klienta
            sendReceive.writeMessage(mainClass.getMessageToSend());
            Log.d(TAG, "Server role TOTu");
        } catch (IOException e) {
            Log.e(TAG, "Nepodařilo se vytvořit komunikační socket: ");
            e.printStackTrace();
        }
    }
}
