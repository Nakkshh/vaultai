'use client';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import api from '@/lib/api';

interface UserProfile {
  username: string;
  email: string;
  avatarUrl: string;
}

interface ConnectedRepo {
  id: number;
  name: string;
  fullName: string;
  indexStatus: string;
  language: string;
  description: string;
}

const statusColor: Record<string, string> = {
  PENDING: 'text-yellow-400',
  QUEUED: 'text-blue-400',
  PROCESSING: 'text-orange-400',
  COMPLETED: 'text-green-400',
  FAILED: 'text-red-400',
};

export default function Dashboard() {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [repos, setRepos] = useState<ConnectedRepo[]>([]);
  const router = useRouter();
  const logout = () => {
    Cookies.remove('vault_token');
    router.push('/');
  };
  const refreshRepo = async (repoId: number) => {
    try {
      await api.post(`/api/repos/${repoId}/refresh`);
      alert('Refresh queued. Status will update shortly.');
    } catch (e) {
      alert('Failed to queue refresh.');
    }
  };

  useEffect(() => {
    const token = Cookies.get('vault_token');
    if (!token) { router.push('/'); return; }

    api.get('/api/auth/me').then(res => setUser(res.data))
      .catch(() => { Cookies.remove('vault_token'); router.push('/'); });

    api.get('/api/repos').then(res => setRepos(res.data));
  }, []);

  if (!user) return (
    <div className="min-h-screen flex items-center justify-center bg-gray-950">
      <p className="text-gray-400">Loading...</p>
    </div>
  );

  return (
    <main className="min-h-screen bg-gray-950 text-white p-8">
      <div className="max-w-4xl mx-auto space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            {user.avatarUrl && (
              <img src={user.avatarUrl} alt="avatar" className="w-10 h-10 rounded-full" />
            )}
            <div>
              <h1 className="text-lg font-semibold">{user.username}</h1>
              <p className="text-gray-400 text-sm">{user.email}</p>
            </div>
          </div>
          <div className="flex gap-3">
            <button
              onClick={() => router.push('/search')}
              className="bg-gray-800 text-white text-sm font-medium px-4 py-2 rounded-lg hover:bg-gray-700 transition"
            >
              Search
            </button>
            <button
              onClick={() => router.push('/repos/connect')}
              className="bg-white text-gray-900 text-sm font-medium px-4 py-2 rounded-lg hover:bg-gray-100 transition"
            >
              + Connect Repo
            </button>
            <button
              onClick={() => router.push('/analytics')}
              className="bg-gray-800 text-white text-sm font-medium px-4 py-2 rounded-lg hover:bg-gray-700 transition"
            >
              Analytics
            </button>
            <button
              onClick={logout}
              className="bg-red-900 text-red-200 text-sm font-medium px-4 py-2 rounded-lg hover:bg-red-800 transition"
            >
              Logout
            </button>
          </div>
        </div>

        <div className="bg-gray-900 rounded-xl border border-gray-800 divide-y divide-gray-800">
          <div className="px-6 py-4">
            <h2 className="font-medium">Connected Repositories</h2>
          </div>
          {repos.length === 0 ? (
            <div className="px-6 py-8 text-center text-gray-500 text-sm">
              No repositories connected yet. Click + Connect Repo to start.
            </div>
          ) : (
            repos.map(repo => (
              <div key={repo.id} className="px-6 py-4 flex items-center justify-between">
                <div>
                  <p className="font-medium text-sm">{repo.fullName}</p>
                  <p className="text-gray-500 text-xs mt-1">{repo.description}</p>
                </div>
                <div className="flex items-center gap-3">
                  {repo.language && (
                    <span className="text-xs text-gray-400">{repo.language}</span>
                  )}

                  <span className={`text-xs font-medium ${statusColor[repo.indexStatus]}`}>
                    {repo.indexStatus}
                  </span>

                  {repo.indexStatus === 'COMPLETED' && (
                    <button
                      onClick={() => refreshRepo(repo.id)}
                      className="text-xs text-gray-400 hover:text-white border border-gray-700
                                px-2 py-1 rounded hover:border-gray-500 transition"
                    >
                      Refresh
                    </button>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </main>
  );
}