function Sidebar({ onNewChat, disabled }) {
  return (
    <aside className="sidebar" aria-label="Application sidebar">
      <a className="brand" href="/" aria-label="Nexus AI home">
        <span className="brand-mark" aria-hidden="true">
          N
        </span>
        <span className="brand-copy">
          <strong>Nexus AI</strong>
          <span>Document Assistant</span>
        </span>
      </a>

      <div className="sidebar-content">
        <p className="sidebar-label">WORKSPACE</p>
        <button
          className="new-chat-button"
          type="button"
          onClick={onNewChat}
          disabled={disabled}
        >
          <span className="plus-icon" aria-hidden="true">
            +
          </span>
          New conversation
        </button>

        <div className="sidebar-note">
          <span className="note-icon" aria-hidden="true">
            ✦
          </span>
          <p>Ask a question to start a conversation with your AI assistant.</p>
        </div>
      </div>

      <div className="sidebar-footer">
        <span className="profile-avatar" aria-hidden="true">
          N
        </span>
        <div>
          <strong>Nexus workspace</strong>
          <span>AI document assistant</span>
        </div>
      </div>
    </aside>
  )
}

export default Sidebar
