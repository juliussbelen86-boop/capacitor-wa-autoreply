package com.alteri.wareply;

import android.app.Notification;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listens to WhatsApp notifications and provides auto-reply capabilities
 * via Android's RemoteInput API.
 *
 * Built by the Alteri team — https://github.com/juliussbelen86-boop/capacitor-wa-autoreply
 */
public class WANotificationService extends NotificationListenerService {
    private static final String TAG = "WAAutoReply";
    private static final String WHATSAPP_PKG = "com.whatsapp";
    private static final String WHATSAPP_BIZ = "com.whatsapp.w4b";

    public static final String ACTION_MESSAGE = "com.alteri.wareply.MESSAGE";
    public static final String ACTION_REPLY = "com.alteri.wareply.REPLY";
    public static final String ACTION_OWN_MSG = "com.alteri.wareply.OWN_MSG";

    private final Map<String, Notification.Action> replyActions = new HashMap<>();
    private final Map<String, Long> replyCooldowns = new HashMap<>();
    private final Map<String, List<String>> pendingMsgs = new HashMap<>();
    private final Map<String, Runnable> pendingTimers = new HashMap<>();
    private final Map<String, String> lastMsgPerContact = new HashMap<>();
    private final List<String> sentByPlugin = new ArrayList<>();

    private static final long COOLDOWN_MS = 20000;
    private static final int MAX_SENT_TRACKING = 50;
    private static final long DEBOUNCE_MS = 4000;

    private Handler bgHandler;
    private HandlerThread bgThread;
    private BroadcastReceiver replyReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service started");

        bgThread = new HandlerThread("WAAutoReplyBG");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());

        replyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (ACTION_REPLY.equals(intent.getAction())) {
                    String contact = intent.getStringExtra("contact");
                    String message = intent.getStringExtra("message");
                    if (contact != null && message != null) doReply(contact, message);
                }
            }
        };

        IntentFilter f = new IntentFilter(ACTION_REPLY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(replyReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(replyReceiver, f);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (replyReceiver != null) try { unregisterReceiver(replyReceiver); } catch (Exception e) {}
        if (bgThread != null) bgThread.quitSafely();
    }

    /**
     * Reply to a WhatsApp contact using the notification's RemoteInput.
     */
    public void doReply(String contactKey, String message) {
        Notification.Action action = replyActions.get(contactKey);
        if (action == null) { Log.w(TAG, "No reply action for: " + contactKey); return; }

        try {
            RemoteInput[] inputs = action.getRemoteInputs();
            if (inputs == null || inputs.length == 0) return;

            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            for (RemoteInput ri : inputs) bundle.putCharSequence(ri.getResultKey(), message);
            RemoteInput.addResultsToIntent(inputs, intent, bundle);
            action.actionIntent.send(getApplicationContext(), 0, intent);
            replyCooldowns.put(contactKey, System.currentTimeMillis());
            sentByPlugin.add(message);
            if (sentByPlugin.size() > MAX_SENT_TRACKING) sentByPlugin.remove(0);
            Log.i(TAG, "SENT to " + contactKey + ": " + message.substring(0, Math.min(40, message.length())));
        } catch (Exception e) {
            Log.e(TAG, "Reply error: " + e.getMessage());
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (!WHATSAPP_PKG.equals(pkg) && !WHATSAPP_BIZ.equals(pkg)) return;

        Notification notif = sbn.getNotification();
        if (notif == null) return;
        Bundle extras = notif.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        String text = textCs != null ? textCs.toString() : "";

        if (title.isEmpty() || text.isEmpty()) return;
        if (text.contains("messages") && text.contains("chat")) return;
        if (text.contains("mensajes") && text.contains("chat")) return;
        if (title.equals("WhatsApp") || title.equals("WhatsApp Business")) return;

        // Skip groups
        if (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) != null) return;

        String contact = title;
        String msg = text;

        // Detect own messages
        boolean isOwn = contact.equals("Tú") || contact.equals("Tu") || contact.equals("You")
                || contact.equals("Vous") || contact.equals("Du");
        if (!isOwn && (text.startsWith("Tú: ") || text.startsWith("You: "))) {
            isOwn = true;
            msg = text.substring(text.indexOf(": ") + 2);
        }

        if (isOwn) {
            // Filter out messages sent by this plugin
            if (sentByPlugin.contains(msg)) {
                sentByPlugin.remove(msg);
                Log.d(TAG, "Own msg from plugin, skipping");
                return;
            }

            CharSequence convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
            String dest = convTitle != null ? convTitle.toString() : "unknown";
            Log.i(TAG, "OWN -> " + dest + ": " + msg.substring(0, Math.min(50, msg.length())));

            Intent ownIntent = new Intent(ACTION_OWN_MSG);
            ownIntent.putExtra("contact", dest);
            ownIntent.putExtra("message", msg);
            ownIntent.putExtra("timestamp", sbn.getPostTime());
            ownIntent.setPackage(getPackageName());
            sendBroadcast(ownIntent);
            return;
        }

        // Store reply action
        boolean hasReply = false;
        if (notif.actions != null) {
            for (Notification.Action action : notif.actions) {
                if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                    replyActions.put(contact, action);
                    hasReply = true;
                    break;
                }
            }
        }
        if (!hasReply) return;

        // Cooldown
        Long lr = replyCooldowns.get(contact);
        if (lr != null && (System.currentTimeMillis() - lr) < COOLDOWN_MS) {
            Log.d(TAG, "Cooldown active for " + contact);
            return;
        }

        // Duplicate filter
        String lastMsg = lastMsgPerContact.get(contact);
        if (lastMsg != null && lastMsg.equals(msg)) { Log.d(TAG, "Duplicate skipped"); return; }
        lastMsgPerContact.put(contact, msg);

        Log.i(TAG, "MSG " + contact + ": " + msg.substring(0, Math.min(50, msg.length())));

        // Broadcast to plugin
        Intent bi = new Intent(ACTION_MESSAGE);
        bi.putExtra("contact", contact);
        bi.putExtra("message", msg);
        bi.putExtra("timestamp", sbn.getPostTime());
        bi.setPackage(getPackageName());
        sendBroadcast(bi);

        // Debounce
        final String fContact = contact;
        if (!pendingMsgs.containsKey(fContact)) pendingMsgs.put(fContact, new ArrayList<String>());
        pendingMsgs.get(fContact).add(msg);

        if (pendingTimers.containsKey(fContact)) bgHandler.removeCallbacks(pendingTimers.get(fContact));

        Runnable debounce = new Runnable() {
            @Override
            public void run() {
                List<String> msgs = pendingMsgs.remove(fContact);
                pendingTimers.remove(fContact);
                if (msgs == null || msgs.isEmpty()) return;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < msgs.size(); i++) { if (i > 0) sb.append(" "); sb.append(msgs.get(i)); }
                String full = sb.toString();
                Log.i(TAG, "Processing " + msgs.size() + " msg(s) from " + fContact);

                // Broadcast debounced message
                Intent dbi = new Intent(ACTION_MESSAGE + ".DEBOUNCED");
                dbi.putExtra("contact", fContact);
                dbi.putExtra("message", full);
                dbi.putExtra("count", msgs.size());
                dbi.setPackage(getPackageName());
                sendBroadcast(dbi);
            }
        };
        pendingTimers.put(fContact, debounce);
        bgHandler.postDelayed(debounce, DEBOUNCE_MS);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}
}
