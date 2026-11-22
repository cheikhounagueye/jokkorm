"use client";

import { useEffect, useState } from "react";
import { getCurrentRole, type UserRole } from "@/lib/api";

/** Rôle simulé lu après montage (évite les écarts d'hydratation SSR/client). */
export function useRole(): UserRole | null {
  const [role, setRole] = useState<UserRole | null>(null);
  useEffect(() => setRole(getCurrentRole()), []);
  return role;
}
