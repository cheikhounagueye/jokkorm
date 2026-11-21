import Link from "next/link";

export default function Home() {
  return (
    <section>
      <h1 style={{ marginTop: 0 }}>JokkoRM</h1>
      <p style={{ color: "#555", fontSize: "1.05rem" }}>
        Module d&apos;archivage physique qui se branche sur votre GED/SAE maître (Maarch RM,
        Alfresco, Nuxeo, SI custom) via le <code>SaeMasterPort</code>.
      </p>
      <p>
        Pattern <strong>une seule archive, deux gardiens</strong>.{" "}
        <a href="https://github.com/cheikhounagueye/jokkorm/tree/main/docs/pattern"
           target="_blank" rel="noreferrer">
          Lire la spec
        </a>.
      </p>
      <hr style={{ margin: "2rem 0", border: "none", borderTop: "1px solid #e3e5ea" }} />
      <nav>
        <Link href="/records" style={{
          display: "inline-block",
          background: "#1e2d5e",
          color: "white",
          padding: "0.75rem 1.25rem",
          borderRadius: 4,
          textDecoration: "none",
          fontWeight: 500,
        }}>
          Ouvrir les archives →
        </Link>
      </nav>
      <p style={{ marginTop: "2rem", color: "#888", fontSize: "0.9rem" }}>
        Backend health : <a href="/api/actuator/health">/api/actuator/health</a> · API docs :{" "}
        <a href="/api/swagger-ui.html">/swagger-ui.html</a>
      </p>
    </section>
  );
}
