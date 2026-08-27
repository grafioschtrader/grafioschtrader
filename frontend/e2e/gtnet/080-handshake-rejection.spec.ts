import { expect, test } from '@playwright/test';
import {
  authHeaders,
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

test('a peer can reject the first handshake and the connection remains unauthorized', async ({ browser, page }) => {
  const peerBContext = await browser.newContext({ baseURL: PEER_B_FRONTEND });
  const peerB = await peerBContext.newPage();
  await login(page, PEER_A_FRONTEND);
  await login(peerB, PEER_B_FRONTEND);
  await resetRemotePeer(page, PEER_B_OWN);
  await resetRemotePeer(peerB, PEER_A_OWN);
  await ensureRemotePeer(page, PEER_A_FRONTEND, PEER_B_OWN);
  await ensureRemotePeer(peerB, PEER_B_FRONTEND, PEER_A_OWN);
  const rule = await peerB.request.post('/api/gtnetmessageanswer', {
    headers: await authHeaders(peerB),
    data: {
      requestMsgCode: 'GT_NET_FIRST_HANDSHAKE_SEL_RR_S',
      responseMsgCode: 'GT_NET_FIRST_HANDSHAKE_REJECT_S',
      priority: 1,
      waitDaysApply: 0,
      responseMsgMessage: 'GTNet two-peer rejection proof'
    }
  });
  expect(rule.ok(), await rule.text()).toBeTruthy();
  try {
    await sendMessageFromRow(page, PEER_A_FRONTEND, PEER_B_OWN!, 'GT_NET_FIRST_HANDSHAKE_SEL_RR_S');
    const remote = (await readGTNet(page)).gtNetList.find((entry: any) => entry.domainRemoteName === PEER_B_OWN);
    expect(remote.gtNetConfig?.authorizedRemoteEntry ?? false).toBe(false);
  } finally {
    const created = await rule.json();
    await peerB.request.delete(`/api/gtnetmessageanswer/${created.idGtNetMessageAnswer}`, {
      headers: await authHeaders(peerB)
    });
    await peerBContext.close();
  }
});
