"use client";

import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";

type Health = { status: string; service: string };
type State =
  | { kind: "loading" }
  | { kind: "up"; health: Health }
  | { kind: "down"; message: string };

export function BackendStatus() {
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    apiGet<Health>("/api/health")
      .then((health) => setState({ kind: "up", health }))
      .catch((error: Error) => setState({ kind: "down", message: error.message }));
  }, []);

  if (state.kind === "loading") {
    return <p className="text-zinc-500">Checking backend…</p>;
  }
  if (state.kind === "down") {
    return (
      <p className="text-red-600">
        Backend unreachable: {state.message}
      </p>
    );
  }
  return (
    <p className="text-green-700 dark:text-green-400">
      Backend {state.health.service} is {state.health.status}
    </p>
  );
}
