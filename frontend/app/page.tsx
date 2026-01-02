export default async function Home() {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL}/health`, {
    cache: "no-store",
  }).then((r) => r.json());

  return (
    <main className="p-8">
      <h1 className="text-2xl font-bold">WorkLog System</h1>
      <p className="mt-4">Backend status: {res.status}</p>
    </main>
  );
}
