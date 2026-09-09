package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.preference.PreferenceManager;

import com.limelight.R;
import org.xmlpull.v1.XmlPullParser;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class SettingsProfileStore {
    static int active(Context context) {
        return SettingsProfileState.active(PreferenceManager.getDefaultSharedPreferences(context).getAll());
    }

    static synchronized void change(Context context, int target, boolean reset) throws IOException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, ?> before = prefs.getAll();
        Set<String> keys = managedKeys(context);
        Map<String, Object> after = reset ? SettingsProfileState.resetCurrent(before, keys)
                : SettingsProfileState.switchTo(before, keys, target);
        // Snapshot, destination values and selected slot share one file/transaction. A
        // process death cannot leave the selected slot pointing at another slot's values.
        if (!write(prefs, before, after)) {
            write(prefs, prefs.getAll(), before);
            throw new IOException("Unable to save settings profile");
        }
    }

    static Set<String> managedKeys(Context context) throws IOException {
        Set<String> keys = new HashSet<>(Arrays.asList(SettingsProfileKeys.RUNTIME_KEYS));
        try (XmlResourceParser xml = context.getResources().getXml(R.xml.preferences)) {
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.getEventType() != XmlPullParser.START_TAG) continue;
                String tag = xml.getName();
                if ("CheckBoxPreference".equals(tag) || "ListPreference".equals(tag)
                        || "EditTextPreference".equals(tag) || tag.endsWith("SeekBarPreference")
                        || tag.endsWith("LanguagePreference") || tag.endsWith("SmallIconCheckboxPreference")) {
                    String key = xml.getAttributeValue("http://schemas.android.com/apk/res/android", "key");
                    if (key != null) keys.add(key);
                }
            }
        } catch (Exception e) {
            throw new IOException("Unable to read settings keys", e);
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static boolean write(SharedPreferences prefs, Map<String, ?> before, Map<String, ?> after) {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : before.keySet()) if (!after.containsKey(key)) editor.remove(key);
        for (Map.Entry<String, ?> entry : after.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value.equals(before.get(key))) continue;
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
            else throw new IllegalArgumentException("Unsupported preference type for " + key);
        }
        return editor.commit();
    }

    private SettingsProfileStore() {}
}
