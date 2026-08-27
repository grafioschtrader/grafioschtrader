import { expect, test } from '@playwright/test';

import { libHelpers } from './helpers';
import { deleteRemoteGTNets, GTNET_RX, openCrudMenu, openGTNetSetup, readGTNet, visibleDialog } from './gtnet.helpers';

/**
 * A remote GTNet peer entry: `gtnet-edit.component` in its plain shape, and the rights the setup table applies
 * to a row that is not the own entry.
 *
 * The remote dialog is the same component as `040` drives, reduced to one control. `editOwnInstance()` returns
 * an empty field list unless `callParam.isMyEntry`, so everything except `domainRemoteName` disappears — the
 * remaining values are set by the remote instance through `GTNetMessage`, not by us. In exchange the ordinary
 * dynamic-form submit button reappears, because `ngOnInit` only omits it for the own entry.
 *
 * Two things this spec depends on:
 *
 *  - **The reachability probe does not run here.** `saveOnlyAttributes` calls `BaseDataClient.getActuatorInfo`
 *    only while `gtNetJpaRepository.count() == 0`, i.e. for the very first row, which `040` created. A remote
 *    entry may therefore point at an address nothing answers on, and this spec uses `192.0.2.10` — RFC 5737
 *    TEST-NET-1, which is guaranteed not to be a local interface, so `isDomainNameThisMachine` cannot mistake
 *    it for this host and steal `g.gnet.my.entry.id`.
 *  - **`040` must have run**, both for the own entry and because the probe would otherwise fire against
 *    TEST-NET-1 and fail.
 *
 * Re-runnable: every remote entry is removed before the peer is created. The own entry is never touched.
 */

/** RFC 5737 TEST-NET-1: reserved for documentation, never routed, never a local interface. */
const REMOTE_DOMAIN = 'http://192.0.2.10:8082';

test.describe.serial('GTNet remote entry', () => {
  test('creates a remote peer entry', async ({ page }) => {
    await libHelpers.loginAsFixtureUser(page, 'admin');
    await deleteRemoteGTNets(page);

    const before = await readGTNet(page);
    expect(
      before.gtNetMyEntryId,
      'no own GTNet entry — 040-gtnet-own-entry has to run first, otherwise the reachability probe of the first ' +
        'insert fires against the unroutable remote address'
    ).not.toBeNull();

    const container = await openGTNetSetup(page);
    const menu = await openCrudMenu(page, container);
    await menu.getByRole('menuitem', { name: GTNET_RX.createRecord }).first().click();

    const dialog = await visibleDialog(page);
    await dialog.locator('#domainRemoteName').fill(REMOTE_DOMAIN);
    // Everything else belongs to the own entry only, so the remote dialog carries no other control.
    await expect(dialog.locator('select#timeZone')).toHaveCount(0);
    await expect(dialog.locator('editable-table')).toHaveCount(0);

    // A new entry is POSTed; updateEntity() only uses PUT once the entity has an id.
    const savePromise = page.waitForResponse(
      (response) => response.url().endsWith('/api/gtnet') && ['POST', 'PUT'].includes(response.request().method())
    );
    await dialog.locator('button[type="submit"]').click();
    const saveResponse = await savePromise;
    expect(saveResponse.ok(), `${saveResponse.status()} ${await saveResponse.text()}`).toBeTruthy();
    await dialog.waitFor({ state: 'hidden', timeout: 15_000 });

    const after = await readGTNet(page);
    const remote = after.gtNetList.find((gtNet) => gtNet.domainRemoteName === REMOTE_DOMAIN);
    expect(remote, `remote peer ${REMOTE_DOMAIN} in the setup table`).toBeTruthy();
    expect(
      after.gtNetMyEntryId,
      'the own entry must still be the one 040 created — a remote address may never claim the identity'
    ).toBe(before.gtNetMyEntryId);
  });

  test('a remote row offers no edit, and the own row no delete', async ({ page }) => {
    await libHelpers.loginAsFixtureUser(page, 'admin');
    const data = await readGTNet(page);
    const remote = data.gtNetList.find((gtNet) => gtNet.domainRemoteName === REMOTE_DOMAIN);
    const own = data.gtNetList.find((gtNet) => gtNet.idGtNet === data.gtNetMyEntryId);
    expect(remote, 'the remote peer created by the previous test').toBeTruthy();
    expect(own, 'the own entry created by 040').toBeTruthy();

    // hasRightsForUpdateEntity() is true for the own entry alone and hasRightsForDeleteEntity() for everything
    // except it, so the two rows are exact mirror images of each other in the CRUD menu.
    const container = await openGTNetSetup(page);

    const remoteRow = container.locator('tbody tr', { hasText: REMOTE_DOMAIN }).first();
    await remoteRow.waitFor({ state: 'visible', timeout: 10_000 });
    const remoteMenu = await openCrudMenu(page, container, remoteRow);
    await expect(remoteMenu.getByRole('menuitem', { name: GTNET_RX.editRecord }).first()).toHaveAttribute(
      'aria-disabled',
      'true'
    );
    await expect(remoteMenu.getByRole('menuitem', { name: GTNET_RX.deleteRecord }).first()).not.toHaveAttribute(
      'aria-disabled',
      'true'
    );
    await page.keyboard.press('Escape');

    const ownRow = container.locator('tbody tr', { hasText: own!.domainRemoteName }).first();
    const ownMenu = await openCrudMenu(page, container, ownRow);
    await expect(ownMenu.getByRole('menuitem', { name: GTNET_RX.editRecord }).first()).not.toHaveAttribute(
      'aria-disabled',
      'true'
    );
    await expect(ownMenu.getByRole('menuitem', { name: GTNET_RX.deleteRecord }).first()).toHaveAttribute(
      'aria-disabled',
      'true'
    );
    await page.keyboard.press('Escape');

    // The peer stays: 065 needs a two-row table to make the export round trip meaningful, and 070 removes it.
    const after = await readGTNet(page);
    expect(after.gtNetList).toHaveLength(data.gtNetList.length);
  });
});
