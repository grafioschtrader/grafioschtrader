import { GTNetMessageCodeType } from './gtnet.message';

/**
 * Corresponds to backend: grafiosch-base/src/main/java/grafiosch/gtnet/GTNetMessageAttemptStatus.java
 * Mirrored enum: GTNetMessageAttemptStatus
 */
export enum GTNetMessageAttemptStatus {
  QUEUED = 0,
  WAITING_HANDSHAKE = 1,
  RETRYABLE_FAILURE = 2,
  DELIVERED = 3,
  PEER_OUT_OF_SERVICE = 4,
  EXPIRED = 5
}

/** Administrator-facing per-target outcome of one outgoing background message. */
export interface GTNetMessageAttemptView {
  idGtNetMessageAttempt: number;
  idGtNetMessage: number;
  messageCode: GTNetMessageCodeType | string;
  messageTimestamp: string;
  idGtNet: number;
  targetDomain: string;
  attemptStatus: GTNetMessageAttemptStatus | string;
  tryCount: number;
  lastAttemptTimestamp?: string;
  sendTimestamp?: string;
  lastError?: string;
}
