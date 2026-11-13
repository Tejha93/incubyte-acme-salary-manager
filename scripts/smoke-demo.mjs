// Run after installing playwright-core and starting the local demo.
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const url = process.env.DEMO_URL || 'http://127.0.0.1:8080';
const browser = await chromium.launch({
  ...(process.env.DEMO_BROWSER_PATH ? { executablePath: process.env.DEMO_BROWSER_PATH }
    : { channel: process.env.DEMO_BROWSER || 'msedge' }),
  headless: true,
});
const context = await browser.newContext({
  viewport: { width: 1280, height: 840 },
  ...(process.env.DEMO_VIDEO_DIR && {
    recordVideo: { dir: process.env.DEMO_VIDEO_DIR, size: { width: 1280, height: 840 } },
  }),
});
const page = await context.newPage();
const dwell = () => process.env.DEMO_VIDEO_DIR ? page.waitForTimeout(1500) : Promise.resolve();

try {
  await page.goto(url);
  await page.getByRole('heading', { name: 'Sign in' }).waitFor();
  await dwell();
  await page.getByLabel('Username').fill(process.env.DEMO_USERNAME || 'hr');
  await page.getByLabel('Password').fill(process.env.DEMO_PASSWORD || 'acme-local-demo');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByText('10,000 matching').waitFor();
  await dwell();

  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByText('Showing 13–24 of 10,000').waitFor();
  await page.getByLabel('Find an employee').fill('AC-00001');
  await page.getByText('1 matching', { exact: true }).waitFor();
  await page.getByRole('button', { name: /View salary for.*AC-00001/ }).click();
  await page.getByRole('heading', { name: 'Aisha Bennett' }).waitFor();
  await dwell();

  const amount = page.getByLabel('New annual salary (INR)');
  const current = await amount.inputValue();
  const target = current === '725000.25' ? '735000.50' : '725000.25';
  await amount.fill(target);
  await page.getByLabel('Reason for change').fill('Annual pay review');
  await page.getByRole('button', { name: 'Save salary' }).click();
  await page.getByText('Salary updated. The change is recorded in history.').waitFor();
  assert.match(await page.locator('.history article').first().innerText(), /Annual pay review/);
  await dwell();

  await page.getByRole('button', { name: 'Close employee details' }).click();
  await page.getByRole('button', { name: 'Pay insights' }).click();
  await page.getByText('10,000', { exact: true }).waitFor();
  await dwell();
  await page.getByLabel('Country').selectOption('India');
  await page.getByLabel('Department').selectOption('Engineering');
  await page.getByRole('cell', { name: 'INR', exact: true }).waitFor();
  assert.equal(await page.locator('tbody tr').count(), 1);
  await dwell();

  await page.reload();
  await page.getByRole('heading', { name: 'Employee directory' }).waitFor();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.getByRole('heading', { name: 'Sign in' }).waitFor();
  await dwell();
  console.log('Browser smoke test passed.');
} finally {
  await context.close();
  await browser.close();
}
