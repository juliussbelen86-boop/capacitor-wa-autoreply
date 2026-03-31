import { registerPlugin } from '@capacitor/core';
import type { WAAutoReplyPlugin } from './definitions';

const WAAutoReply = registerPlugin<WAAutoReplyPlugin>('WAAutoReply');

export * from './definitions';
export { WAAutoReply };
