import type { Metadata } from "next";
import type { ReactNode } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { Providers } from "@/lib/providers";
import "./globals.css";

export const metadata: Metadata = {
  title: "JokkoRM — Records Management",
  description: "Module d'archivage physique pour SAE maître. AGPL-3.0.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="fr">
      <head>
        <link rel="preconnect" href="https://rsms.me/" />
        <link rel="stylesheet" href="https://rsms.me/inter/inter.css" />
      </head>
      <body>
        <Providers>
          <AppShell>
            {children}
          </AppShell>
        </Providers>
      </body>
    </html>
  );
}
