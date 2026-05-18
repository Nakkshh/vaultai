'use client';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import api from '@/lib/api';

interface GithubRepo {
  id: number;
  name: string;
  full_name: string;
  default_branch: string;
  language: string;
  description: string;
  private: boolean;
}

export default function ConnectRepo() {
  const [repos, setRepos] = useState<GithubRepo[]>([]);
  const [loading, setLoading] = useState(true);
  const [connecting, setConnecting] = useState<number | null>(null);
  const router = useRouter();

  useEffect(() => {
    if (!Cookies.get('vault_token')) { router.push('/'); return; }
    api.get('/api/repos/available')
      .then(res => setRepos(res.data))
      .finally(() => setLoading(false));
  }, []);

  const connect = async (repo: GithubRepo) => {
    setConnecting(repo.id);
    try {
      await api.post('/api/repos/connect', {
        githubRepoId: String(repo.id),
        name: repo.name,
        fullName: repo.full_name,
        defaultBranch: repo.default_branch,
        language: repo.language,
        description: repo.description,
      });
      router.push('/dashboard');
    } catch (e) {
      alert('Failed to connect repo. Try again.');
      setConnecting(null);
    }
  };

  return (
    <main className="min-h-screen bg-gray-950 text-white p-8">
      <div className="max-w-3xl mx-auto space-y-6">
        <div className="flex items-center gap-4">
          <button onClick={() => router.back()}
            className="text-gray-400 hover:text-white text-sm">← Back</button>
          <h1 className="text-lg font-semibold">Connect a Repository</h1>
        </div>

        {loading ? (
          <p className="text-gray-400 text-sm">Loading your repositories...</p>
        ) : (
          <div className="bg-gray-900 rounded-xl border border-gray-800 divide-y divide-gray-800">
            {repos.map(repo => (
              <div key={repo.id} className="px-6 py-4 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium">{repo.full_name}</p>
                  <p className="text-gray-500 text-xs mt-1">
                    {repo.language || 'Unknown'} · {repo.private ? 'Private' : 'Public'}
                  </p>
                  {repo.description && (
                    <p className="text-gray-600 text-xs mt-1">{repo.description}</p>
                  )}
                </div>
                <button
                  onClick={() => connect(repo)}
                  disabled={connecting === repo.id}
                  className="text-xs bg-white text-gray-900 font-medium px-3 py-1.5 rounded-md hover:bg-gray-100 disabled:opacity-50 transition"
                >
                  {connecting === repo.id ? 'Connecting...' : 'Connect'}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}