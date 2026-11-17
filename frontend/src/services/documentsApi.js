import { API_BASE_URL } from './apiBaseUrl.js'

export const MAX_DOCUMENT_SIZE_BYTES = 10 * 1024 * 1024

export function uploadDocument(file, onProgress = () => {}) {
  const formData = new FormData()
  formData.append('file', file)

  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open('POST', `${API_BASE_URL}/api/documents`)
    request.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    })

    request.addEventListener('load', () => {
      let payload
      try {
        payload = JSON.parse(request.responseText)
      } catch {
        reject(
          new Error(
            request.status < 200 || request.status >= 300
              ? `The upload failed (HTTP ${request.status}).`
              : 'The document service returned an invalid response.',
          ),
        )
        return
      }

      if (request.status < 200 || request.status >= 300) {
        reject(
          new Error(
            typeof payload?.message === 'string'
              ? payload.message
              : `The upload failed (HTTP ${request.status}).`,
          ),
        )
        return
      }

      if (
        typeof payload?.documentId !== 'string' ||
        typeof payload?.documentName !== 'string' ||
        !Number.isInteger(payload?.chunkCount)
      ) {
        reject(new Error('The document service returned an invalid upload result.'))
        return
      }

      resolve(payload)
    })

    request.addEventListener('error', () => {
      reject(
        new Error(
          'Unable to reach the document service. Check your connection and VITE_API_BASE_URL, then try again.',
        ),
      )
    })
    request.addEventListener('abort', () => {
      reject(new Error('The document upload was cancelled.'))
    })
    request.send(formData)
  })
}
