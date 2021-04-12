package cz.fim.uhk.thesis.libraryforp2pcommunication;

import android.content.Context;

/**
 * @author Bc. Ondřej Schneider - FIM UHK
 * @version 1.0
 * @since 2021-04-06
 * Společné rozhraní pro řízení chodu externích knihoven - musí být implementováno každou knihovnou
 */
public interface LibraryLoaderInterface {
    int start(String path, Context context);

    int stop();

    int resume(String path, Context context);

    int exit();

    String getDescription();
}
