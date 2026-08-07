import { Pie } from 'react-chartjs-2'

const COLORS = ['#d32f2f', '#ff9800', '#1a73e8', '#4caf50', '#9c27b0', '#00bcd4', '#607d8b']

export default function DeviceTypeChart({ data = [] }) {
  const chartData = {
    labels: data.map((d) => d.type || d.device_type || 'Unknown'),
    datasets: [
      {
        data: data.map((d) => d.count || 0),
        backgroundColor: COLORS.slice(0, data.length),
        borderWidth: 2,
        borderColor: '#ffffff',
      },
    ],
  }

  const options = {
    responsive: true,
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          padding: 20,
          usePointStyle: true,
        },
      },
      tooltip: {
        callbacks: {
          label: function (context) {
            const total = context.dataset.data.reduce((a, b) => a + b, 0)
            const value = context.parsed
            const pct = ((value / total) * 100).toFixed(1)
            return `${context.label}: ${value} (${pct}%)`
          },
        },
      },
    },
  }

  return <Pie data={chartData} options={options} />
}
