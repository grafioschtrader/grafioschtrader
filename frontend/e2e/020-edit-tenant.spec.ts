import {expect, Locator, Page, test} from '@playwright/test';

import {loadUsers, loginAsFixtureUser, UserFixture} from './helpers';

/** Target values for Grafioschtrader's application-specific tenant edit dialog. */
interface TenantEditTarget {
  tenantName: string;
  excludeDivTax: boolean;
  /** English natural key from the fixture; the de-CH UI renders this country as "Schweiz". */
  country: string;
  /** Natural key of the import platform; its database id differs between test databases. */
  idGtImportPlatform: string;
}

interface TenantEditUserFixture extends UserFixture {
  tenantEdit?: TenantEditTarget;
}

interface SavedTenant {
  tenantName: string;
  excludeDivTax: boolean;
  country: string;
  idGtImportPlatform: number;
}

const COUNTRY_CODE_SWITZERLAND = 'CH';
const EDIT_TENANT_RX = /^(Edit Client|Bearbeiten Klient|EDIT_RECORD\|TENANT)(\.\.\.)?$/i;

const tenantEditUsers = (loadUsers() as TenantEditUserFixture[])
  // Partial tenantEdit blocks belong to the backend integration suite. This UI workflow needs all
  // four dialog values and therefore consumes only complete targets.
  .filter(user => user.tenantEdit != null
    && typeof user.tenantEdit.tenantName === 'string'
    && typeof user.tenantEdit.excludeDivTax === 'boolean');

if (tenantEditUsers.length === 0) {
  throw new Error('No tenantEdit target found in the Grafioschtrader users.json fixture');
}

/** Opens the tenant edit dialog from the root node of the main portfolio tree. */
async function openTenantEditDialog(page: Page): Promise<Locator> {
  const tenantRoot = page.locator('.p-tree-node-content').first();
  await tenantRoot.waitFor({state: 'visible', timeout: 15_000});
  await tenantRoot.click({button: 'right'});

  const editItem = page.locator('p-contextmenu').getByText(EDIT_TENANT_RX).first();
  await editItem.waitFor({state: 'visible', timeout: 5_000});
  await editItem.click();

  const dialog = page.locator('.p-dialog:visible');
  await dialog.waitFor({state: 'visible', timeout: 10_000});
  await expect(dialog.locator('select#country option')).not.toHaveCount(0, {timeout: 10_000});
  await expect(dialog.locator('select#idGtImportPlatform option')).not.toHaveCount(0, {timeout: 10_000});
  return dialog;
}

/** Fills a dynamic-form text control and commits its value to the Angular form. */
async function fillText(dialog: Locator, fieldId: string, value: string): Promise<void> {
  const input = dialog.locator(`input#${fieldId}`);
  await input.fill(value);
  await input.dispatchEvent('input');
  await input.blur();
}

/** Selects an option by its fixture label and returns the option's generated database-id value. */
async function selectOptionByLabel(select: Locator, label: string): Promise<string> {
  const option = select.locator('option').filter({hasText: label}).first();
  await option.waitFor({state: 'attached', timeout: 10_000});
  const value = await option.getAttribute('value');
  expect(value, `option '${label}' has no value`).not.toBeNull();
  await select.selectOption({value: value as string});
  await select.dispatchEvent('change');
  return value as string;
}

/** Selects Switzerland by ISO code while checking the localized option label. */
async function selectSwitzerland(countrySelect: Locator, expectedEnglishLabel: string): Promise<void> {
  const option = countrySelect.locator(`option[value="${COUNTRY_CODE_SWITZERLAND}"]`);
  await option.waitFor({state: 'attached', timeout: 10_000});
  await expect(option).toHaveText(new RegExp(`^\\s*(${expectedEnglishLabel}|Schweiz)\\s*$`, 'i'));
  await countrySelect.selectOption(COUNTRY_CODE_SWITZERLAND);
  await countrySelect.dispatchEvent('change');
}

/** Verifies all fixture-driven values currently displayed by the tenant edit dialog. */
async function expectTenantTarget(dialog: Locator, target: TenantEditTarget): Promise<void> {
  await expect(dialog.locator('input#tenantName')).toHaveValue(target.tenantName);
  await expect(dialog.locator('input#excludeDivTax')).toBeChecked({checked: target.excludeDivTax});
  await expect(dialog.locator('select#country')).toHaveValue(COUNTRY_CODE_SWITZERLAND);
  await expect(dialog.locator('select#country option:checked'))
    .toHaveText(new RegExp(`^\\s*(${target.country}|Schweiz)\\s*$`, 'i'));
  await expect(dialog.locator('select#idGtImportPlatform option:checked'))
    .toHaveText(target.idGtImportPlatform);
}

test.describe.serial('edit tenant settings', () => {
  for (const user of tenantEditUsers) {
    const target = user.tenantEdit as TenantEditTarget;

    test(`updates the tenant of ${user.nickname}`, async ({page}) => {
      await loginAsFixtureUser(page, user.nickname);
      const dialog = await openTenantEditDialog(page);

      await fillText(dialog, 'tenantName', target.tenantName);
      await dialog.locator('input#excludeDivTax').setChecked(target.excludeDivTax);
      await selectSwitzerland(dialog.locator('select#country'), target.country);
      const importPlatformValue = await selectOptionByLabel(
        dialog.locator('select#idGtImportPlatform'), target.idGtImportPlatform);

      const updateResponsePromise = page.waitForResponse(response =>
        response.url().endsWith('/api/tenant') && response.request().method() === 'PUT',
      {timeout: 20_000});
      await dialog.locator('button[type="submit"]').click();
      const updateResponse = await updateResponsePromise;
      expect(updateResponse.ok(), `${updateResponse.status()} ${await updateResponse.text()}`).toBeTruthy();

      const savedTenant = await updateResponse.json() as SavedTenant;
      expect(savedTenant.tenantName).toBe(target.tenantName);
      expect(savedTenant.excludeDivTax).toBe(target.excludeDivTax);
      expect(savedTenant.country).toBe(COUNTRY_CODE_SWITZERLAND);
      expect(savedTenant.idGtImportPlatform).toBe(Number(importPlatformValue));
      await dialog.waitFor({state: 'hidden', timeout: 10_000});
    });

    test(`persists the tenant settings of ${user.nickname}`, async ({page}) => {
      await loginAsFixtureUser(page, user.nickname);
      const dialog = await openTenantEditDialog(page);
      await expectTenantTarget(dialog, target);
    });
  }
});
