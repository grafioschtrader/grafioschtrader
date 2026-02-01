import {BaseParam} from '../../lib/entities/base.param';

/**
 * Request DTO for submitting GTNet admin messages to multiple targets via multi-select.
 * Allows administrators to send a single message to multiple selected peers using
 * background delivery via GTNetMessageAttempt.
 */
export class MultiTargetMsgRequest {
  /** List of GTNet domain IDs that should receive this message */
  idGTNetTargetDomains: number[] = [];

  /** Optional free-text note to include with the message */
  message: string = null;

  /** Typed parameters for the message */
  gtNetMessageParamMap: { [key: string]: BaseParam } = {};

  /** Visibility level: ALL_USERS or ADMIN_ONLY */
  visibility: string = null;

  constructor(targetIds: number[], message: string = null, visibility: string = null) {
    this.idGTNetTargetDomains = targetIds;
    this.message = message;
    this.visibility = visibility;
  }
}
