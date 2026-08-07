import { useQuery } from 'react-query'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  LineElement,
  ArcElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js'
import { Bar, Line, Pie } from 'react-chartjs-2'
import { dashboardApi } from '../api/examshield'

ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  LineElement,
  ArcElement,
  Title,
  Tooltip,
  Legend
)

export default function Statistics() {
  const { data: dashboard, isLoading } = useQuery('dashboard', dashboardApi.getDashboard, {
    refetchInterval: 30000,
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-500"></div>
      </div>
    )
  }

  const detectionTrendData = {
    labels: dashboard?.detections_last_7_days?.map((d) => d.date) || [],
    datasets: [
      {
        label: 'Detections',
        data: dashboard?.detections_last_7_days?.map((d) => d.count) || [],
        borderColor: '#1a73e8',
        backgroundColor: 'rgba(26, 115, 232, 0.1)',
        fill: true,
        tension: 0.4,
      },
    ],
  }

  const deviceTypeData = {
    labels: dashboard?.most_common_device_types?.map((d) => d.type) || [],
    datasets: [
      {
        data: dashboard?.most_common_device_types?.map((d) => d.count) || [],
        backgroundColor: ['#d32f2f', '#ff9800', '#1a73e8', '#4caf50', '#9c27b0', '#00bcd4'],
      },
    ],
  }

  const riskData = {
    labels: dashboard?.risk_distribution?.map((r) => r.risk_level) || [],
    datasets: [
      {
        label: 'Risk Distribution',
        data: dashboard?.risk_distribution?.map((r) => r.count) || [],
        backgroundColor: ['#d32f2f', '#ff9800', '#4caf50'],
      },
    ],
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-800">Statistics</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="card p-6">
          <h3 className="font-semibold text-gray-700 mb-4">Detection Trends (7 Days)</h3>
          {dashboard?.detections_last_7_days?.length > 0 ? (
            <Line
              data={detectionTrendData}
              options={{
                responsive: true,
                plugins: { legend: { display: false } },
                scales: {
                  y: { beginAtZero: true, ticks: { stepSize: 1 } },
                },
              }}
            />
          ) : (
            <p className="text-gray-400 text-center py-8">No trend data available</p>
          )}
        </div>

        <div className="card p-6">
          <h3 className="font-semibold text-gray-700 mb-4">Device Type Distribution</h3>
          {dashboard?.most_common_device_types?.length > 0 ? (
            <Pie
              data={deviceTypeData}
              options={{
                responsive: true,
                plugins: {
                  legend: {
                    position: 'bottom',
                    labels: { padding: 20 },
                  },
                },
              }}
            />
          ) : (
            <p className="text-gray-400 text-center py-8">No device data available</p>
          )}
        </div>

        <div className="card p-6">
          <h3 className="font-semibold text-gray-700 mb-4">Risk Level Distribution</h3>
          {dashboard?.risk_distribution?.length > 0 ? (
            <Bar
              data={riskData}
              options={{
                responsive: true,
                plugins: { legend: { display: false } },
                scales: {
                  y: { beginAtZero: true, ticks: { stepSize: 1 } },
                },
              }}
            />
          ) : (
            <p className="text-gray-400 text-center py-8">No risk data available</p>
          )}
        </div>

        <div className="card p-6">
          <h3 className="font-semibold text-gray-700 mb-4">Room Comparison</h3>
          {dashboard?.room_comparison?.length > 0 ? (
            <Bar
              data={{
                labels: dashboard.room_comparison.map((r) => r.name),
                datasets: [
                  {
                    label: 'Detections',
                    data: dashboard.room_comparison.map((r) => r.detections),
                    backgroundColor: '#1a73e8',
                  },
                ],
              }}
              options={{
                responsive: true,
                plugins: { legend: { display: false } },
                scales: {
                  y: { beginAtZero: true, ticks: { stepSize: 1 } },
                },
              }}
            />
          ) : (
            <p className="text-gray-400 text-center py-8">No room comparison data available</p>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {dashboard?.detections_by_hour?.length > 0 && (
          <div className="card p-6 col-span-full">
            <h3 className="font-semibold text-gray-700 mb-4">Detections by Hour</h3>
            <Bar
              data={{
                labels: Array.from({ length: 24 }, (_, i) => `${i}:00`),
                datasets: [
                  {
                    label: 'Detections',
                    data: Array.from({ length: 24 }, (_, i) => {
                      const found = dashboard.detections_by_hour.find((d) => d.hour === i)
                      return found ? found.count : 0
                    }),
                    backgroundColor: '#ff9800',
                  },
                ],
              }}
              options={{
                responsive: true,
                plugins: { legend: { display: false } },
                scales: {
                  y: { beginAtZero: true, ticks: { stepSize: 1 } },
                },
              }}
            />
          </div>
        )}
      </div>
    </div>
  )
}
