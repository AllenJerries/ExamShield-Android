import { formatDate, getRiskColor } from '../utils/helpers'

export default function IncidentCard({ incident, onResolve }) {
  return (
    <div className="card p-5">
      <div className="flex items-start justify-between mb-3">
        <div>
          <h3 className="font-semibold text-gray-800">
            {incident.description || 'Device Detected'}
          </h3>
          <p className="text-sm text-gray-500 mt-1">
            {formatDate(incident.timestamp)}
          </p>
        </div>
        <span className={`px-3 py-1 rounded-full text-xs font-medium ${getRiskColor(incident.severity)}`}>
          {incident.severity}
        </span>
      </div>

      <div className="space-y-2 text-sm">
        {incident.evidence_notes && (
          <p className="text-gray-600">
            <span className="font-medium text-gray-700">Evidence:</span> {incident.evidence_notes}
          </p>
        )}
      </div>

      <div className="flex items-center justify-between mt-4 pt-3 border-t border-gray-100">
        <div className="flex items-center space-x-2">
          <div className={`w-2 h-2 rounded-full ${incident.resolved ? 'bg-green-500' : 'bg-yellow-500'}`}></div>
          <span className="text-xs text-gray-500">
            {incident.resolved ? 'Resolved' : 'Unresolved'}
          </span>
        </div>
        {!incident.resolved && (
          <button
            onClick={() => onResolve?.(incident.id)}
            className="btn-primary text-xs"
          >
            Mark Resolved
          </button>
        )}
      </div>
    </div>
  )
}
