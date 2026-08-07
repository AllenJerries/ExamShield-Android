import { Routes, Route } from 'react-router-dom'
import Sidebar from './components/Sidebar'
import Navbar from './components/Navbar'
import Dashboard from './pages/Dashboard'
import LiveMonitoring from './pages/LiveMonitoring'
import ExamManagement from './pages/ExamManagement'
import IncidentReports from './pages/IncidentReports'
import Statistics from './pages/Statistics'
import Settings from './pages/Settings'

function App() {
  return (
    <div className="flex h-screen bg-gray-50">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Navbar />
        <main className="flex-1 overflow-y-auto p-6">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/live-monitoring" element={<LiveMonitoring />} />
            <Route path="/exam-management" element={<ExamManagement />} />
            <Route path="/incidents" element={<IncidentReports />} />
            <Route path="/statistics" element={<Statistics />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </main>
      </div>
    </div>
  )
}

export default App
