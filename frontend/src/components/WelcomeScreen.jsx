function WelcomeScreen() {
  return (
    <div className="welcome-screen">
      <div className="welcome-mark" aria-hidden="true">
        <span>N</span>
        <i />
      </div>
      <p className="eyebrow">WELCOME TO NEXUS AI</p>
      <h2>Your ideas, in conversation.</h2>
      <p className="welcome-description">
        Ask the assistant a question to get started. Your conversation will
        appear here.
      </p>
      <div className="welcome-hint">
        <span aria-hidden="true">↳</span>
        <span>Start by typing a message below</span>
      </div>
    </div>
  )
}

export default WelcomeScreen
