export default function Home() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center bg-gray-950 text-white">
      <div className="text-center space-y-6 max-w-md px-4">
        <h1 className="text-4xl font-bold tracking-tight">VaultAI</h1>

        <p className="text-gray-400 text-lg">
          Semantic search for your codebases and docs.
        </p>

        <a
          href={`${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8085'}/oauth2/authorization/github`}
          className="inline-block bg-white text-gray-900 font-semibold px-6 py-3 rounded-lg hover:bg-gray-100 transition"
        >
          Login with GitHub
        </a>
      </div>
    </main>
  );
}