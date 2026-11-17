# Nexus AI – Document Assistant

React 19 and Vite frontend for the Enterprise RAG Document Assistant.

## Run locally

1. Copy `.env.example` to `.env.local` and set `VITE_API_BASE_URL` to the backend
   origin.
2. Install dependencies with `npm install`.
3. Start the frontend with `npm run dev`.

The chat sends `POST ${VITE_API_BASE_URL}/api/chat` with
`{"message":"..."}` and displays the `answer` from the JSON response. If the
frontend and backend use different origins, the backend must allow the
frontend's origin through CORS.

## Checks

- `npm run lint`
- `npm run build`
