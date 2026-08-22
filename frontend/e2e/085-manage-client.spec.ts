import { test, expect } from '@playwright/test';
import { findMailhogMessage, getUser, loginAsFixtureUser } from './helpers';
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
  tenantRootNode
} from './manage-client.helpers';

/**
 * E2E coverage for issue #200 — multi-tenant access: an advisor manages a client portfolio and the
 * client gets a read-only login.
 *
 * Advisor = users.json nickname 'alledit' (hugo.graf@grafiosch.com, ROLE_ALLEDIT, de-CH → German
 * UI). The managed client is the users.json row flagged 'ec' (e2euser.c@test.local); that user is
 * NOT registered by auth.setup.ts — it is created here through the Create-client dialog and is
 * intentionally never deleted, so every step is idempotent for reruns against the same database.
 * The created client inherits the advisor's locale, hence the credential e-mail and the client's
 * own UI are German; all selectors match both languages.
 */

const ADVISOR_NICKNAME = 'alledit';
const CLIENT_NICKNAME = 'e2euserc';
/** Tenant name/nickname the backend derives from the client e-mail's local part. */
const CLIENT_TENANT_NAME = 'e2euser.c';
const CLIENT_PORTFOLIO = 'Client Portfolio';

test.describe.serial('Manage clients (issue #200)', () => {
  const client = getUser(CLIENT_NICKNAME);

  test('advisor creates the managed client; credentials are e-mailed', async ({ page }) => {
    test.setTimeout(120_000);
    await loginAsFixtureUser(page, ADVISOR_NICKNAME);

    // Idempotency: skip creation when a previous run already created the client.
    let myClients = await openManageClientDialog(page, RX.switchToClientItem, RX.myClientsHeader);
    const clientExists = (await myClients.locator('tbody tr', { hasText: client.email }).count()) > 0;
    await closeDialog(page, myClients);

    if (!clientExists) {
      const createDialog = await openManageClientDialog(page, RX.createClientItem, RX.createClientItem);
      await fillInput(createDialog, '#email', client.email);
      await fillInput(createDialog, '#password', client.password);
      await fillInput(createDialog, '#passwordConfirm', client.password);
      await createDialog.locator('button[type="submit"]').click();
      await expectToast(page, RX.clientCreatedToast);

      // The credentials e-mail is sent asynchronously — poll MailHog. Only asserted in the
      // creation branch: on reruns MailHog was cleared by auth.setup and no new mail is sent.
      const mail = await findMailhogMessage(client.email, RX.clientCreatedMailSubject);
      expect(mail).not.toBeNull();
      expect(mail!.body).toContain(client.email);
      expect(mail!.body).toContain(client.password);
    }

    // The client shows up in "My clients" with the tenant name derived from the e-mail local part.
    myClients = await openManageClientDialog(page, RX.switchToClientItem, RX.myClientsHeader);
    const row = myClients.locator('tbody tr', { hasText: client.email });
    await expect(row).toHaveCount(1);
    await expect(row).toContainText(CLIENT_TENANT_NAME);
    await closeDialog(page, myClients);
  });

  test('advisor switches into the client tenant with full CRUD and back', async ({ page }) => {
    test.setTimeout(120_000);
    await loginAsFixtureUser(page, ADVISOR_NICKNAME);
    const homeRootText = (await tenantRootNode(page).innerText()).trim();

    const myClients = await openManageClientDialog(page, RX.switchToClientItem, RX.myClientsHeader);
    const row = myClients.locator('tbody tr', { hasText: client.email });
    await expect(row).toHaveCount(1);
    // The advisor holds a MANAGE grant → the row offers a delete action (not exercised: the
    // client must survive this test) and the switch action.
    await expect(row.locator('button:has(i.fa-trash)')).toHaveCount(1);
    await row.locator('button:has(i.fa-sign-in)').click();

    // switchAndReload: new JWT + navigation to /mainview + full page reload into the client tenant.
    await expect(tenantRootNode(page)).toContainText(CLIENT_TENANT_NAME, { timeout: 30_000 });

    // Inside a managed client the Client menu offers the way back.
    await openClientMenu(page);
    await expect(page.locator('p-menubar').getByText(RX.backToMyItem).first()).toBeVisible({ timeout: 5_000 });
    await closeMenus(page);

    // MANAGE access is full CRUD: create a portfolio in the client tenant (idempotent).
    if ((await page.locator('.p-tree-node-content', { hasText: CLIENT_PORTFOLIO }).count()) === 0) {
      await tenantRootNode(page).click({ button: 'right' });
      const menuItem = page.locator('p-contextmenu').getByText(RX.createPortfolioItem).first();
      await menuItem.waitFor({ state: 'visible', timeout: 5_000 });
      await menuItem.click();
      const pfDialog = page.locator('.p-dialog').first();
      await pfDialog.waitFor({ state: 'visible', timeout: 10_000 });
      await fillInput(pfDialog, '#name', CLIENT_PORTFOLIO);
      // Currency defaults to the tenant currency (advisor's CHF) — no change needed.
      await pfDialog.locator('button[type="submit"]').click();
      await pfDialog.waitFor({ state: 'hidden', timeout: 10_000 });
    }
    await expect(page.locator('.p-tree-node-content', { hasText: CLIENT_PORTFOLIO })).toBeVisible({ timeout: 10_000 });

    // Back to the advisor's own tenant.
    await clickClientMenuItem(page, RX.backToMyItem);
    await expect(tenantRootNode(page)).toContainText(homeRootText, { timeout: 30_000 });
  });

  test('client login is read-only: data visible, no Client menu, writes rejected', async ({ page }) => {
    test.setTimeout(120_000);
    await loginAsFixtureUser(page, CLIENT_NICKNAME);

    // The client sees the data the advisor maintains for them.
    await expect(page.locator('.p-tree-node-content', { hasText: CLIENT_PORTFOLIO })).toBeVisible({ timeout: 15_000 });

    // Read-only + not inside a managed client → the Client menu is suppressed entirely.
    await expectNoClientMenu(page);

    // The tree menu is not read-only gated, so the write reaches the backend and must be
    // rejected there (TenantReadOnlyFilter).
    await attemptCreatePortfolioExpectRejected(page, 'Read Only Blocked');
  });
});
