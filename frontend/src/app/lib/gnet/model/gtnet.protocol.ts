import { GTNetMessageCodeType } from './gtnet.message';

/**
 * What the backend says about one GTNet message code.
 *
 * The client used to keep three hand-maintained copies of this — a response map next to the message enum, a second one
 * next to the auto-answer rule model, and a list of the codes a rule may be written for. All three had drifted from the
 * server and from each other, so a reply the backend refuses was offered and every application code was missing.
 * Delivered by `GET /api/gtnetmessage/protocol`.
 */
export interface GTNetProtocolDescriptor {
  /** Enum constant name, which is also the NLS key. */
  name: string;
  /** Wire value, which is what a stored message row carries. */
  value: number;
  category: MessageCategory | string;
  /** An administrator may send this code from the message dialog. */
  userInitiable: boolean;
  /** A sent instance stays open until one of its answers arrives, and is delete protected until then. */
  requiresResponse: boolean;
  /** Names of the codes that answer this one. */
  validResponses: string[];
  /** The code carries a payload. */
  hasModel: boolean;
  /** The payload is filled in by the user, so `/msgformdefinition` describes it. */
  formEligible: boolean;
  /** The code may carry a replyTo without being an answer, as an admin message does. */
  threadable: boolean;
  /** The code may be the request of an auto-answer rule. */
  autoAnswerRequest: boolean;
  /** The code may be the answer of an auto-answer rule. */
  autoAnswerResponse: boolean;
}

/**
 * How a message behaves in the protocol. The values are the names of the backend enum
 * `grafiosch.gtnet.MessageCategory`; it declares no numeric values, so the mirror guard cannot enrol it.
 */
export enum MessageCategory {
  REQUEST = 'REQUEST',
  RESPONSE = 'RESPONSE',
  ANNOUNCEMENT = 'ANNOUNCEMENT'
}

/**
 * Resolves a code given either as its numeric value or as its constant name to the constant name the descriptors are
 * keyed by.
 *
 * @param messageCode the code as it appears on a message row or in a form control
 * @returns the constant name, or null when the value belongs to no known code
 */
export function messageCodeName(messageCode: GTNetMessageCodeType | number | string): string | null {
  if (messageCode == null) {
    return null;
  }
  return typeof messageCode === 'string' ? messageCode : (GTNetMessageCodeType[messageCode] ?? null);
}
