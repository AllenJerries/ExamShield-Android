import { useState } from 'react'
import { useQuery, useQueryClient } from 'react-query'
import { incidentsApi, reportsApi, examsApi } from '../api/examshield'
import IncidentCard from '../components/IncidentCard'
import { downloadPDF } from '../utils/helpers'

export default function IncidentReports() {
  const queryClient = useQueryClient()
  const [selectedExam, setSelectedExam] = useState(null)
  const [severityFilter, setSeverityFilter] = useState(null)
  const [exporting, setExporting] = useState(false)

  const { data: exams } = useQuery('exams', () => examsApi.getAll())

  const { data: incidents, refetch } = useQuery(
    ['incidents', selectedExam, severityFilter],
    () => incidentsApi.getForExam(selectedExam, severityFilter ? { severity: severityFilter } : {}),
    { enabled: !!selectedExam }
  )

  const handleResolve = async (incidentId) => {
    try {
      await incidentsApi.resolve(incidentId)
      refetch()
    } catch (err) {
      console.error('Failed to resolve', err)
    }
  }

  const handleExportPDF = async () => {
    if (!selectedExam) return
    setExporting(true)
    try {
      const blob = await reportsApi.generate(selectedExam)
      downloadPDF(blob, `exam_report_${selectedExam}.pdf`)
    } catch (err) {
      console.error('Export failed', err)
    }
    setExporting(false)
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-800">Incident Reports</h1>
        <button
          onClick={handleExportPDF}
          disabled={!selectedExam || exporting}
          className="btn-primary"
        >
          {exporting ? 'Exporting...' : '📄 Export PDF'}
        </button>
      </div>

      <div className="card p-5">
        <label className="block text-sm font-medium text-gray-700 mb-2">Select Exam</label>
        <select
          value={selectedExam || ''}
          onChange={(e) => setSelectedExam(e.target.value ? parseInt(e.target.value) : null)}
          className="input-field"
        >
          <option value="">Choose an exam...</option>
          {exams?.exams?.map((exam) => (
            <option key={exam.id} value={exam.id}>
              {exam.exam_name} - {exam.exam_date}
            </option>
          ))}
        </select>
      </div>

      <div className="flex space-x-2">
        {[null, 'critical', 'high', 'medium', 'low'].map((s) => (
          <button
            key={s || 'all'}
            onClick={() => setSeverityFilter(s)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              severityFilter === s
                ? 'bg-primary-500 text-white'
                : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
            }`}
          >
            {s ? s.charAt(0).toUpperCase() + s.slice(1) : 'All'}
          </button>
        ))}
      </div>

      {incidents?.incidents?.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {incidents.incidents.map((incident) => (
            <IncidentCard key={incident.id} incident={incident} onResolve={handleResolve} />
          ))}
        </div>
      ) : (
        <div className="card p-8 text-center text-gray-400">
          <p className="text-3xl mb-2">✅</p>
          <p className="font-medium">No incidents found</p>
          <p className="text-sm">
            {selectedExam ? 'This exam has no recorded incidents' : 'Select an exam to view incidents'}
          </p>
        </div>
      )}
    </div>
  )
}
