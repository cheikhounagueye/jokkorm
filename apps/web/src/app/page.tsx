export default function Home() {
  return (
    <main style={{ fontFamily: "system-ui, sans-serif", padding: "3rem", maxWidth: 720, margin: "0 auto" }}>
      <h1 style={{ fontSize: "2rem", marginBottom: "0.5rem" }}>JokkoRM</h1>
      <p style={{ color: "#555", marginTop: 0 }}>
        Module d&apos;archivage physique. Se branche sur une GED/SAE maître (Maarch RM, Alfresco, Nuxeo, ou SI custom)
        via le <code>SaeMasterPort</code>.
      </p>
      <p>
        Phase 0 — scaffold. Les écrans arrivent en phase 3 (voir{" "}
        <a href="https://github.com/jokkofile/jokkorm#roadmap">roadmap</a>).
      </p>
      <hr style={{ margin: "2rem 0", border: "none", borderTop: "1px solid #eee" }} />
      <p style={{ color: "#888", fontSize: "0.9rem" }}>
        Backend health : <a href="/api/actuator/health">/api/actuator/health</a> · API docs :{" "}
        <a href="/api/swagger-ui.html">/swagger-ui.html</a>
      </p>
    </main>
  );
}
