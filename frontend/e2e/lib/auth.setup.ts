import {expect, test as setup} from '@playwright/test';

setup('generic integration-host users can sign in', async ({page}) => {
  await page.goto('/login');
  await page.locator('#email').waitFor({state: 'visible'});
  await page.locator('#email').fill('admin@test.local');
  await page.locator('#password').fill('Test1234');
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/mail\/mailsendrecv/);
});
