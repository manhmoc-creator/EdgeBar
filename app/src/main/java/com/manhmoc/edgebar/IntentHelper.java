package com.manhmoc.edgebar;
import android.content.Intent;
public class IntentHelper {
    public static Intent getQrIntent() {
        Intent i = new Intent("com.google.zxing.client.android.SCAN");
        i.setPackage("com.google.android.gms");
        return i;
    }
}
