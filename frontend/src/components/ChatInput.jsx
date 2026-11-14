function ChatInput({
  draft,
  isLoading,
  validationError,
  onDraftChange,
  onSubmit,
}) {
  function handleKeyDown(event) {
    if (
      event.key === 'Enter' &&
      !event.shiftKey &&
      !event.nativeEvent.isComposing
    ) {
      event.preventDefault()
      event.currentTarget.form.requestSubmit()
    }
  }

  return (
    <div className="composer-wrap">
      <form className="composer" onSubmit={onSubmit}>
        <label className="visually-hidden" htmlFor="chat-message">
          Message the assistant
        </label>
        <textarea
          id="chat-message"
          name="message"
          value={draft}
          onChange={(event) => onDraftChange(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Ask Nexus AI anything..."
          rows="1"
          disabled={isLoading}
          aria-invalid={Boolean(validationError)}
          aria-describedby={
            validationError ? 'message-validation' : 'composer-help'
          }
        />
        <button
          className="send-button"
          type="submit"
          disabled={isLoading}
          aria-label={isLoading ? 'Sending message' : 'Send message'}
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
            focusable="false"
          >
            <path
              d="M5 12h14M13 6l6 6-6 6"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </button>
      </form>
      {validationError ? (
        <p className="validation-message" id="message-validation" role="alert">
          {validationError}
        </p>
      ) : (
        <p className="composer-help" id="composer-help">
          Press <kbd>Enter</kbd> to send <span aria-hidden="true">·</span>{' '}
          <kbd>Shift</kbd> + <kbd>Enter</kbd> for a new line
        </p>
      )}
    </div>
  )
}

export default ChatInput
