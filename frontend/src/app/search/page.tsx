'use client';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import api from '@/lib/api';

interface SearchResult {
  chunk_text: string;
  file_path: string;
  repo_id: number;
  start_line: number;
  end_line: number;
  score: number;
}

interface Repo {
  id: number;
  fullName: string;
  indexStatus: string;
}

export default function SearchPage() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [repos, setRepos] = useState<Repo[]>([]);
  const [selectedRepoId, setSelectedRepoId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const router = useRouter();

  useEffect(() => {
    if (!Cookies.get('vault_token')) { router.push('/'); return; }
    api.get('/api/repos').then(res => {
      const completed = res.data.filter((r: Repo) => r.indexStatus === 'COMPLETED');
      setRepos(completed);
    });
  }, []);

  const getRepoName = (repoId: number) => {
    const repo = repos.find(r => r.id === repoId);
    return repo ? repo.fullName : `Repo #${repoId}`;
  };

  const search = async () => {
    if (!query.trim()) return;
    setLoading(true);
    setSearched(true);
    try {
      const payload: any = { query };
      if (selectedRepoId !== null) payload.repo_id = selectedRepoId;
      const res = await api.post('/api/search', payload);
      setResults(res.data.results);
    } catch (e) {
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  const handleKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') search();
  };

  return (
    <main className="min-h-screen bg-gray-950 text-white p-8">
      <div className="max-w-4xl mx-auto space-y-6">

        <div className="flex items-center justify-between">
          <h1 className="text-xl font-semibold">VaultAI Search</h1>
          <button onClick={() => router.push('/dashboard')}
            className="text-sm text-gray-400 hover:text-white">
            ← Dashboard
          </button>
        </div>

        <div className="space-y-3">
          <div className="flex gap-3">
            <input
              type="text"
              value={query}
              onChange={e => setQuery(e.target.value)}
              onKeyDown={handleKey}
              placeholder="Search your codebase..."
              className="flex-1 bg-gray-900 border border-gray-700 rounded-lg px-4 py-3
                         text-sm text-white placeholder-gray-500 focus:outline-none
                         focus:border-gray-500"
            />
            <button
              onClick={search}
              disabled={loading}
              className="bg-white text-gray-900 font-medium px-6 py-3 rounded-lg
                         hover:bg-gray-100 disabled:opacity-50 transition text-sm"
            >
              {loading ? 'Searching...' : 'Search'}
            </button>
          </div>

          {/* Repo filter */}
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-xs text-gray-500">Search in:</span>
            <button
              onClick={() => setSelectedRepoId(null)}
              className={`text-xs px-3 py-1 rounded-full border transition
                ${selectedRepoId === null
                  ? 'border-white text-white'
                  : 'border-gray-700 text-gray-400 hover:border-gray-500'}`}
            >
              All Repos
            </button>
            {repos.map(repo => (
              <button
                key={repo.id}
                onClick={() => setSelectedRepoId(repo.id)}
                className={`text-xs px-3 py-1 rounded-full border transition
                  ${selectedRepoId === repo.id
                    ? 'border-white text-white'
                    : 'border-gray-700 text-gray-400 hover:border-gray-500'}`}
              >
                {repo.fullName}
              </button>
            ))}
          </div>
        </div>

        {searched && !loading && results.length === 0 && (
          <p className="text-gray-500 text-sm text-center py-8">
            No results found. Try a different query.
          </p>
        )}

        <div className="space-y-4">
          {results.map((result, i) => (
            <div key={i}
              className="bg-gray-900 border border-gray-800 rounded-xl p-5 space-y-3">
              <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-xs text-blue-400 font-medium">
                    {getRepoName(result.repo_id)}
                  </span>
                  <span className="text-gray-600 text-xs">→</span>
                  <span className="text-xs text-gray-400 font-mono bg-gray-800
                                   px-2 py-1 rounded">
                    {result.file_path}
                  </span>
                  <span className="text-xs text-gray-600">
                    L{result.start_line}–{result.end_line}
                  </span>
                </div>
                <span className="text-xs text-gray-600">
                  score: {result.score.toFixed(4)}
                </span>
              </div>
              <pre className="text-xs text-gray-300 bg-gray-950 rounded-lg p-4
                              overflow-x-auto whitespace-pre-wrap font-mono
                              border border-gray-800 max-h-60">
                {result.chunk_text}
              </pre>
            </div>
          ))}
        </div>

      </div>
    </main>
  );
}
