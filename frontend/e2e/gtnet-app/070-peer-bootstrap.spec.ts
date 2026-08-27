import { expect, Page, test } from '@playwright/test';
import {
  authHeaders,
  ensureOwnPeer,
  ensureRemotePeer,
  login,
  PEER_A_FRONTEND,
  PEER_A_OWN,
  PEER_B_FRONTEND,
  PEER_B_OWN,
  readGTNet,
  sendMessageFromRow,
  submitMessage
} from './peer.helpers';

const ACCEPT_RULE_PRIORITY = 41;
const SYNCABLE_KINDS = 'LAST_PRICE,HISTORICAL_PRICES';

/**
 * Establishes the topology the payload suite assumes: both application peers have their own entry, know each other,
 * have completed the handshake so a GTNetConfig with tokens exists on both sides, and have an accepted data exchange
 * for both syncable kinds. Everything is created the way a user creates it, so no bootstrap component bypasses the
 * code under test.
 *
 * The data request is part of the bootstrap because a handshake alone no longer entitles a peer to anything: serving
 * last prices or historical quotes requires an accepted, unrevoked grant for that peer and that kind. Without it the
 * payload suite that runs next would be refused with NO_GRANT on every exchange code.
 */
test('both application peers know each other and complete the handshake', async ({ browser, page }) => {
  const peerBContext = await browser.newContext({ baseURL: PEER_B_FRONTEND });
  const peerB = await peerBContext.newPage();
  try {
    await login(page, PEER_A_FRONTEND);
    await login(peerB, PEER_B_FRONTEND);

    await ensureOwnPeer(page, PEER_A_FRONTEND, PEER_A_OWN);
    await ensureOwnPeer(peerB, PEER_B_FRONTEND, PEER_B_OWN);
    await ensureRemotePeer(page, PEER_A_FRONTEND, PEER_B_OWN);
    await ensureRemotePeer(peerB, PEER_B_FRONTEND, PEER_A_OWN);

    await sendMessageFromRow(page, PEER_A_FRONTEND, PEER_B_OWN!, 'GT_NET_FIRST_HANDSHAKE_SEL_RR_S');

    await expect
      .poll(async () => {
        const remote = (await readGTNet(page)).gtNetList.find((entry: any) => entry.domainRemoteName === PEER_B_OWN);
        return remote?.gtNetConfig?.authorizedRemoteEntry;
      })
      .toBe(true);
    const stateB = await readGTNet(peerB);
    const remoteA = stateB.gtNetList.find((entry: any) => entry.domainRemoteName === PEER_A_OWN);
    expect(remoteA?.gtNetConfig?.authorizedRemoteEntry).toBe(true);
    expect(JSON.stringify(stateB)).not.toMatch(/tokenThis|tokenRemote/);

    await installAcceptRule(peerB);
    try {
      await submitMessage(page, PEER_B_OWN!, 'GT_NET_DATA_REQUEST_SEL_RR_C', { entityKinds: SYNCABLE_KINDS });
      await expect.poll(() => grantedKinds(peerB, PEER_A_OWN!)).toEqual(['HISTORICAL_PRICES', 'LAST_PRICE']);
      await expect.poll(() => grantedKinds(page, PEER_B_OWN!)).toEqual(['HISTORICAL_PRICES', 'LAST_PRICE']);
    } finally {
      await removeAcceptRule(peerB);
    }
  } finally {
    await peerBContext.close();
  }
});

/** The exchange kinds a peer has an accepted, unrevoked grant for, sorted so the assertion is order-independent. */
async function grantedKinds(page: Page, domain: string): Promise<string[]> {
  const remote = (await readGTNet(page)).gtNetList.find((entry: any) => entry.domainRemoteName === domain);
  return (remote?.gtNetEntities ?? [])
    .filter((entity: any) => entity.gtNetConfigEntity?.exchange)
    .map((entity: any) => entity.entityKind)
    .sort();
}

/** An auto-answer rule turns the data request into an approval without an administrator sitting in front of it. */
async function installAcceptRule(peerB: Page): Promise<void> {
  await removeAcceptRule(peerB);
  const response = await peerB.request.post('/api/gtnetmessageanswer', {
    headers: await authHeaders(peerB),
    data: {
      requestMsgCode: 'GT_NET_DATA_REQUEST_SEL_RR_C',
      responseMsgCode: 'GT_NET_DATA_REQUEST_ACCEPT_S',
      priority: ACCEPT_RULE_PRIORITY,
      waitDaysApply: 0
    }
  });
  expect(response.ok(), await response.text()).toBeTruthy();
}

async function removeAcceptRule(peerB: Page): Promise<void> {
  const headers = await authHeaders(peerB);
  const response = await peerB.request.get('/api/gtnetmessageanswer', { headers });
  if (!response.ok()) {
    return;
  }
  for (const rule of (await response.json()) as any[]) {
    if (rule.priority === ACCEPT_RULE_PRIORITY) {
      await peerB.request.delete(`/api/gtnetmessageanswer/${rule.idGtNetMessageAnswer}`, { headers });
    }
  }
}
