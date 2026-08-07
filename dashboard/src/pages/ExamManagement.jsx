import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from 'react-query'
import { examsApi, roomsApi } from '../api/examshield'
import { formatDate, getStatusColor } from '../utils/helpers'

export default function ExamManagement() {
  const queryClient = useQueryClient()
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [filter, setFilter] = useState('all')
  const [form, setForm] = useState({
    room_id: '',
    exam_name: '',
    exam_date: '',
    invigilator_name: '',
  })

  const { data: exams } = useQuery(['exams', filter], () =>
    examsApi.getAll(filter === 'all' ? {} : { status: filter })
  )
  const { data: rooms } = useQuery('rooms', roomsApi.getAll)

  const createMutation = useMutation(examsApi.create, {
    onSuccess: () => {
      queryClient.invalidateQueries('exams')
      setShowCreateForm(false)
      setForm({ room_id: '', exam_name: '', exam_date: '', invigilator_name: '' })
    },
  })

  const endExamMutation = useMutation(examsApi.endExam, {
    onSuccess: () => queryClient.invalidateQueries('exams'),
  })

  const deleteMutation = useMutation(examsApi.delete, {
    onSuccess: () => queryClient.invalidateQueries('exams'),
  })

  const handleCreate = (e) => {
    e.preventDefault()
    createMutation.mutate({
      ...form,
      room_id: parseInt(form.room_id),
    })
  }

  const handleEndExam = (examId) => {
    if (window.confirm('End this exam session?')) {
      endExamMutation.mutate(examId)
    }
  }

  const handleDelete = (examId) => {
    if (window.confirm('Delete this exam record?')) {
      deleteMutation.mutate(examId)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-800">Exam Management</h1>
        <button onClick={() => setShowCreateForm(!showCreateForm)} className="btn-primary">
          {showCreateForm ? 'Cancel' : '+ New Exam'}
        </button>
      </div>

      {showCreateForm && (
        <div className="card p-6">
          <h2 className="text-lg font-semibold text-gray-800 mb-4">Create New Exam Session</h2>
          <form onSubmit={handleCreate} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Room</label>
                <select
                  value={form.room_id}
                  onChange={(e) => setForm({ ...form, room_id: e.target.value })}
                  className="input-field"
                  required
                >
                  <option value="">Select Room</option>
                  {rooms?.rooms?.map((room) => (
                    <option key={room.id} value={room.id}>
                      {room.name} ({room.room_number})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Exam Name</label>
                <input
                  type="text"
                  value={form.exam_name}
                  onChange={(e) => setForm({ ...form, exam_name: e.target.value })}
                  className="input-field"
                  placeholder="e.g. Mathematics 101"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Exam Date</label>
                <input
                  type="date"
                  value={form.exam_date}
                  onChange={(e) => setForm({ ...form, exam_date: e.target.value })}
                  className="input-field"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Invigilator</label>
                <input
                  type="text"
                  value={form.invigilator_name}
                  onChange={(e) => setForm({ ...form, invigilator_name: e.target.value })}
                  className="input-field"
                  placeholder="Invigilator name"
                />
              </div>
            </div>
            <button type="submit" className="btn-primary" disabled={createMutation.isLoading}>
              {createMutation.isLoading ? 'Creating...' : 'Start Exam'}
            </button>
          </form>
        </div>
      )}

      <div className="flex space-x-2">
        {['all', 'active', 'completed', 'cancelled'].map((s) => (
          <button
            key={s}
            onClick={() => setFilter(s)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              filter === s
                ? 'bg-primary-500 text-white'
                : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
            }`}
          >
            {s.charAt(0).toUpperCase() + s.slice(1)}
          </button>
        ))}
      </div>

      <div className="space-y-4">
        {exams?.exams?.map((exam) => (
          <div key={exam.id} className="card p-5">
            <div className="flex items-center justify-between mb-3">
              <div>
                <h3 className="font-semibold text-gray-800 text-lg">{exam.exam_name}</h3>
                <p className="text-sm text-gray-500">Room #{exam.room_id}</p>
              </div>
              <span className={`px-3 py-1 rounded-full text-xs font-medium ${getStatusColor(exam.status)}`}>
                {exam.status}
              </span>
            </div>
            <div className="grid grid-cols-3 gap-4 text-sm text-gray-600 mb-4">
              <div>
                <span className="font-medium text-gray-700">Date:</span> {exam.exam_date || 'N/A'}
              </div>
              <div>
                <span className="font-medium text-gray-700">Start:</span> {exam.start_time ? formatDate(exam.start_time) : 'N/A'}
              </div>
              <div>
                <span className="font-medium text-gray-700">Invigilator:</span> {exam.invigilator_name || 'N/A'}
              </div>
            </div>
            <div className="flex space-x-2">
              {exam.status === 'active' && (
                <button onClick={() => handleEndExam(exam.id)} className="btn-danger text-sm">
                  End Exam
                </button>
              )}
              <button onClick={() => handleDelete(exam.id)} className="text-sm text-gray-400 hover:text-danger-500">
                Delete
              </button>
            </div>
          </div>
        ))}
        {(!exams?.exams || exams.exams.length === 0) && (
          <div className="card p-8 text-center text-gray-400">
            <p className="text-3xl mb-2">📋</p>
            <p className="font-medium">No exams found</p>
            <p className="text-sm">Create a new exam to get started</p>
          </div>
        )}
      </div>
    </div>
  )
}
