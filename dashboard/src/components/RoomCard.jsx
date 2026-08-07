export default function RoomCard({ room, onClick }) {
  const hasAlerts = room.high_risk_count > 0

  return (
    <div
      className={`card p-5 cursor-pointer transition-all hover:shadow-md ${
        hasAlerts ? 'border-danger-200' : ''
      }`}
      onClick={() => onClick?.(room)}
    >
      <div className="flex items-center justify-between mb-3">
        <div>
          <h3 className="font-semibold text-gray-800">{room.room_name}</h3>
          <p className="text-sm text-gray-500">Room {room.room_number}</p>
        </div>
        <div className={`px-3 py-1 rounded-full text-xs font-medium ${
          hasAlerts
            ? 'bg-danger-50 text-danger-600'
            : 'bg-green-50 text-green-600'
        }`}>
          {hasAlerts ? 'ALERT' : 'CLEAN'}
        </div>
      </div>
      <p className="text-sm text-gray-600 mb-3">{room.exam_name}</p>
      <div className="flex items-center justify-between text-xs text-gray-400">
        <span>Detections: {room.total_detections}</span>
        <span>Invigilator: {room.invigilator_name}</span>
      </div>
      {room.last_detection && (
        <p className="text-xs text-gray-400 mt-1">
          Last scan: {new Date(room.last_detection).toLocaleTimeString()}
        </p>
      )}
    </div>
  )
}
