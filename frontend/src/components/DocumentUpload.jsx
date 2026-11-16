import { useRef, useState } from 'react'
import {
  MAX_DOCUMENT_SIZE_BYTES,
  uploadDocument,
} from '../services/documentsApi.js'

function DocumentUpload() {
  const [isUploading, setIsUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [selectedFileName, setSelectedFileName] = useState('')
  const [feedback, setFeedback] = useState({ message: '', isError: false })
  const fileInput = useRef(null)
  const requestInProgress = useRef(false)

  async function handleFileChange(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file || requestInProgress.current) return

    const extension = file.name.split('.').pop()?.toLowerCase()
    if (!['pdf', 'txt'].includes(extension)) {
      setFeedback({ message: 'Choose a PDF or TXT file.', isError: true })
      return
    }
    if (file.size === 0) {
      setFeedback({ message: 'The selected file is empty.', isError: true })
      return
    }
    if (file.size > MAX_DOCUMENT_SIZE_BYTES) {
      setFeedback({ message: 'The file must not exceed 10 MiB.', isError: true })
      return
    }

    requestInProgress.current = true
    setIsUploading(true)
    setUploadProgress(0)
    setSelectedFileName(file.name)
    setFeedback({ message: '', isError: false })
    try {
      const result = await uploadDocument(file, setUploadProgress)
      setFeedback({
        message: `${result.documentName} indexed (${result.chunkCount} chunks).`,
        isError: false,
      })
    } catch (error) {
      setFeedback({
        message:
          error instanceof Error ? error.message : 'The document upload failed.',
        isError: true,
      })
    } finally {
      requestInProgress.current = false
      setIsUploading(false)
      setUploadProgress(0)
    }
  }

  return (
    <div className="document-upload">
      <input
        ref={fileInput}
        className="visually-hidden"
        id="document-upload-input"
        type="file"
        accept=".pdf,.txt,application/pdf,text/plain"
        onChange={handleFileChange}
        aria-label="Choose a PDF or TXT document"
      />
      <button
        className="upload-button"
        type="button"
        onClick={() => fileInput.current?.click()}
        disabled={isUploading}
      >
        {isUploading ? 'Uploading…' : 'Upload document'}
      </button>
      {isUploading && (
        <div className="upload-progress" role="status" aria-live="polite">
          <span className="upload-file-name">{selectedFileName}</span>
          <progress
            value={uploadProgress}
            max="100"
            aria-label={`Uploading ${selectedFileName}`}
          />
          <span>{uploadProgress === 100 ? 'Indexing…' : `${uploadProgress}%`}</span>
        </div>
      )}
      {feedback.message && (
        <p
          className={`upload-feedback${feedback.isError ? ' upload-feedback-error' : ''}`}
          role={feedback.isError ? 'alert' : 'status'}
        >
          {feedback.message}
        </p>
      )}
    </div>
  )
}

export default DocumentUpload
