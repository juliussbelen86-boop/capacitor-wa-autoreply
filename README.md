# capacitor-wa-autoreply

<p align="center">
  <strong>Capacitor plugin for WhatsApp auto-reply on Android</strong>
</p>

<p align="center">
  Read incoming WhatsApp messages, reply automatically via notifications, and capture your own messages — all running in the background without opening WhatsApp.
</p>

<p align="center">
  <a href="https://www.npmjs.com/package/capacitor-wa-autoreply"><img src="https://img.shields.io/npm/v/capacitor-wa-autoreply.svg" alt="npm version"></a>
  <a href="https://www.npmjs.com/package/capacitor-wa-autoreply"><img src="https://img.shields.io/npm/dm/capacitor-wa-autoreply.svg" alt="npm downloads"></a>
  <a href="https://github.com/juliussbelen86-boop/capacitor-wa-autoreply/blob/main/LICENSE"><img src="https://img.shields.io/npm/l/capacitor-wa-autoreply.svg" alt="license"></a>
</p>

---

## How it works

This plugin uses Android's `NotificationListenerService` to capture WhatsApp notifications and `RemoteInput` to reply directly through the notification — the same mechanism used by Android Auto and smartwatches. **WhatsApp doesn't know your app exists.**

```
WhatsApp notification arrives
       ↓
NotificationListenerService captures contact + message
       ↓
Your app processes the message (AI, rules, etc.)
       ↓
RemoteInput replies through the notification
       ↓
Message appears in WhatsApp as if the user typed it
```

## Features

- 📩 **Read incoming WhatsApp messages** — contact name, message text, timestamp
- ✉️ **Auto-reply via RemoteInput** — replies appear as normal messages in WhatsApp
- 👤 **Capture own messages** — detect when the user sends messages manually
- 🔄 **Built-in debounce** — groups multiple rapid messages from the same contact
- 🛡️ **Anti-loop protection** — 20-second cooldown prevents reply loops
- 🔇 **Duplicate filter** — ignores duplicate notifications from WhatsApp
- 📱 **Background operation** — works with screen off, app closed
- 📇 **Read contacts** — access phone book for contact mapping
- ⏸️ **Pause/Resume** — toggle auto-reply on/off from your app

## Platform Support

| Platform | Supported |
|----------|-----------|
| Android  | ✅        |
| iOS      | ❌ (not possible due to Apple restrictions) |
| Web      | ❌        |

## Installation

```bash
npm install capacitor-wa-autoreply
npx cap sync
```

## Setup

### 1. Register the plugin in your `MainActivity.java`

```java
import com.alteri.wareply.WAAutoReplyPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(WAAutoReplyPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
```

### 2. Add the service to your `AndroidManifest.xml`

Inside `<application>`:

```xml
<service
    android:name="com.alteri.wareply.WANotificationService"
    android:label="WhatsApp Auto-Reply"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

### 3. Add permissions (optional, for contacts)

```xml
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

### 4. Grant notification access

The user must manually enable "Notification Access" for your app in Android Settings. You can open this screen programmatically:

```typescript
import { WAAutoReply } from 'capacitor-wa-autoreply';

const { granted } = await WAAutoReply.checkPermission();
if (!granted) {
  await WAAutoReply.requestPermission(); // Opens Android settings
}
```

## Usage

### Listen for incoming messages

```typescript
import { WAAutoReply } from 'capacitor-wa-autoreply';

WAAutoReply.addListener('whatsappMessage', (data) => {
  console.log(`${data.contact}: ${data.message}`);
  // Process with your AI, rules engine, etc.
});
```

### Reply to a message

```typescript
await WAAutoReply.reply({
  contact: 'John',    // Must match the notification title
  message: 'Got it!'  // Your reply
});
```

### Listen for debounced messages

When a contact sends multiple messages quickly, they're grouped:

```typescript
WAAutoReply.addListener('debouncedMessage', (data) => {
  console.log(`${data.contact} sent ${data.count} messages: ${data.message}`);
  // All messages joined with spaces
});
```

### Capture own messages

Detect when the user sends messages manually (useful for learning their writing style):

```typescript
WAAutoReply.addListener('ownMessage', (data) => {
  console.log(`User sent to ${data.contact}: ${data.message}`);
});
```

### Pause/Resume

```typescript
await WAAutoReply.setPaused({ paused: true });  // Stop auto-replying
await WAAutoReply.setPaused({ paused: false }); // Resume
```

### Read phone contacts

```typescript
const { contactos, total } = await WAAutoReply.getContacts();
// contactos = { "John Doe": "1234567890", ... }
```

## API Reference

| Method | Description |
|--------|-------------|
| `checkPermission()` | Check if notification access is granted |
| `requestPermission()` | Open Android notification settings |
| `reply({ contact, message })` | Reply to a WhatsApp contact |
| `getContacts()` | Read device contact list |
| `saveConfig({ token, userId, server })` | Store config in SharedPreferences |
| `setPaused({ paused })` | Pause or resume auto-reply |

| Event | Description |
|-------|-------------|
| `whatsappMessage` | Incoming message from a contact |
| `ownMessage` | Message sent by the device owner |
| `debouncedMessage` | Grouped messages after 4s debounce |

## How is this different from WhatsApp Business API?

| | This plugin | WhatsApp Business API |
|---|---|---|
| Cost | Free | $0.005-0.08 per message |
| Setup | Install plugin, grant permission | Apply to Meta, get approved |
| Rate limits | None | 1000 messages/day initially |
| Risk of ban | Minimal (uses Android APIs) | None (official) |
| Works with personal WA | ✅ | ❌ (business accounts only) |
| Requires server | No | Yes |

## Important Notes

- **Android only** — iOS does not allow reading other apps' notifications
- **Notification access required** — users must manually grant this permission
- **Anti-abuse** — built-in cooldowns and duplicate filters prevent spam
- **Google Play compliance** — apps using `NotificationListenerService` must justify the permission in Play Store submission
- **This plugin does NOT access WhatsApp's internal APIs** — it only interacts with Android's notification system

## Use Cases

- 🤖 AI-powered auto-responders
- 📊 Message analytics and logging
- 🏢 Customer service automation
- ⏰ Out-of-office replies
- 🧠 Writing style analysis
- 🔔 Custom notification handling

## Credits

Built with ❤️ by the team behind **[Alteri](https://github.com/juliussbelen86-boop)**.

> [!IMPORTANT]
> **Status:** Closed Beta in progress with selected testers. Public Beta coming soon! Stay tuned.

## License

MIT
