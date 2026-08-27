import { expect, test } from '@playwright/test';
import {
  ensureOwnPeer,
  ensureRemotePeer,
  login,
  PEER_A_FRONTEND,
  PEER_A_OWN,
  PEER_B_FRONTEND,
  PEER_B_OWN,
  readGTNet
} from './peer.helpers';

test('bootstraps two independent peers and reconciles their reciprocal entries', async ({ browser, page }) => {
  const peerBContext = await browser.newContext({ baseURL: PEER_B_FRONTEND });
  const peerB = await peerBContext.newPage();
  try {
    await login(page, PEER_A_FRONTEND);
    await login(peerB, PEER_B_FRONTEND);
    await ensureOwnPeer(page, PEER_A_FRONTEND, PEER_A_OWN);
    await ensureOwnPeer(peerB, PEER_B_FRONTEND, PEER_B_OWN);
    await ensureRemotePeer(page, PEER_A_FRONTEND, PEER_B_OWN);
    await ensureRemotePeer(peerB, PEER_B_FRONTEND, PEER_A_OWN);
    expect((await readGTNet(page)).gtNetList).toHaveLength(2);
    expect((await readGTNet(peerB)).gtNetList).toHaveLength(2);
  } finally {
    await peerBContext.close();
  }
});
