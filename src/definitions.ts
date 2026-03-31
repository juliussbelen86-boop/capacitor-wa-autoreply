export interface WAAutoReplyPlugin {
  /**
   * Check if notification listener permission is granted.
   * The user must enable "Notification Access" in Android settings.
   */
  checkPermission(): Promise<{ granted: boolean }>;

  /**
   * Open Android notification listener settings so the user can grant permission.
   */
  requestPermission(): Promise<{ opened: boolean }>;

  /**
   * Reply to a WhatsApp message via the notification's RemoteInput.
   * Only works if there's an active notification from the specified contact.
   * @param options - contact name and message to send
   */
  reply(options: { contact: string; message: string }): Promise<{ sent: boolean }>;

  /**
   * Read contacts from the device's phone book.
   * Requires READ_CONTACTS permission.
   * @returns Map of contact names to phone numbers
   */
  getContacts(): Promise<{ contactos: Record<string, string>; total: number }>;

  /**
   * Save configuration to SharedPreferences (accessible from background service).
   * @param options - token, userId, and server URL
   */
  saveConfig(options: { token: string; userId: string; server: string }): Promise<{ saved: boolean }>;

  /**
   * Pause or resume auto-reply from JavaScript.
   * @param options - paused state
   */
  setPaused(options: { paused: boolean }): Promise<{ paused: boolean }>;

  /**
   * Listen for incoming WhatsApp messages.
   * Fired when a WhatsApp notification is received from another person.
   */
  addListener(
    eventName: 'whatsappMessage',
    listenerFunc: (data: { contact: string; message: string; timestamp: number }) => void
  ): Promise<void>;

  /**
   * Listen for outgoing messages (sent by the device owner).
   * Useful for learning the user's writing style.
   */
  addListener(
    eventName: 'ownMessage',
    listenerFunc: (data: { contact: string; message: string; timestamp: number }) => void
  ): Promise<void>;
}
