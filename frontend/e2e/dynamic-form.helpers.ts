import { expect, Locator, Page } from '@playwright/test';

/**
 * Selects a dynamic-form option by visible text. Dynamic forms use either a native select or an Optimus p-select,
 * depending on whether the field needs richer option rendering such as stock-exchange country flags.
 */
export async function selectDynamicFormOptionByText(
  page: Page,
  scope: Locator,
  fieldId: string,
  text: string | RegExp
): Promise<void> {
  const nativeSelect = scope.locator(`select#${fieldId}`).first();
  if (await nativeSelect.isVisible().catch(() => false)) {
    const option = nativeSelect.locator('option').filter({ hasText: text });
    await expect(option, `no option matching ${text} in select#${fieldId}`).toHaveCount(1, { timeout: 15_000 });
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
  await expect(option, `no option matching ${text} in p-select#${fieldId}`).toHaveCount(1, { timeout: 10_000 });
  // Tax metadata can rebuild the lower half of a security dialog while this overlay is open. The option remains the
  // unique intended target, but the newly rendered form can overlap it. Trigger the option's own handler and prove the
  // model was updated; an overlay recreated by that update is then closed explicitly.
  await option.evaluate((element: HTMLElement) => element.click());
  await expect(optimusSelect).toContainText(text, { timeout: 10_000 });
  // A selection closes the overlay through a leave animation. An Escape pressed while it is still fading no longer
  // reaches the overlay but the dialog, which closes on Escape, so it is sent only when the overlay stays open.
  const openOverlays = page.locator('.p-select-overlay:visible');
  const closedByItself = await expect(openOverlays)
    .toHaveCount(0, { timeout: 2_000 })
    .then(
      () => true,
      () => false
    );
  if (!closedByItself) {
    await page.keyboard.press('Escape');
  }
  await expect(openOverlays).toHaveCount(0, { timeout: 10_000 });
}
