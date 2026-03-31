package com.alteri.wareply;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Capacitor plugin for WhatsApp auto-reply.
 * Uses NotificationListenerService to read messages and RemoteInput to reply.
 *
 * Built by the Alteri team — https://github.com/juliussbelen86-boop/capacitor-wa-autoreply
 */
@CapacitorPlugin(name = "WAAutoReply")
public class WAAutoReplyPlugin extends Plugin {
    private static final String TAG = "WAAutoReply";
    private static final String PREFS_NAME = "WAAutoReplyPrefs";
    private BroadcastReceiver receiver;

    @Override
    public void load() {
        Log.i(TAG, "Plugin loaded");

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;

                String contact = intent.getStringExtra("contact");
                String message = intent.getStringExtra("message");
                long ts = intent.getLongExtra("timestamp", 0);
                if (contact == null || message == null) return;

                if (WANotificationService.ACTION_MESSAGE.equals(action)) {
                    JSObject data = new JSObject();
                    data.put("contact", contact);
                    data.put("message", message);
                    data.put("timestamp", ts);
                    notifyListeners("whatsappMessage", data);
                }

                if (WANotificationService.ACTION_OWN_MSG.equals(action)) {
                    JSObject data = new JSObject();
                    data.put("contact", contact);
                    data.put("message", message);
                    data.put("timestamp", ts);
                    notifyListeners("ownMessage", data);
                }

                if ((WANotificationService.ACTION_MESSAGE + ".DEBOUNCED").equals(action)) {
                    int count = intent.getIntExtra("count", 1);
                    JSObject data = new JSObject();
                    data.put("contact", contact);
                    data.put("message", message);
                    data.put("count", count);
                    notifyListeners("debouncedMessage", data);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(WANotificationService.ACTION_MESSAGE);
        filter.addAction(WANotificationService.ACTION_OWN_MSG);
        filter.addAction(WANotificationService.ACTION_MESSAGE + ".DEBOUNCED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getContext().registerReceiver(receiver, filter);
        }
    }

    @Override
    protected void handleOnDestroy() {
        if (receiver != null) try { getContext().unregisterReceiver(receiver); } catch (Exception e) {}
    }

    @PluginMethod
    public void checkPermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", isNotificationServiceEnabled());
        call.resolve(ret);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve(new JSObject().put("opened", true));
        } catch (Exception e) {
            call.reject("Could not open settings: " + e.getMessage());
        }
    }

    @PluginMethod
    public void reply(PluginCall call) {
        String contact = call.getString("contact");
        String message = call.getString("message");
        if (contact == null || message == null) { call.reject("Missing contact or message"); return; }

        Intent ri = new Intent(WANotificationService.ACTION_REPLY);
        ri.putExtra("contact", contact);
        ri.putExtra("message", message);
        ri.setPackage(getContext().getPackageName());
        getContext().sendBroadcast(ri);

        Log.i(TAG, "Reply sent: " + contact + " -> " + message.substring(0, Math.min(30, message.length())));
        call.resolve(new JSObject().put("sent", true));
    }

    @PluginMethod
    public void getContacts(PluginCall call) {
        try {
            android.content.ContentResolver cr = getContext().getContentResolver();
            android.database.Cursor cursor = cr.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                }, null, null, null
            );

            JSObject result = new JSObject();
            org.json.JSONObject contacts = new org.json.JSONObject();
            int count = 0;

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    String number = cursor.getString(1);
                    if (name != null && number != null) {
                        String clean = number.replaceAll("[^0-9]", "");
                        if (clean.length() >= 8) { contacts.put(name, clean); count++; }
                    }
                }
                cursor.close();
            }

            result.put("contactos", contacts);
            result.put("total", count);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("Error reading contacts: " + e.getMessage());
        }
    }

    @PluginMethod
    public void saveConfig(PluginCall call) {
        SharedPreferences.Editor ed = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        ed.putString("token", call.getString("token", ""));
        ed.putString("userId", call.getString("userId", ""));
        ed.putString("server", call.getString("server", ""));
        ed.apply();
        call.resolve(new JSObject().put("saved", true));
    }

    @PluginMethod
    public void setPaused(PluginCall call) {
        boolean paused = call.getBoolean("paused", false);
        getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("paused", paused).apply();
        call.resolve(new JSObject().put("paused", paused));
    }

    private boolean isNotificationServiceEnabled() {
        try {
            String pkg = getContext().getPackageName();
            String flat = Settings.Secure.getString(getContext().getContentResolver(), "enabled_notification_listeners");
            if (!TextUtils.isEmpty(flat)) {
                for (String name : flat.split(":")) {
                    ComponentName cn = ComponentName.unflattenFromString(name);
                    if (cn != null && TextUtils.equals(pkg, cn.getPackageName())) return true;
                }
            }
        } catch (Exception e) {}
        return false;
    }
}
