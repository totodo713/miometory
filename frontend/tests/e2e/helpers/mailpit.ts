/**
 * Mailpit REST API helper for E2E tests.
 * Mailpit runs on localhost:8025 (docker-compose.dev.yml).
 * Backend sends SMTP to localhost:1025 (application.yaml default).
 */

const MAILPIT_API = process.env.MAILPIT_API_URL || "http://localhost:8025/api/v1";

interface MailpitMessage {
  ID: string;
  From: { Address: string; Name: string };
  To: Array<{ Address: string; Name: string }>;
  Subject: string;
  Snippet: string;
  Created: string;
}

interface MailpitMessageDetail extends MailpitMessage {
  HTML: string;
  Text: string;
}

interface MailpitSearchResponse {
  total: number;
  messages: MailpitMessage[];
}

/**
 * Search for emails sent to a specific address.
 * Retries up to maxRetries times with intervalMs delay to handle SMTP delivery latency.
 */
export async function waitForEmail(
  to: string,
  options: { maxRetries?: number; intervalMs?: number; subject?: string } = {},
): Promise<MailpitMessage> {
  const { maxRetries = 10, intervalMs = 1000, subject } = options;

  for (let i = 0; i < maxRetries; i++) {
    const query = subject ? `to:${to} subject:"${subject}"` : `to:${to}`;
    const response = await fetch(`${MAILPIT_API}/search?query=${encodeURIComponent(query)}`);
    if (!response.ok) {
      throw new Error(`Mailpit search failed: ${response.status} ${response.statusText}`);
    }
    const data: MailpitSearchResponse = await response.json();
    if (data.total > 0) {
      return data.messages[0];
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new Error(`No email found for ${to} after ${maxRetries} retries`);
}

/**
 * Get the full message detail (including HTML body) by message ID.
 */
export async function getMessageDetail(messageId: string): Promise<MailpitMessageDetail> {
  const response = await fetch(`${MAILPIT_API}/message/${messageId}`);
  if (!response.ok) {
    throw new Error(`Mailpit get message failed: ${response.status}`);
  }
  return response.json();
}

/**
 * Extract the first href matching a pattern from an HTML email body.
 * Used to extract verification links like /verify-email?token=xxx.
 */
export function extractLinkFromHtml(html: string, pathPattern: RegExp): string | null {
  const matches = html.matchAll(/href="([^"]+)"/g);
  for (const match of matches) {
    if (pathPattern.test(match[1])) {
      return match[1];
    }
  }
  return null;
}

/**
 * Delete all messages in Mailpit. Call in test setup to ensure clean state.
 */
export async function clearMailbox(): Promise<void> {
  await fetch(`${MAILPIT_API}/messages`, { method: "DELETE" });
}
