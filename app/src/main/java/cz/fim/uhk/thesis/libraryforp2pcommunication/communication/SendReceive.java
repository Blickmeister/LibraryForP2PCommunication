package cz.fim.uhk.thesis.libraryforp2pcommunication.communication;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import cz.fim.uhk.thesis.libraryforp2pcommunication.MainClass;

/**
 * @author Bc. Ondřej Schneider - FIM UHK
 * @version 1.0
 * @since 2021-04-06
 * Třída definující formu komunikace (zpráv) mezi P2P klientem a P2P serverem
 * Jakým způsobem jsou zprávy zasílány a přijímány či čteny
 */
public class SendReceive extends Thread {
    private final Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final MainClass mainClass;

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
                // pokud zpráva není prázdná
                if (bytes > 0) {
                    Log.d(TAG, "readMessage() - zaslaná zpráva se právě čte");
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
            Log.d(TAG, "writeMessage() - zpráva se odesílá");
        } catch (IOException e) {
            Log.e(TAG, "Nepodařilo se zapsat zprávu k odeslání: ");
            e.printStackTrace();
        }
    }
}
