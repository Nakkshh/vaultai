'use client';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import api from '@/lib/api';

interface Summary {
  totalSearches: number;
  avgLatencyMs: number;
  cacheHitRate: number;
  topQueries: { query: string; count: number }[];
}

interface TimelineEntry {
  date: string;
  count: number;
}

export default function AnalyticsPage() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [timeline, setTimeline] = useState<TimelineEntry[]>([]);
  const router = useRouter();

  useEffect(() => {
    if (!Cookies.get('vault_token')) { router.push('/'); return; }
    api.get('/api/analytics/summary').then(r => setSummary(r.data));
    api.get('/api/analytics/timeline').then(r => setTimeline(r.data.timeline));
  }, []);

  return (
    <main className="min-h-screen bg-gray-950 text-white p-8">
      <div className="max-w-4xl mx-auto space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-semibold">Analytics</h1>
          <button onClick={() => router.push('/dashboard')}
            className="text-sm text-gray-400 hover:text-white">
            ← Dashboard
          </button>
        </div>

        {summary && (
          <>
            <div className="grid grid-cols-3 gap-4">
              {[
                { label: 'Total Searches', value: summary.totalSearches },
                { label: 'Avg Latency', value: `${summary.avgLatencyMs}ms` },
                { label: 'Cache Hit Rate', value: `${summary.cacheHitRate}%` },
              ].map(stat => (
                <div key={stat.label}
                  className="bg-gray-900 border border-gray-800 rounded-xl p-5">
                  <p className="text-gray-400 text-xs mb-1">{stat.label}</p>
                  <p className="text-2xl font-semibold">{stat.value}</p>
                </div>
              ))}
            </div>

            <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
              <h2 className="text-sm font-medium mb-4">Top Queries</h2>
              {summary.topQueries.length === 0 ? (
                <p className="text-gray-500 text-sm">No searches yet.</p>
              ) : (
                <div className="space-y-2">
                  {summary.topQueries.map((q, i) => (
                    <div key={i} className="flex items-center justify-between">
                      <span className="text-sm text-gray-300 font-mono">{q.query}</span>
                      <span className="text-xs text-gray-500">{String(q.count)} searches</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
              <h2 className="text-sm font-medium mb-4">Searches — Last 7 Days</h2>
              {timeline.length === 0 ? (
                <p className="text-gray-500 text-sm">No data yet.</p>
              ) : (
                <div className="space-y-2">
                  {timeline.map((t, i) => (
                    <div key={i} className="flex items-center gap-3">
                      <span className="text-xs text-gray-400 w-24">{t.date}</span>
                      <div className="flex-1 bg-gray-800 rounded-full h-2">
                        <div
                          className="bg-white rounded-full h-2"
                          style={{
                            width: `${Math.min((t.count /
                              Math.max(...timeline.map(x => x.count))) * 100, 100)}%`
                          }}
                        />
                      </div>
                      <span className="text-xs text-gray-400 w-6">{t.count}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </main>
  );
}
