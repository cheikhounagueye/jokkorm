import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Providers } from "@/lib/providers";
import "./globals.css";

export const metadata: Metadata = {
  title: "JokkoRM — Records Management",
  description: "Module d'archivage physique pour SAE maître. AGPL-3.0.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="fr">
      <body>
        <Providers>
          <TopBar />
          <main style={{ maxWidth: 1080, margin: "0 auto", padding: "1.5rem" }}>
            {children}
          </main>
        </Providers>
      </body>
    </html>
  );
}

function TopBar() {
  return (
    <header
      style={{
        background: "#1e2d5e",
        color: "white",
        padding: "0.75rem 1.5rem",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
        <a href="/" style={{ color: "white", textDecoration: "none", fontWeight: 600 }}>
          JokkoRM
        </a>
        <span style={{ opacity: 0.6, fontSize: "0.85rem" }}>
          Module d&apos;archivage physique
        </span>
      </div>
      <nav style={{ display: "flex", gap: "1rem", fontSize: "0.9rem" }}>
        <a href="/records" style={{ color: "white", textDecoration: "none" }}>Archives</a>
        <a href="/magasin" style={{ color: "white", textDecoration: "none" }}>Magasin</a>
        <a href="/magasin/lots" style={{ color: "white", textDecoration: "none" }}>Lots</a>
        <a href="https://github.com/cheikhounagueye/jokkorm" target="_blank" rel="noreferrer"
           style={{ color: "white", textDecoration: "none", opacity: 0.7 }}>GitHub</a>
      </nav>
    </header>
  );
}
