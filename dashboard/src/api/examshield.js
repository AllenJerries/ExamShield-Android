import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
})

export const dashboardApi = {
  getDashboard: () => api.get('/dashboard').then(r => r.data),
  getStatistics: () => api.get('/statistics').then(r => r.data),
}

export const roomsApi = {
  getAll: () => api.get('/rooms').then(r => r.data),
  getById: (id) => api.get(`/rooms/${id}`).then(r => r.data),
  create: (data) => api.post('/rooms', data).then(r => r.data),
  delete: (id) => api.delete(`/rooms/${id}`).then(r => r.data),
}

export const examsApi = {
  getAll: (params) => api.get('/exams', { params }).then(r => r.data),
  getById: (id) => api.get(`/exams/${id}`).then(r => r.data),
  create: (data) => api.post('/exams', data).then(r => r.data),
  endExam: (id) => api.put(`/exams/${id}/end`).then(r => r.data),
  delete: (id) => api.delete(`/exams/${id}`).then(r => r.data),
}

export const detectionsApi = {
  getForExam: (examId, params) => api.get(`/detections/${examId}`, { params }).then(r => r.data),
  create: (data) => api.post('/detections', data).then(r => r.data),
  updateAction: (id, action, confirmed) =>
    api.put(`/detections/${id}/action`, null, { params: { action_taken: action, confirmed_cheating: confirmed } })
    .then(r => r.data),
}

export const whitelistApi = {
  getForRoom: (roomId) => api.get(`/whitelist/${roomId}`).then(r => r.data),
  add: (data) => api.post('/whitelist', data).then(r => r.data),
  remove: (id) => api.delete(`/whitelist/${id}`).then(r => r.data),
  bulkAdd: (devices) => api.post('/whitelist/bulk', devices).then(r => r.data),
}

export const incidentsApi = {
  getForExam: (examId, params) => api.get(`/incidents/${examId}`, { params }).then(r => r.data),
  create: (data) => api.post('/incidents', data).then(r => r.data),
  resolve: (id) => api.put(`/incidents/${id}/resolve`).then(r => r.data),
}

export const reportsApi = {
  generate: (examId) => api.post(`/reports/generate/${examId}`, {}, { responseType: 'blob' }).then(r => r.data),
}

export default api
