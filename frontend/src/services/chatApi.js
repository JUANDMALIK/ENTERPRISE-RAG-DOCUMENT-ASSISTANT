const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '')
  .trim()
  .replace(/\/+$/, '')

export async function sendChatMessage(message) {
  let response

  try {
    response = await fetch(`${API_BASE_URL}/api/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ message }),
    })
  } catch {
    throw new Error(
      'Unable to reach the assistant. Check your connection and VITE_API_BASE_URL, then try again.',
    )
  }

  let payload
  try {
    payload = await response.json()
  } catch {
    if (!response.ok) {
      throw new Error(`The assistant request failed (HTTP ${response.status}).`)
    }
    throw new Error('The assistant returned an invalid response.')
  }

  if (!response.ok) {
    throw new Error(
      `The assistant request failed (HTTP ${response.status}).`,
    )
  }

  if (typeof payload?.answer !== 'string') {
    throw new Error('The assistant response did not include a valid answer.')
  }

  return payload.answer
}
