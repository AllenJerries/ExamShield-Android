import { getRiskColor, formatDate, getDeviceIcon } from '../utils/helpers'

export default function DeviceTable({ devices = [], onUpdateAction }) {
  if (devices.length === 0) {
    return (
      <div className="card p-8 text-center text-gray-400">
        <p className="text-3xl mb-2">📡</p>
        <p className="font-medium">No devices detected</p>
        <p className="text-sm">This room is clean</p>
      </div>
    )
  }

  return (
    <div className="card overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-200">
              <th className="text-left px-4 py-3 font-medium text-gray-600">Device</th>
              <th className="text-left px-4 py-3 font-medium text-gray-600">Type</th>
              <th className="text-left px-4 py-3 font-medium text-gray-600">Risk</th>
              <th className="text-left px-4 py-3 font-medium text-gray-600">RSSI</th>
              <th className="text-left px-4 py-3 font-medium text-gray-600">Time</th>
              <th className="text-left px-4 py-3 font-medium text-gray-600">Action</th>
              <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {devices.map((device) => (
              <tr key={device.id} className="hover:bg-gray-50">
                <td className="px-4 py-3">
                  <span className="mr-2">{getDeviceIcon(device.device_type)}</span>
                  {device.device_name || 'Unknown'}
                </td>
                <td className="px-4 py-3 capitalize">{device.device_type}</td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-1 rounded-full text-xs font-medium ${getRiskColor(device.risk_level)}`}>
                    {device.risk_level}
                  </span>
                </td>
                <td className="px-4 py-3">{device.rssi ?? 'N/A'}</td>
                <td className="px-4 py-3 text-gray-500">{formatDate(device.detected_at)}</td>
                <td className="px-4 py-3">
                  <select
                    value={device.action_taken || 'pending'}
                    onChange={(e) => onUpdateAction?.(device.id, e.target.value)}
                    className="text-xs border border-gray-200 rounded px-2 py-1"
                  >
                    <option value="pending">Pending</option>
                    <option value="investigating">Investigating</option>
                    <option value="found">Found</option>
                    <option value="false_alarm">False Alarm</option>
                  </select>
                </td>
                <td className="px-4 py-3">
                  {device.confirmed_cheating ? (
                    <span className="text-danger-600 font-medium">Confirmed</span>
                  ) : (
                    <span className="text-gray-400">Pending</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
