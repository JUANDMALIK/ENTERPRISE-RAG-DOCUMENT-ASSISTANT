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
        Upload a PDF or TXT document, then ask a question grounded in its
        contents.
      </p>
      <div className="welcome-hint">
        <span aria-hidden="true">↳</span>
        <span>Upload a document or ask a question below</span>
      </div>
    </div>
  )
}

export default WelcomeScreen
