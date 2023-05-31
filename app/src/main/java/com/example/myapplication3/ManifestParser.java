package com.example.myapplication3;

import android.util.Log;

import net.dongliu.apk.parser.ApkFile;

import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.Set;

public class ManifestParser {

    public ManifestParser() {
    }

    public static String getManifestPath(String packageName) {
        return "/data/app/" + packageName + "-*/base.apk";
    }

    public float[] getInputVector(String packageName) {
        String manifestPath = getManifestPath(packageName);
        String manifest = getManifest(manifestPath);
        return parseManifest(manifest);
    }

    private float[] parseManifest1(String manifest) {
        String[] permissions = manifest.split("<uses-permission android:name=\"");
        float[] inputVector = new float[permissions.length];
        for (int i = 1; i < permissions.length; i++) {
            String permission = permissions[i];
            String[] permissionParts = permission.split("\"");
            String permissionName = permissionParts[0];
            //inputVector[i] = getPermissionValue(permissionName);
        }
        return inputVector;
    }

    private Hashtable<String, Integer> createFeatureDict(){

    }
    private float[] parseManifest(String manifest) {
        Hashtable<String, Integer> featuresTable = new Hashtable<String, Integer>();
        // read the list of permissions, intent filters and hardware features in the res/raw folder
        // and initialize the featuresTable with them

        // read the file res/raw/permissions and add the permissions to the featuresTable
        try {
            BufferedReader reader = new BufferedReader(new FileReader("res/raw/permissions"));
            String line = null;
            StringBuilder stringBuilder = new StringBuilder();
            String ls = System.getProperty("line.separator");

            while((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(ls);
            }

            // log stringBuilder.toString();
            String[] permissions = stringBuilder.toString().split(ls);
            for (String permission : permissions) {
                featuresTable.put(permission, 0);
            }
            reader.close();

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //log the keys in the hashtable
        Set<String> keys = featuresTable.keySet();
        for (String key : keys) {
            Log.d("key", key);
        }

        // read the file res/raw/intent_filters and add the intent filters to the featuresTable
        // read the file res/raw/hardware_features and add the hardware features to the featuresTable

        return null;
    }

    private String getManifest(String manifestPath){
        try {
            ApkFile apkFile = new ApkFile(new File(manifestPath));
            return apkFile.getManifestXml();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
