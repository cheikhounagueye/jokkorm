import type { Metadata } from "next";
import { Inter } from "next/font/google";
import type { ReactNode } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { Providers } from "@/lib/providers";
import "./globals.css";

// Police servie par JokkoRM lui-même : le mode magasin tourne sur un réseau local sans Internet.
const inter = Inter({ subsets: ["latin", "latin-ext"], variable: "--font-inter", display: "swap" });

export const metadata: Metadata = {
  title: "JokkoRM — Records Management",
  description: "Module d'archivage physique pour SAE maître. AGPL-3.0.",
  icons: { apple: "/icons/apple-touch-icon.png" },
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="fr" className={inter.variable}>
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
