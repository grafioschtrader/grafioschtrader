import { expect, test } from '@playwright/test';

import { libHelpers } from './helpers';
import { authHeaders, GTNET_RX, openCrudMenu, openGTNetSetup, readGTNet, acceptConfirmDialog } from './gtnet.helpers';

/**
 * Teardown of the GTNet specs `040`–`065`: removes the peers and the auto-answer rules they created, and leaves
 * the own entry in place.
 *
 * The asymmetry is deliberate and comes from `GTNetJpaRepositoryImpl.deleteGTNet`, which refuses exactly two
 * cases: the row named by `g.gnet.my.entry.id`, and any row that still carries an unanswered request-response
 * message. So a per-spec reset can remove peers but never the identity — the only reset that also clears
 * `g.gnet.my.entry.id` is recreating the database, which is a per-suite operation. Keeping the own entry is
 * also what makes the GTNet specs rerunnable without a database reset: `040` finds it and updates it.
 *
 * There are no unanswered request-response rows to clear at this stage. Sending a message needs a peer that
 * answers, so on a single instance nothing ever reaches `GT_NET_FIRST_HANDSHAKE_SEL_RR_S` and friends; the
 * two-peer suite is where that part of the reset becomes real.
 *
 * The first peer is deleted through the UI, so the confirm dialog and the delete menu entry are covered; any
 * further ones are removed through REST, because repeating the same click adds nothing.
 */

test.describe.serial('GTNet teardown', () => {
  test('deletes the peers created by the GTNet specs and keeps the own entry', async ({ page }) => {
    await libHelpers.loginAsFixtureUser(page, 'admin');

    const before = await readGTNet(page);
    expect(before.gtNetMyEntryId, 'own entry — 040 has to run first').not.toBeNull();
    const remotes = before.gtNetList.filter((gtNet) => gtNet.idGtNet !== before.gtNetMyEntryId);

    if (remotes.length > 0) {
      const container = await openGTNetSetup(page);
      const row = container.locator('tbody tr', { hasText: remotes[0].domainRemoteName }).first();
      await row.waitFor({ state: 'visible', timeout: 10_000 });
      const menu = await openCrudMenu(page, container, row);

      const deletePromise = page.waitForResponse(
        (response) =>
          response.url().endsWith(`/api/gtnet/${remotes[0].idGtNet}`) && response.request().method() === 'DELETE'
      );
      await menu.getByRole('menuitem', { name: GTNET_RX.deleteRecord }).first().click();
      await acceptConfirmDialog(page);
      const deleteResponse = await deletePromise;
      expect(deleteResponse.ok(), `${deleteResponse.status()} ${await deleteResponse.text()}`).toBeTruthy();
    }

    const headers = await authHeaders(page);
    for (const remote of remotes.slice(1)) {
      const response = await page.request.delete(`/api/gtnet/${remote.idGtNet}`, { headers });
      expect(response.ok(), `deleting peer ${remote.domainRemoteName}`).toBeTruthy();
    }

    const after = await readGTNet(page);
    expect(after.gtNetList.map((gtNet) => gtNet.idGtNet)).toEqual([before.gtNetMyEntryId]);
    expect(after.gtNetMyEntryId, 'the own entry survives every per-spec reset').toBe(before.gtNetMyEntryId);
  });

  test('deletes the auto-answer rules', async ({ page }) => {
    await libHelpers.loginAsFixtureUser(page, 'admin');
    const headers = await authHeaders(page);

    const listResponse = await page.request.get('/api/gtnetmessageanswer', { headers });
    expect(listResponse.ok(), `loading auto-answer rules: ${await listResponse.text()}`).toBeTruthy();
    const answers = (await listResponse.json()) as { idGtNetMessageAnswer: number }[];

    for (const answer of answers) {
      const response = await page.request.delete(`/api/gtnetmessageanswer/${answer.idGtNetMessageAnswer}`, { headers });
      expect(response.ok(), `deleting auto-answer rule ${answer.idGtNetMessageAnswer}`).toBeTruthy();
    }

    const remaining = await page.request.get('/api/gtnetmessageanswer', { headers });
    expect((await remaining.json()) as unknown[]).toHaveLength(0);
  });
});
