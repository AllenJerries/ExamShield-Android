export default function AlertFeed({ alerts = [] }) {
  if (alerts.length === 0) {
    return (
      <div className="card p-6 text-center text-gray-400">
        <p className="text-2xl mb-2">✅</p>
        <p className="font-medium">No recent alerts</p>
        <p className="text-sm">All rooms are clear</p>
      </div>
    )
  }

  return (
    <div className="card divide-y divide-gray-100">
      <div className="px-5 py-3 font-medium text-gray-700 border-b border-gray-100">
        Recent Alerts
      </div>
      {alerts.slice(0, 10).map((alert, index) => (
        <div key={alert.id || index} className="px-5 py-3 flex items-center space-x-3">
          <div className={`w-2 h-2 rounded-full ${
            alert.risk_level === 'high' ? 'bg-danger-500' :
            alert.risk_level === 'medium' ? 'bg-yellow-500' : 'bg-blue-500'
          }`}></div>
          <div className="flex-1">
            <p className="text-sm font-medium text-gray-700">
              {alert.device_name || 'Unknown Device'}
            </p>
            <p className="text-xs text-gray-400">
              {alert.device_type} | RSSI: {alert.rssi}
            </p>
          </div>
          <span className={`text-xs px-2 py-1 rounded-full font-medium ${
            alert.risk_level === 'high' ? 'bg-danger-50 text-danger-600' :
            alert.risk_level === 'medium' ? 'bg-yellow-50 text-yellow-600' :
            'bg-blue-50 text-blue-600'
          }`}>
            {alert.risk_level?.toUpperCase()}
          </span>
          {alert.detected_at && (
            <span className="text-xs text-gray-400">
              {new Date(alert.detected_at).toLocaleTimeString()}
            </span>
          )}
        </div>
      ))}
    </div>
  )
}
