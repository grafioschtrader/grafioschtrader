import { expect, test } from '@playwright/test';
import {
  login,
  PEER_A_FRONTEND,
  PEER_A_OWN,
  PEER_B_FRONTEND,
  PEER_B_OWN,
  readGTNet,
  ensureRemotePeer,
  resetRemotePeer,
  sendMessageFromRow
} from './peer.helpers';

test('an accepted first handshake authorizes both directions without exposing tokens', async ({ browser, page }) => {
  const peerBContext = await browser.newContext({ baseURL: PEER_B_FRONTEND });
  const peerB = await peerBContext.newPage();
  try {
    await login(page, PEER_A_FRONTEND);
    await login(peerB, PEER_B_FRONTEND);
    await resetRemotePeer(page, PEER_B_OWN);
    await resetRemotePeer(peerB, PEER_A_OWN);
    await ensureRemotePeer(page, PEER_A_FRONTEND, PEER_B_OWN);
    await ensureRemotePeer(peerB, PEER_B_FRONTEND, PEER_A_OWN);
    await sendMessageFromRow(page, PEER_A_FRONTEND, PEER_B_OWN!, 'GT_NET_FIRST_HANDSHAKE_SEL_RR_S');
    await expect
      .poll(async () => {
        const remote = (await readGTNet(page)).gtNetList.find((entry: any) => entry.domainRemoteName === PEER_B_OWN);
        return remote.gtNetConfig?.authorizedRemoteEntry;
      })
      .toBe(true);
    const aJson = JSON.stringify(await readGTNet(page));
    const b = await readGTNet(peerB);
    const remoteA = b.gtNetList.find((entry: any) => entry.domainRemoteName === PEER_A_OWN);
    expect(remoteA.gtNetConfig?.authorizedRemoteEntry).toBe(true);
    expect(aJson).not.toMatch(/tokenThis|tokenRemote/);
    expect(JSON.stringify(b)).not.toMatch(/tokenThis|tokenRemote/);
  } finally {
    await peerBContext.close();
  }
});
