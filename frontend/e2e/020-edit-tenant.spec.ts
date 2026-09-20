import { expect, Locator, Page, test } from '@playwright/test';

import { selectDynamicFormOptionByText } from './dynamic-form.helpers';
import { loadUsers, loginAsFixtureUser, UserFixture } from './helpers';

/** Target values for Grafioschtrader's application-specific tenant edit dialog. */
interface TenantEditTarget {
  tenantName: string;
  excludeDivTax: boolean;
  /** English natural key from the fixture; the de-CH UI renders this country as "Schweiz". */
  country: string;
  /** Opt-in to the Grafioschtrader import templates; the platform itself is configured instance wide. */
  useGtImportTemplates: boolean;
}

interface TenantEditUserFixture extends UserFixture {
  tenantEdit?: TenantEditTarget;
}

interface SavedTenant {
  tenantName: string;
  excludeDivTax: boolean;
  country: string;
  useGtImportTemplates: boolean;
}

const COUNTRY_CODE_SWITZERLAND = 'CH';
const EDIT_TENANT_RX = /^(Edit Client|Bearbeiten Klient|EDIT_RECORD\|TENANT)(\.\.\.)?$/i;

const tenantEditUsers = (loadUsers() as TenantEditUserFixture[])
  // Partial tenantEdit blocks belong to the backend integration suite. This UI workflow needs all
  // four dialog values and therefore consumes only complete targets.
  .filter(
    (user) =>
      user.tenantEdit != null &&
      typeof user.tenantEdit.tenantName === 'string' &&
      typeof user.tenantEdit.excludeDivTax === 'boolean'
  );

if (tenantEditUsers.length === 0) {
  throw new Error('No tenantEdit target found in the Grafioschtrader users.json fixture');
}

/** Opens the tenant edit dialog from the root node of the main portfolio tree. */
async function openTenantEditDialog(page: Page): Promise<Locator> {
  // Dashboard is also a root tree node and currently renders before the tenant node.
  // The tenant root is labelled with the translated PORTFOLIOS key followed by the tenant name.
  const tenantRoot = page.getByRole('treeitem', { name: /^Portfolios-/ }).first();
  await tenantRoot.waitFor({ state: 'visible', timeout: 15_000 });
  await tenantRoot.click({ button: 'right' });

  const editItem = page.locator('p-contextmenu').getByText(EDIT_TENANT_RX).first();
  await editItem.waitFor({ state: 'visible', timeout: 5_000 });
  await editItem.click();

  const dialog = page.locator('.p-dialog:visible');
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  // Currencies and countries load together; currency stays a native select, so its options signal that load.
  await expect(dialog.locator('select#currency option')).not.toHaveCount(0, { timeout: 10_000 });
  await expect(dialog.locator('p-select#country')).toBeVisible({ timeout: 10_000 });
  // The opt-in is only rendered once an administrator has chosen the import platform of this instance, which the
  // backend integration suite does before these specs run.
  await dialog.locator('input#useGtImportTemplates').waitFor({ state: 'visible', timeout: 10_000 });
  return dialog;
}

/** Fills a dynamic-form text control and commits its value to the Angular form. */
async function fillText(dialog: Locator, fieldId: string, value: string): Promise<void> {
  const input = dialog.locator(`input#${fieldId}`);
  await input.fill(value);
  await input.dispatchEvent('input');
  await input.blur();
}

/** Selects Switzerland in the filterable country dropdown by its localized label. */
async function selectSwitzerland(page: Page, dialog: Locator, expectedEnglishLabel: string): Promise<void> {
  await selectDynamicFormOptionByText(page, dialog, 'country', new RegExp(`${expectedEnglishLabel}|Schweiz`, 'i'));
}

/** Verifies all fixture-driven values currently displayed by the tenant edit dialog. */
async function expectTenantTarget(dialog: Locator, target: TenantEditTarget): Promise<void> {
  await expect(dialog.locator('input#tenantName')).toHaveValue(target.tenantName);
  await expect(dialog.locator('input#excludeDivTax')).toBeChecked({ checked: target.excludeDivTax });
  await expect(dialog.locator('p-select#country')).toContainText(new RegExp(`${target.country}|Schweiz`, 'i'));
  await expect(dialog.locator('input#useGtImportTemplates')).toBeChecked({ checked: target.useGtImportTemplates });
}

test.describe.serial('edit tenant settings', () => {
  for (const user of tenantEditUsers) {
    const target = user.tenantEdit as TenantEditTarget;

    test(`updates the tenant of ${user.nickname}`, async ({ page }) => {
      await loginAsFixtureUser(page, user.nickname);
      const dialog = await openTenantEditDialog(page);

      await fillText(dialog, 'tenantName', target.tenantName);
      await dialog.locator('input#excludeDivTax').setChecked(target.excludeDivTax);
      await selectSwitzerland(page, dialog, target.country);
      await dialog.locator('input#useGtImportTemplates').setChecked(target.useGtImportTemplates);

      const updateResponsePromise = page.waitForResponse(
        (response) => response.url().endsWith('/api/tenant') && response.request().method() === 'PUT',
        { timeout: 20_000 }
      );
      await dialog.locator('button[type="submit"]').click();
      const updateResponse = await updateResponsePromise;
      expect(updateResponse.ok(), `${updateResponse.status()} ${await updateResponse.text()}`).toBeTruthy();

      const savedTenant = (await updateResponse.json()) as SavedTenant;
      expect(savedTenant.tenantName).toBe(target.tenantName);
      expect(savedTenant.excludeDivTax).toBe(target.excludeDivTax);
      expect(savedTenant.country).toBe(COUNTRY_CODE_SWITZERLAND);
      expect(savedTenant.useGtImportTemplates).toBe(target.useGtImportTemplates);
      await dialog.waitFor({ state: 'hidden', timeout: 10_000 });
    });

    test(`persists the tenant settings of ${user.nickname}`, async ({ page }) => {
      await loginAsFixtureUser(page, user.nickname);
      const dialog = await openTenantEditDialog(page);
      await expectTenantTarget(dialog, target);
    });
  }
});
