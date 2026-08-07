import { NavLink } from 'react-router-dom'

const navItems = [
  { path: '/', label: 'Dashboard', icon: '📊' },
  { path: '/live-monitoring', label: 'Live Monitoring', icon: '📡' },
  { path: '/exam-management', label: 'Exam Management', icon: '📋' },
  { path: '/incidents', label: 'Incident Reports', icon: '⚠️' },
  { path: '/statistics', label: 'Statistics', icon: '📈' },
  { path: '/settings', label: 'Settings', icon: '⚙️' },
]

export default function Sidebar() {
  return (
    <aside className="w-64 bg-white border-r border-gray-200 flex flex-col">
      <div className="p-6 border-b border-gray-200">
        <h1 className="text-2xl font-bold text-primary-500">ExamShield</h1>
        <p className="text-xs text-gray-500 mt-1">Detection Dashboard</p>
      </div>
      <nav className="flex-1 p-4 space-y-1">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `flex items-center space-x-3 px-4 py-3 rounded-lg transition-colors ${
                isActive
                  ? 'bg-primary-50 text-primary-600 font-medium'
                  : 'text-gray-600 hover:bg-gray-50'
              }`
            }
            end={item.path === '/'}
          >
            <span className="text-lg">{item.icon}</span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="p-4 border-t border-gray-200">
        <div className="flex items-center space-x-3">
          <div className="w-8 h-8 bg-primary-500 rounded-full flex items-center justify-center text-white text-sm font-medium">
            ES
          </div>
          <div>
            <p className="text-sm font-medium text-gray-700">ExamShield v1.0</p>
            <p className="text-xs text-gray-400">System Online</p>
          </div>
        </div>
      </div>
    </aside>
  )
}
