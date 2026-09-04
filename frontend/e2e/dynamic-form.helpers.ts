import { expect, Locator, Page } from '@playwright/test';

/**
 * Selects a dynamic-form option by visible text. Dynamic forms use either a native select or an Optimus p-select,
 * depending on whether the field needs richer option rendering such as stock-exchange country flags.
 */
export async function selectDynamicFormOptionByText(
  page: Page,
  scope: Locator,
  fieldId: string,
  text: string
): Promise<void> {
  const nativeSelect = scope.locator(`select#${fieldId}`).first();
  if (await nativeSelect.isVisible().catch(() => false)) {
    const option = nativeSelect.locator('option').filter({ hasText: text });
    await expect(option, `no option containing "${text}" in select#${fieldId}`).toHaveCount(1, { timeout: 15_000 });
    await nativeSelect.selectOption(await option.getAttribute('value'));
    await nativeSelect.dispatchEvent('change');
    return;
  }

  const optimusSelect = scope.locator(`p-select#${fieldId}`).first();
  await expect(optimusSelect, `dynamic-form field #${fieldId}`).toBeVisible({ timeout: 15_000 });
  await optimusSelect.click();

  const overlay = page.locator('.p-select-overlay:visible').first();
  await expect(overlay, `open options for p-select#${fieldId}`).toBeVisible({ timeout: 10_000 });
  const option = overlay.getByRole('option').filter({ hasText: text });
  await expect(option, `no option containing "${text}" in p-select#${fieldId}`).toHaveCount(1, { timeout: 10_000 });
  await option.click();
  await expect(overlay).toBeHidden({ timeout: 10_000 });
  await expect(optimusSelect).toContainText(text);
}
