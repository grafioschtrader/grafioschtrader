import { expect, Locator, Page, test } from '@playwright/test';

import { libHelpers } from './helpers';
import {
  authHeaders,
  GTNET_RX,
  labelledButton,
  openCrudMenu,
  openGTNetSetup,
  ownPeerUrl,
  readGTNet,
  visibleDialog
} from './gtnet.helpers';

/**
 * The GTNet own entry: `gtnet-edit.component` in its `isMyEntry` shape, driven on a single instance.
 *
 * This is the entry that turns a GTNet-enabled instance into an operational one. `grafiosch_t` starts with
 * `g.gnet.use = 1` but `g.gnet.my.entry.id` NULL and every `gt_net*` table empty, so the setup table opens on
 * the red `GT_NET_COMM_REQUIREMENT` hint and this spec is what clears it.
 *
 * Three things about this form are not obvious from looking at it:
 *
 *  - **The address must be a non-loopback literal IPv4 of this host.** `saveOnlyAttributes` writes
 *    `g.gnet.my.entry.id` only when `isDomainNameThisMachine(domainRemoteName)` is true, and that walks the
 *    local interfaces *skipping every loopback one*. On the very first `gt_net` insert it additionally probes
 *    the URL through `BaseDataClient.getActuatorInfo`, so the host has to hairpin to its own address and the
 *    local firewall has to allow it. The runner resolves the address; see {@link ownPeerUrl}.
 *  - **The own entry has no dynamic-form submit button.** `ngOnInit` appends `createSubmitButton()` only when
 *    the entry is *not* the own one; here the save sits below the batch entity table and calls `submitAll()`.
 *  - **The batch table rows come from the host registry.** `GTNetWithMessages.exchangeKindTypes` is filled from
 *    `ExchangeKindTypeRegistry`, which `grafiosch-test-integration` fills with its own
 *    `IntegrationExchangeKindType`. A kind whose `supportsPush()` is false must not offer `AC_PUSH_OPEN`.
 *
 * Login is `admin`, which is tagged `e2e: "i"` in users.json and therefore created by the backend
 * `ResourceTestSuite`, not by `auth.setup.ts`. Run that suite against `grafiosch_t` first — `e2eTest --lib` does.
 *
 * Re-runnable: when an own entry already exists the spec edits it instead of creating one. `domainRemoteName`
 * is `@PropertyOnlyCreation` and disabled on update, so an own entry carrying a *different* address can be
 * repaired through neither the UI nor REST; the spec then fails with that instruction rather than limping on.
 */

const OWN_SETTINGS = {
  dailyRequestLimit: '1500',
  maxLimit: '250'
} as const;

/** Opens the create-or-edit dialog for the own entry from the setup table. */
async function openOwnEntryDialog(page: Page, hasOwnEntry: boolean, ownDomain: string): Promise<Locator> {
  const container = await openGTNetSetup(page);
  if (hasOwnEntry) {
    const row = container.locator('tbody tr', { hasText: ownDomain }).first();
    await row.waitFor({ state: 'visible', timeout: 10_000 });
    const menu = await openCrudMenu(page, container, row);
    await menu.getByRole('menuitem', { name: GTNET_RX.editRecord }).first().click();
  } else {
    // The empty setup table shows the red hint that the own entry has to be created first. With nothing
    // selected the CRUD menu offers exactly one entry, CREATE.
    await expect(container).toContainText(GTNET_RX.commRequirement);
    const menu = await openCrudMenu(page, container);
    await menu.getByRole('menuitem', { name: GTNET_RX.createRecord }).first().click();
  }
  return visibleDialog(page);
}

/**
 * Returns the batch-table row of the exchange kind at `index` of `GTNetWithMessages.exchangeKindTypes`.
 *
 * The rows are `kindTypes.map(...)` over exactly that array, so its position is the row position — the kind's
 * *value* is not, because `ExchangeKindTypeRegistry.getAllKinds()` streams a `ConcurrentHashMap` and promises
 * no order. Within the row the cells carry no id, name or data attribute: `editable-table` renders a bare
 * `<select>` per select column and a bare `<input type="number">` for the numeric one, so they are addressed
 * by position too.
 */
function kindRow(dialog: Locator, index: number): Locator {
  return dialog.locator('editable-table tbody tr').nth(index);
}

test.describe.serial('GTNet own entry', () => {
  test('creates or updates the own entry and configures every exchange kind', async ({ page }) => {
    const ownDomain = ownPeerUrl();
    await libHelpers.loginAsFixtureUser(page, 'admin');

    const before = await readGTNet(page);
    expect(
      before.exchangeKindTypes.length,
      'the host registered no exchange kind — GTNetWithMessages.exchangeKindTypes is empty, so the batch entity ' +
        'table cannot render. IntegrationExchangeKindConfig must run.'
    ).toBeGreaterThan(0);

    const existingOwn = before.gtNetList.find((gtNet) => gtNet.idGtNet === before.gtNetMyEntryId);
    if (existingOwn && existingOwn.domainRemoteName !== ownDomain) {
      throw new Error(
        `The own GTNet entry is registered as '${existingOwn.domainRemoteName}' but this host now resolves to ` +
          `'${ownDomain}'. domainRemoteName is @PropertyOnlyCreation and disabled on update, so it can be ` +
          'repaired through neither the UI nor REST. Drop and recreate grafiosch_t, restart the backend on the ' +
          'e2e profile, clear frontend/e2e/lib/.auth/, then rerun.'
      );
    }

    const dialog = await openOwnEntryDialog(page, !!existingOwn, ownDomain);

    // Nothing may be typed before the dialog has finished initialising. `initialize()` subscribes to
    // `gps.getTimezones()` and only inside that callback calls `transferBusinessObjectToForm(gtNet)` — with a
    // fresh `new GTNet()` on create. That call resets every control, so a value filled while the request is
    // still in flight is silently wiped and the save fails with "domain.remote.name must not be null".
    // The populated timeZone select is the observable signal that the callback has run.
    const timeZone = dialog.locator('select#timeZone');
    await expect.poll(async () => timeZone.locator('option').count(), { timeout: 15_000 }).toBeGreaterThan(1);

    const domainField = dialog.locator('#domainRemoteName');
    if (existingOwn) {
      // @PropertyOnlyCreation: the address is fixed once the entry exists, and the dialog reflects that.
      await expect(domainField).toBeDisabled();
      await expect(domainField).toHaveValue(ownDomain);
    } else {
      await domainField.fill(ownDomain);
    }

    const timeZoneValue = await timeZone.evaluate((select) => {
      const options = Array.from((select as HTMLSelectElement).options).filter((option) => option.value);
      return options.find((option) => option.value === 'UTC')?.value ?? options[0].value;
    });
    await timeZone.selectOption(timeZoneValue);

    await dialog.locator('#spreadCapability').setChecked(true);
    await dialog.locator('#dailyRequestLimit').fill(OWN_SETTINGS.dailyRequestLimit);
    await dialog.locator('#serverBusy').setChecked(false);
    await dialog.locator('#allowServerCreation').setChecked(true);
    // serverOnline is disabled on update; saveOnlyAttributes forces SOS_ONLINE for the own entry anyway.
    const serverOnline = dialog.locator('select#serverOnline');
    if (await serverOnline.isEnabled()) {
      await serverOnline.selectOption('SOS_ONLINE');
    }

    for (const [index, kind] of before.exchangeKindTypes.entries()) {
      const row = kindRow(dialog, index);
      const acceptRequest = row.locator('select').nth(0);
      const offered = await acceptRequest
        .locator('option')
        .evaluateAll((options) => options.map((option) => (option as HTMLOptionElement).value));
      if (kind.supportsPush) {
        expect(offered, `AC_PUSH_OPEN offered for push-capable kind ${kind.name}`).toContain('AC_PUSH_OPEN');
      } else {
        // getAcceptRequestOptions filters the option out, and validateEntityPushSupport would reject it server
        // side with gt.gtnet.entity.push.not.supported. Asserting the option set catches a regression in either.
        expect(offered, `AC_PUSH_OPEN must not be offered for ${kind.name} (supportsPush() is false)`).not.toContain(
          'AC_PUSH_OPEN'
        );
      }
      await acceptRequest.selectOption('AC_OPEN');
      await row.locator('select').nth(1).selectOption('SS_OPEN');
      await row.locator('input[type="number"]').fill(OWN_SETTINGS.maxLimit);
    }

    // Create posts, update puts: the shared updateEntity() branches on whether the entity carries an id.
    const savePromise = page.waitForResponse(
      (response) => response.url().endsWith('/api/gtnet') && ['POST', 'PUT'].includes(response.request().method())
    );
    // The own entry saves through the p-button below the batch table, not through a dynamic-form submit
    // button - ngOnInit only appends one when the entry is not the own one. Its pi-check icon puts a glyph
    // into the accessible name, so it is matched by label; see labelledButton.
    await labelledButton(dialog, GTNET_RX.save).click();
    const saveResponse = await savePromise;
    expect(saveResponse.ok(), `${saveResponse.status()} ${await saveResponse.text()}`).toBeTruthy();
    await dialog.waitFor({ state: 'hidden', timeout: 15_000 });

    const after = await readGTNet(page);
    expect(
      after.gtNetMyEntryId,
      'g.gnet.my.entry.id is still NULL — saveOnlyAttributes only writes it when isDomainNameThisMachine() ' +
        `matches, and it skips loopback interfaces. Registered address: ${ownDomain}`
    ).not.toBeNull();

    const own = after.gtNetList.find((gtNet) => gtNet.idGtNet === after.gtNetMyEntryId);
    expect(own?.domainRemoteName).toBe(ownDomain);
    expect(own?.dailyRequestLimit).toBe(Number(OWN_SETTINGS.dailyRequestLimit));
    expect(own?.spreadCapability).toBe(true);
    expect(own?.allowServerCreation).toBe(true);
    expect(own?.serverBusy).toBe(false);
    expect(own?.gtNetEntities.map((entity) => entity.entityKind).sort()).toEqual(
      after.exchangeKindTypes.map((kind) => kind.value).sort()
    );
    for (const entity of own!.gtNetEntities) {
      expect(entity.maxLimit).toBe(Number(OWN_SETTINGS.maxLimit));
    }
  });

  test('the own entry cannot be deleted', async ({ page }) => {
    await libHelpers.loginAsFixtureUser(page, 'admin');
    const data = await readGTNet(page);
    expect(data.gtNetMyEntryId, 'own entry created by the previous test').not.toBeNull();

    // deleteGTNet refuses this with gt.gtnet.cannot.delete.own.entry. Asserted at REST level because the setup
    // table already disables the entry: hasRightsForDeleteEntity() returns false for the own row.
    const response = await page.request.delete(`/api/gtnet/${data.gtNetMyEntryId}`, {
      headers: await authHeaders(page),
      failOnStatusCode: false
    });
    expect(response.ok(), 'deleting the own GTNet entry must be refused').toBeFalsy();
  });
});
