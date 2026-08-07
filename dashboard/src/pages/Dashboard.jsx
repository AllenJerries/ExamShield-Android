import { useQuery } from 'react-query'
import { dashboardApi } from '../api/examshield'
import StatCard from '../components/StatCard'
import AlertFeed from '../components/AlertFeed'
import { useNavigate } from 'react-router-dom'

export default function Dashboard() {
  const navigate = useNavigate()
  const { data, isLoading, error } = useQuery('dashboard', dashboardApi.getDashboard, {
    refetchInterval: 15000,
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-500"></div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="card p-8 text-center text-danger-500">
        <p className="text-2xl mb-2">⚠️</p>
        <p className="font-medium">Failed to load dashboard</p>
        <p className="text-sm text-gray-400">Check backend connection</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard
          title="Active Exams"
          value={data?.active_exams || 0}
          icon="📋"
          color="warning"
          subtitle="Currently monitoring"
          onClick={() => navigate('/exam-management')}
        />
        <StatCard
          title="Detections Today"
          value={data?.total_detections_today || 0}
          icon="📡"
          color="danger"
          subtitle="Unauthorized devices"
          onClick={() => navigate('/live-monitoring')}
        />
        <StatCard
          title="Confirmed Incidents"
          value={data?.confirmed_incidents_today || 0}
          icon="⚠️"
          color="danger"
          subtitle="Cheating attempts"
          onClick={() => navigate('/incidents')}
        />
        <StatCard
          title="Monitored Rooms"
          value={data?.monitored_rooms || 0}
          icon="🏛️"
          color="primary"
          subtitle="Under surveillance"
          onClick={() => navigate('/live-monitoring')}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div>
          <h2 className="text-lg font-semibold text-gray-800 mb-4">Recent Alerts</h2>
          <AlertFeed alerts={data?.recent_alerts || []} />
        </div>

        <div>
          <h2 className="text-lg font-semibold text-gray-800 mb-4">Device Type Distribution</h2>
          <div className="card p-6">
            {data?.most_common_device_types?.length > 0 ? (
              <div className="space-y-4">
                {data.most_common_device_types.map((dt, i) => (
                  <div key={i}>
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-sm capitalize">{dt.type}</span>
                      <span className="text-sm font-medium">{dt.count}</span>
                    </div>
                    <div className="w-full bg-gray-100 rounded-full h-2">
                      <div
                        className="bg-primary-500 rounded-full h-2 transition-all"
                        style={{
                          width: `${Math.min(
                            (dt.count /
                              Math.max(...data.most_common_device_types.map((t) => t.count))) *
                              100,
                            100
                          )}%`,
                        }}
                      ></div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center text-gray-400 py-8">
                <p>No data available yet</p>
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="flex space-x-4">
        <button
          onClick={() => navigate('/exam-management')}
          className="btn-primary flex items-center space-x-2"
        >
          <span>➕</span>
          <span>New Exam Session</span>
        </button>
        <button
          onClick={() => navigate('/statistics')}
          className="px-4 py-2 border border-primary-500 text-primary-500 rounded-lg hover:bg-primary-50 transition-colors"
        >
          View Full Statistics
        </button>
      </div>
    </div>
  )
}
