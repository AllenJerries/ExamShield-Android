import { useState, useEffect } from 'react'
import { dashboardApi } from '../api/examshield'

export default function Navbar() {
  const [currentTime, setCurrentTime] = useState(new Date())
  const [dashboardData, setDashboardData] = useState(null)

  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(new Date()), 30000)
    return () => clearInterval(timer)
  }, [])

  useEffect(() => {
    const fetchData = async () => {
      try {
        const data = await dashboardApi.getDashboard()
        setDashboardData(data)
      } catch {}
    }
    fetchData()
    const interval = setInterval(fetchData, 30000)
    return () => clearInterval(interval)
  }, [])

  return (
    <header className="bg-white border-b border-gray-200 px-6 py-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-4">
          <h2 className="text-xl font-semibold text-gray-800">
            {document.title || 'Dashboard'}
          </h2>
          {dashboardData?.active_exams > 0 && (
            <span className="px-3 py-1 bg-danger-50 text-danger-600 rounded-full text-sm font-medium animate-pulse">
              {dashboardData.active_exams} Active Exam(s)
            </span>
          )}
        </div>
        <div className="flex items-center space-x-6">
          {dashboardData && (
            <div className="flex items-center space-x-4 text-sm text-gray-500">
              <span>Detected: {dashboardData.total_detections_today}</span>
              <span>|</span>
              <span>Alerts: {dashboardData.confirmed_incidents_today}</span>
            </div>
          )}
          <div className="flex items-center space-x-2">
            <div className="w-2 h-2 bg-green-500 rounded-full"></div>
            <span className="text-sm text-gray-600">
              {currentTime.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })}
            </span>
          </div>
        </div>
      </div>
    </header>
  )
}
