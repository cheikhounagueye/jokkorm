"use client";

import { IconContext } from "@phosphor-icons/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useState } from "react";

const ICON_DEFAULTS = { weight: "duotone" as const, size: 18 };

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(() => new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        retry: 1,
        refetchOnWindowFocus: false,
      },
    },
  }));
  return (
    <QueryClientProvider client={client}>
      <IconContext.Provider value={ICON_DEFAULTS}>{children}</IconContext.Provider>
    </QueryClientProvider>
  );
}
