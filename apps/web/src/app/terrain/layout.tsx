import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = {
  title: "JokkoRM — Magasin",
  appleWebApp: { capable: true, title: "Magasin", statusBarStyle: "black-translucent" },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#14181f",
};

export default function TerrainLayout({ children }: { children: ReactNode }) {
  return children;
}
