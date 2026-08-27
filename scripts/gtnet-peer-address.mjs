/**
 * Resolves the address under which a GTNet peer running on this host reaches *itself*.
 *
 * Why this is not simply `http://localhost:<port>`:
 *
 *   - `GTNetJpaRepositoryImpl.isDomainNameThisMachine` walks the local `NetworkInterface` list and
 *     **skips every loopback interface**. An own entry registered as `localhost` or `127.0.0.1` is
 *     therefore never recognised as this instance, `g.gnet.my.entry.id` is never written, and the
 *     instance stays non-operational however the form is filled in.
 *   - On the very first `gt_net` insert (`gtNetJpaRepository.count() == 0`) `saveOnlyAttributes`
 *     probes the URL being saved through `BaseDataClient.getActuatorInfo`. The address has to be
 *     reachable *from the instance itself*, so the host must hairpin to its own address and the local
 *     firewall has to allow it.
 *
 * Hence: the first private IPv4 that actually accepts a TCP connection on the peer's own port — not
 * "the LAN address". VPN, Docker NAT (172.x), Hyper-V and WSL adapters all present plausible but
 * unroutable candidates, and only a connect attempt tells them apart.
 *
 * Always a literal IPv4, never a hostname: `BaseDataClient` builds its `WebClient` with
 * `ResolvedAddressTypes.IPV6_PREFERRED`, so a dual-stack name would steer the client to an address
 * `isDomainNameThisMachine` never matched.
 */

import net from 'node:net';
import os from 'node:os';
import { pathToFileURL } from 'node:url';

/** Milliseconds a single connect attempt may take before the candidate is discarded. */
const CONNECT_TIMEOUT_MS = 1500;

/** RFC 1918 ranges. A public address would work too, but a test host is not expected to have one. */
function isPrivateIPv4(address) {
  const [a, b] = address.split('.').map(Number);
  return a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
}

/** Every non-internal private IPv4 of this host, in the order the OS reports its interfaces. */
export function privateIPv4Candidates() {
  const candidates = [];
  for (const [name, addresses] of Object.entries(os.networkInterfaces())) {
    for (const entry of addresses ?? []) {
      // Node >= 18 reports family as the string 'IPv4'; older builds used the number 4.
      const isV4 = entry.family === 'IPv4' || entry.family === 4;
      if (isV4 && !entry.internal && isPrivateIPv4(entry.address)) {
        candidates.push({ name, address: entry.address });
      }
    }
  }
  return candidates;
}

function canConnect(host, port) {
  return new Promise(resolve => {
    const socket = net.connect({ port, host });
    const done = open => { socket.destroy(); resolve(open); };
    socket.setTimeout(CONNECT_TIMEOUT_MS, () => done(false));
    socket.once('connect', () => done(true));
    socket.once('error', () => done(false));
  });
}

/**
 * Returns `http://<private-ipv4>:<port>` for the first candidate that answers on `port`.
 *
 * The peer must already be listening — call this after the health gate, never before.
 *
 * @param port the peer's `server.port`
 * @returns the URL to register as the own entry's `domainRemoteName`
 * @throws when no candidate answers, naming every interface tried
 */
export async function resolveOwnAddress(port) {
  const candidates = privateIPv4Candidates();
  for (const candidate of candidates) {
    if (await canConnect(candidate.address, port)) {
      return `http://${candidate.address}:${port}`;
    }
  }
  const tried = candidates.length === 0
    ? 'none (no non-internal private IPv4 on this host)'
    : candidates.map(c => `${c.address} (${c.name})`).join(', ');
  throw new Error(
    `No private IPv4 of this host accepts a TCP connection on port ${port}. Tried: ${tried}.\n`
    + 'GTNet binds an instance identity to a non-loopback address and probes it from the instance '
    + 'itself, so the host has to hairpin to its own address. Allow the local firewall to accept '
    + `connections to port ${port} from the host's own LAN address, then retry.`);
}

// Allow `node scripts/gtnet-peer-address.mjs 8081` for a quick manual check.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.argv[2]);
  if (!Number.isInteger(port)) {
    console.error('Usage: node scripts/gtnet-peer-address.mjs <port>');
    process.exitCode = 2;
  } else {
    resolveOwnAddress(port).then(url => console.log(url), error => {
      console.error(error.message);
      process.exitCode = 1;
    });
  }
}
