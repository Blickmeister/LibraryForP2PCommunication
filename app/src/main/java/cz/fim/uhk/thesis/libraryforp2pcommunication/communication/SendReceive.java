package cz.fim.uhk.thesis.libraryforp2pcommunication.communication;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Target;
import java.net.Socket;

import cz.fim.uhk.thesis.libraryforp2pcommunication.MainClass;

public class SendReceive extends Thread {
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private MainClass mainClass;

    private static final String TAG = "P2PLibrary/SendReceive";
    private static final int BUFFER_SIZE = 1024;

    public SendReceive(Socket skt, MainClass mainClass) {
        this.socket = skt;
        this.mainClass = mainClass;
        try {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "Problém se získáním streamů ze socket objektu: ");
            e.printStackTrace();
        }
    }

    // naslouchání obdrženým zprávám běží asynchronně v novém vlákně
    @Override
    public void run() {
        readMessage();
    }

    // metoda pro obdržení zprávy
    public void readMessage() {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytes;

        while (socket != null) {
            try {
                bytes = inputStream.read(buffer);
                Log.d(TAG, "readMessage TOTO");
                // pokud zpráva není prázdná
                if (bytes > 0) {
                    Log.d(TAG, "readMessage Handleros poslanos TOTI");
                    mainClass.handler.obtainMessage(MainClass.getMessageRead(), bytes, -1, buffer)
                            .sendToTarget();
                }
            } catch (IOException e) {
                Log.e(TAG, "Nepodařilo se přečíst přijatou zprávu: ");
                e.printStackTrace();
            }
        }
    }

    // metoda pro zaslání zprávy
    public void writeMessage(byte[] bytes) {
        try {
            outputStream.write(bytes);
            Log.d(TAG, "Write Message poslanos");
        } catch (IOException e) {
            Log.e(TAG, "Nepodařilo se zapsat zprávu k odeslání: ");
            e.printStackTrace();
        }
    }
}
