export function formatDate(dateString) {
  if (!dateString) return 'N/A'
  const date = new Date(dateString)
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatTime(dateString) {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function getRiskColor(level) {
  switch (level?.toLowerCase()) {
    case 'high': return 'text-danger-500 bg-danger-50'
    case 'medium': return 'text-yellow-600 bg-yellow-50'
    case 'low': return 'text-green-600 bg-green-50'
    default: return 'text-gray-600 bg-gray-50'
  }
}

export function getStatusColor(status) {
  switch (status?.toLowerCase()) {
    case 'active': return 'text-green-600 bg-green-50'
    case 'completed': return 'text-blue-600 bg-blue-50'
    case 'cancelled': return 'text-red-600 bg-red-50'
    default: return 'text-gray-600 bg-gray-50'
  }
}

export function getDeviceIcon(type) {
  switch (type?.toLowerCase()) {
    case 'earphone': return '🎧'
    case 'watch': return '⌚'
    case 'phone': return '📱'
    case 'wifi_device': return '📶'
    default: return '📡'
  }
}

export function downloadPDF(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  window.URL.revokeObjectURL(url)
  document.body.removeChild(a)
}
