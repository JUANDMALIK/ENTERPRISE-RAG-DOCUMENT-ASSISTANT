function MessageList({ messages }) {
  return (
    <div className="message-list" aria-live="polite">
      {messages.map((message) => {
        const isAssistant = message.role === 'assistant'

        return (
          <article
            className={`message-row ${isAssistant ? 'message-row-assistant' : 'message-row-user'}`}
            key={message.id}
          >
            {isAssistant && (
              <span className="assistant-avatar" aria-label="Nexus AI">
                N
              </span>
            )}
            <div className="message-content">
              <span className="message-author">
                {isAssistant ? 'Nexus AI' : 'You'}
              </span>
              <p className="message-bubble">{message.content}</p>
              {isAssistant && message.sources?.length > 0 && (
                <div className="message-sources" aria-label="Answer sources">
                  <span>Sources</span>
                  <ul>
                    {message.sources.map((source, sourceIndex) => (
                      <li
                        key={`${source.documentId}-${source.chunkIndex ?? sourceIndex}`}
                      >
                        {source.documentName}
                        {Number.isInteger(source.chunkIndex) &&
                          ` · chunk ${source.chunkIndex + 1}`}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
            {!isAssistant && (
              <span className="user-avatar" aria-label="You">
                Y
              </span>
            )}
          </article>
        )
      })}
    </div>
  )
}

export default MessageList
