import { expect, test } from '@playwright/test';
import { libHelpers } from './helpers';

// The first entry of the menu bar collapses and restores the navigation tree. Its icon is changed on the menu item
// that the published top menu shares, so the menu bar only shows the new direction when the model is published anew.

test('the collapse entry of the menu bar shows the direction the tree can be moved to', async ({ page }) => {
  await libHelpers.loginAsFixtureUser(page, 'admin');
  await expect(page.locator('p-tree')).toBeVisible();
  const menubar = page.locator('menubar');

  await expect(menubar.locator('.pi-chevron-left')).toHaveCount(1);
  await menubar.locator('.pi-chevron-left').click();
  await expect(menubar.locator('.pi-chevron-right')).toHaveCount(1);
  await expect(menubar.locator('.pi-chevron-left')).toHaveCount(0);

  await menubar.locator('.pi-chevron-right').click();
  await expect(menubar.locator('.pi-chevron-left')).toHaveCount(1);
  await expect(menubar.locator('.pi-chevron-right')).toHaveCount(0);
});

test('dragging the gutter until the tree disappears turns the collapse icon', async ({ page }) => {
  await libHelpers.loginAsFixtureUser(page, 'admin');
  await expect(page.locator('p-tree')).toBeVisible();
  const menubar = page.locator('menubar');
  await expect(menubar.locator('.pi-chevron-left')).toHaveCount(1);

  // No event inside the menu bar is involved: Split.js reports the end of the drag to ViewSizeChangedService
  const gutter = page.locator('.gutter.gutter-horizontal');
  const box = await gutter.boundingBox();
  await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2);
  await page.mouse.down();
  await page.mouse.move(0, box!.y + box!.height / 2, { steps: 10 });
  await page.mouse.up();

  await expect(menubar.locator('.pi-chevron-right')).toHaveCount(1);
  await menubar.locator('.pi-chevron-right').click();
  await expect(menubar.locator('.pi-chevron-left')).toHaveCount(1);
});
