import {test, expect} from '@playwright/test';
import {findMailhogMessage, getUser, loginAs, loginAsFixtureUser} from './helpers';
import {
  attemptCreatePortfolioExpectRejected,
  clickClientMenuItem,
  closeDialog,
  closeMenus,
  expectNoClientMenu,
  expectToast,
  fillInput,
  openClientMenu,
  openManageClientDialog,
  RX,
  tenantRootNode,
} from './manage-client.helpers';

/**
 * E2E coverage for issue #201 — share read access to my own portfolio, in both flavours:
 *
 * 1. Unknown recipient e-mail → a pure read-only viewer login is created and its credentials are
 *    e-mailed. The viewer is the users.json row flagged 'er' (e2euser.r@test.local) — NOT registered
 *    by auth.setup.ts, created here by the share feature and intentionally never revoked.
 * 2. Already-registered recipient → a READ tenant_access grant. The grantee is the regular e2e
 *    user 'e2euser', who can then switch into the shared portfolio (read-only) and back.
 *
 * Owner = 'alledit' (hugo.graf@grafiosch.com, de-CH → German UI); the created viewer inherits that
 * locale (German e-mail/UI) while 'e2euser' runs English — all selectors match both languages.
 * Everything is idempotent for reruns against the same database.
 */

const OWNER_NICKNAME = 'alledit';
const VIEWER_NICKNAME = 'e2euserr';
const GRANTEE_NICKNAME = 'e2euser';

test.describe.serial('Share read access (issue #201)', () => {

  const owner = getUser(OWNER_NICKNAME);
  const viewer = getUser(VIEWER_NICKNAME);
  const grantee = getUser(GRANTEE_NICKNAME);

  /** Root-node label of the owner's tenant, captured in test 1 and asserted in tests 2/3. */
  let ownerRootText: string;

  test('owner shares read access: new viewer login and READ grant for a registered user', async ({page}) => {
    test.setTimeout(120_000);
    await loginAsFixtureUser(page, OWNER_NICKNAME);
    ownerRootText = (await tenantRootNode(page).innerText()).trim();

    // Idempotency: skip each flavour whose share already exists from a previous run.
    let viewers = await openManageClientDialog(page, RX.sharedViewersItem, RX.sharedViewersHeader);
    const viewerExists = await viewers.locator('tbody tr', {hasText: viewer.email}).count() > 0;
    const granteeExists = await viewers.locator('tbody tr', {hasText: grantee.email}).count() > 0;
    await closeDialog(page, viewers);

    if (!viewerExists) {
      // Flavour 1: unknown e-mail → status NEW → password fields appear → viewer login created.
      const shareDialog =
        await openManageClientDialog(page, RX.shareReadAccessItem, RX.shareDialogHeader);
      await fillInput(shareDialog, '#email', viewer.email);
      await shareDialog.getByRole('button', {name: RX.checkExistenceBtn}).click();
      await shareDialog.locator('#password').waitFor({state: 'visible', timeout: 10_000});
      await fillInput(shareDialog, '#password', viewer.password);
      await fillInput(shareDialog, '#passwordConfirm', viewer.password);
      await shareDialog.locator('button[type="submit"]').click();
      await expectToast(page, RX.sharedToast);

      // The invitation e-mail (sent asynchronously) carries the credentials as entered.
      const mail = await findMailhogMessage(viewer.email, RX.shareInvitedMailSubject);
      expect(mail).not.toBeNull();
      expect(mail!.body).toContain(viewer.email);
      expect(mail!.body).toContain(viewer.password);
    }

    if (!granteeExists) {
      // Flavour 2: registered e-mail → status EXISTS → no password needed, grant only, no e-mail.
      const shareDialog =
        await openManageClientDialog(page, RX.shareReadAccessItem, RX.shareDialogHeader);
      await fillInput(shareDialog, '#email', grantee.email);
      await shareDialog.getByRole('button', {name: RX.checkExistenceBtn}).click();
      await expect(shareDialog.locator('button[type="submit"]')).toBeEnabled({timeout: 10_000});
      await expect(shareDialog.locator('#password')).toBeHidden();
      await shareDialog.locator('button[type="submit"]').click();
      await expectToast(page, RX.sharedToast);
    }

    // Both shares are listed with their access kind.
    viewers = await openManageClientDialog(page, RX.sharedViewersItem, RX.sharedViewersHeader);
    const viewerRow = viewers.locator('tbody tr', {hasText: viewer.email});
    await expect(viewerRow).toHaveCount(1);
    await expect(viewerRow).toContainText(RX.viewerKind);
    const granteeRow = viewers.locator('tbody tr', {hasText: grantee.email});
    await expect(granteeRow).toHaveCount(1);
    await expect(granteeRow).toContainText(RX.grantKind);
    await closeDialog(page, viewers);
  });

  test('viewer login sees the owner portfolio read-only', async ({page}) => {
    test.setTimeout(120_000);
    await loginAsFixtureUser(page, VIEWER_NICKNAME);

    // The viewer's home tenant IS the owner's tenant — the same tree root as the owner sees.
    expect(ownerRootText).toBeTruthy();
    await expect(tenantRootNode(page)).toContainText(ownerRootText, {timeout: 15_000});

    // Read-only + not inside a managed client → the Client menu is suppressed entirely.
    await expectNoClientMenu(page);

    // Hard write-block by the backend (TenantReadOnlyFilter).
    await attemptCreatePortfolioExpectRejected(page, 'Viewer Blocked');
  });

  test('registered grantee switches into the shared portfolio read-only and back', async ({page}) => {
    test.setTimeout(120_000);
    const creds = await loginAs(page, GRANTEE_NICKNAME);
    const ownRootText = (await tenantRootNode(page).innerText()).trim();
    expect(creds.tenantName).toBeTruthy();

    // The shared tenant is listed in "My clients", labelled with the owner's e-mail. A READ row
    // offers no delete action, only the switch.
    const myClients = await openManageClientDialog(page, RX.switchToClientItem, RX.myClientsHeader);
    const row = myClients.locator('tbody tr', {hasText: owner.email});
    await expect(row).toHaveCount(1);
    await expect(row.locator('button:has(i.fa-trash)')).toHaveCount(0);
    await row.locator('button:has(i.fa-sign-in)').click();

    // Full reload into the owner's tenant.
    await expect(tenantRootNode(page)).toContainText(ownerRootText, {timeout: 30_000});

    // Read-only inside the shared tenant: the Client menu is reduced to "Back to my".
    await openClientMenu(page);
    const menubar = page.locator('p-menubar');
    await expect(menubar.getByText(RX.backToMyItem).first()).toBeVisible({timeout: 5_000});
    await expect(menubar.getByText(RX.shareReadAccessItem)).toHaveCount(0);
    await expect(menubar.getByText(RX.switchToClientItem)).toHaveCount(0);
    await closeMenus(page);

    // CRUD entries are omitted from table context menus (TableCrudSupportMenu) in read-only mode.
    await tenantRootNode(page).click();
    await page.waitForTimeout(2_000);
    const container = page.locator('.data-container').first();
    if (await container.isVisible().catch(() => false)) {
      await container.click();
      await page.waitForTimeout(300);
      await container.click({button: 'right'});
      const menu = page.locator('[role="menu"]:visible');
      if (await menu.isVisible().catch(() => false)) {
        await expect(menu.getByText(/(Create|Erstellen|Delete|L.schen)/i)).toHaveCount(0);
        await page.keyboard.press('Escape');
      }
    }

    // The backend write-block applies to the grantee as well.
    await attemptCreatePortfolioExpectRejected(page, 'Grantee Blocked');

    // Back to the grantee's own tenant — writable again (share item available once more).
    await clickClientMenuItem(page, RX.backToMyItem);
    await expect(tenantRootNode(page)).toContainText(ownRootText, {timeout: 30_000});
    await openClientMenu(page);
    await expect(page.locator('p-menubar').getByText(RX.shareReadAccessItem).first())
      .toBeVisible({timeout: 5_000});
    await closeMenus(page);
  });
});
