import { useState } from 'react'
import { useQuery } from 'react-query'
import { examsApi, detectionsApi, dashboardApi } from '../api/examshield'
import RoomCard from '../components/RoomCard'
import DeviceTable from '../components/DeviceTable'

export default function LiveMonitoring() {
  const [selectedRoom, setSelectedRoom] = useState(null)
  const [selectedExamId, setSelectedExamId] = useState(null)

  const { data: dashboard } = useQuery('dashboard', dashboardApi.getDashboard, {
    refetchInterval: 10000,
  })

  const { data: activeExams } = useQuery(
    ['exams', 'active'],
    () => examsApi.getAll({ status: 'active' }),
    { refetchInterval: 15000 }
  )

  const { data: detections, refetch: refetchDetections } = useQuery(
    ['detections', selectedExamId],
    () => detectionsApi.getForExam(selectedExamId),
    { enabled: !!selectedExamId, refetchInterval: 5000 }
  )

  const handleRoomClick = (room) => {
    setSelectedRoom(room)
    if (room.exam_id) {
      setSelectedExamId(room.exam_id)
    }
  }

  const handleUpdateAction = async (deviceId, action) => {
    try {
      await detectionsApi.updateAction(deviceId, action, action === 'found')
      refetchDetections()
    } catch (err) {
      console.error('Failed to update action', err)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-800">Live Monitoring</h1>
        <div className="flex items-center space-x-2">
          <div className="w-3 h-3 bg-green-500 rounded-full animate-pulse"></div>
          <span className="text-sm text-gray-500">System Active</span>
        </div>
      </div>

      {dashboard?.recent_alerts?.length > 0 && (
        <div className="bg-danger-50 border border-danger-200 rounded-xl p-4">
          <div className="flex items-center space-x-2">
            <span className="text-danger-500 font-bold text-lg">!</span>
            <p className="text-danger-700 font-medium">
              {dashboard.recent_alerts.length} recent unauthorized device(s) detected
            </p>
          </div>
        </div>
      )}

      <div>
        <h2 className="text-lg font-semibold text-gray-800 mb-4">Active Exam Rooms</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {activeExams?.exams?.map((exam) => (
            <RoomCard
              key={exam.id}
              room={{
                exam_id: exam.id,
                room_name: exam.exam_name,
                room_number: '',
                exam_name: exam.exam_name,
                total_detections: 0,
                high_risk_count: 0,
                invigilator_name: exam.invigilator_name,
                last_detection: null,
              }}
              onClick={handleRoomClick}
            />
          ))}
          {(!activeExams?.exams || activeExams.exams.length === 0) && (
            <div className="col-span-full card p-8 text-center text-gray-400">
              <p className="text-3xl mb-2">🏛️</p>
              <p className="font-medium">No active exam sessions</p>
              <p className="text-sm">Start an exam from Exam Management</p>
            </div>
          )}
        </div>
      </div>

      {selectedExamId && (
        <div>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-800">
              Detections - {selectedRoom?.exam_name || `Exam #${selectedExamId}`}
            </h2>
            <button
              onClick={() => { setSelectedRoom(null); setSelectedExamId(null) }}
              className="text-sm text-gray-400 hover:text-gray-600"
            >
              Close
            </button>
          </div>
          <DeviceTable
            devices={detections?.detections || []}
            onUpdateAction={handleUpdateAction}
          />
        </div>
      )}
    </div>
  )
}
