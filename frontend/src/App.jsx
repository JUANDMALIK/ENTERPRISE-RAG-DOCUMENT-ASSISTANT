import { useEffect, useRef, useState } from 'react'
import ChatInput from './components/ChatInput.jsx'
import MessageList from './components/MessageList.jsx'
import Sidebar from './components/Sidebar.jsx'
import WelcomeScreen from './components/WelcomeScreen.jsx'
import { sendChatMessage } from './services/chatApi.js'
import './App.css'

function App() {
  const [messages, setMessages] = useState([])
  const [draft, setDraft] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')
  const [validationError, setValidationError] = useState('')
  const requestInProgress = useRef(false)
  const nextMessageId = useRef(0)
  const conversationEnd = useRef(null)

  useEffect(() => {
    conversationEnd.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages, isLoading, error])

  function createMessage(role, content) {
    nextMessageId.current += 1
    return { id: nextMessageId.current, role, content }
  }

  async function handleSubmit(event) {
    event.preventDefault()

    const message = draft.trim()
    if (!message) {
      setValidationError('Enter a message before sending.')
      return
    }

    if (requestInProgress.current) return

    requestInProgress.current = true
    setIsLoading(true)
    setError('')
    setValidationError('')
    setDraft('')
    const userMessage = createMessage('user', message)
    setMessages((currentMessages) => [
      ...currentMessages,
      userMessage,
    ])

    try {
      const answer = await sendChatMessage(message)
      const assistantMessage = createMessage('assistant', answer)
      setMessages((currentMessages) => [
        ...currentMessages,
        assistantMessage,
      ])
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Something went wrong while contacting the assistant.',
      )
    } finally {
      requestInProgress.current = false
      setIsLoading(false)
    }
  }

  function handleDraftChange(value) {
    setDraft(value)
    if (value.trim()) setValidationError('')
  }

  function handleNewChat() {
    if (requestInProgress.current) return
    setMessages([])
    setDraft('')
    setError('')
    setValidationError('')
  }

  return (
    <div className="app-shell">
      <Sidebar onNewChat={handleNewChat} disabled={isLoading} />

      <main className="chat-workspace">
        <header className="workspace-header">
          <div>
            <p className="eyebrow">YOUR AI WORKSPACE</p>
            <h1>Assistant</h1>
          </div>
          <span className="connection-label">
            <span className="connection-dot" aria-hidden="true" />
            Chat workspace
          </span>
        </header>

        <section
          className="conversation"
          aria-label="Chat conversation"
          aria-busy={isLoading}
        >
          {messages.length === 0 ? (
            <WelcomeScreen />
          ) : (
            <MessageList messages={messages} />
          )}

          {isLoading && (
            <div className="loading-message" role="status" aria-live="polite">
              <span className="assistant-avatar" aria-hidden="true">
                N
              </span>
              <div className="loading-bubble">
                <span className="visually-hidden">Assistant is responding</span>
                <span className="loading-dots" aria-hidden="true">
                  <span />
                  <span />
                  <span />
                </span>
              </div>
            </div>
          )}

          {error && (
            <div className="request-error" role="alert">
              <span className="error-icon" aria-hidden="true">
                !
              </span>
              <p>{error}</p>
            </div>
          )}
          <div ref={conversationEnd} aria-hidden="true" />
        </section>

        <ChatInput
          draft={draft}
          isLoading={isLoading}
          validationError={validationError}
          onDraftChange={handleDraftChange}
          onSubmit={handleSubmit}
        />
      </main>
    </div>
  )
}

export default App
