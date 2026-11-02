import { BackendStatus } from "./backend-status";

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-4 px-4 py-16">
      <h1 className="text-3xl font-semibold">Vet Clinic</h1>
      <p className="text-zinc-600 dark:text-zinc-400">
        Project skeleton. Features are delivered through the increments in
        context/PLAN.md.
      </p>
      <BackendStatus />
    </main>
  );
}
