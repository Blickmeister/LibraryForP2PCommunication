package cz.fim.uhk.thesis.libraryforp2pcommunication;

import android.content.Context;

import java.util.List;

public interface LibraryLoaderInterface {
    int start(String path, Context context);

    int stop();

    int resume();

    int exit();

    String getDescription();
}
