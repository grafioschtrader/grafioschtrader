/**
 * Online status of a GTNet peer.
 *
 * Lives in its own file rather than next to the other GTNet enums so that `enum.mirror.spec.ts`, which reads the
 * first enum of a mirror file, can guard it against the backend.
 *
 * Corresponds to backend: grafiosch-base/src/main/java/grafiosch/gtnet/GTNetServerOnlineStatusTypes.java
 */
export enum GTNetServerOnlineStatusTypes {
  SOS_UNKNOWN = 0,
  SOS_ONLINE = 1,
  SOS_OFFLINE = 2,
  /**
   * The peer announced that it discontinues its operation and the announced date has passed. Terminal: the peer is
   * never contacted again, and neither a status check nor an inbound message lifts it. An administrator can set the
   * status back by hand, or delete the peer.
   */
  SOS_OUT_OF_SERVICE = 3
}
