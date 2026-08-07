import { Bar } from 'react-chartjs-2'

export default function RoomComparison({ data = [] }) {
  const chartData = {
    labels: data.map((r) => r.name || `Room ${r.room_number}`),
    datasets: [
      {
        label: 'Detections',
        data: data.map((r) => r.detections || 0),
        backgroundColor: ['#1a73e8', '#ff9800', '#4caf50', '#d32f2f', '#9c27b0', '#00bcd4'],
        borderRadius: 6,
      },
    ],
  }

  const options = {
    responsive: true,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: '#1e1e1e',
        padding: 12,
        cornerRadius: 8,
      },
    },
    scales: {
      y: {
        beginAtZero: true,
        ticks: { stepSize: 1, precision: 0 },
        grid: { color: 'rgba(0,0,0,0.05)' },
      },
      x: {
        grid: { display: false },
      },
    },
  }

  return <Bar data={chartData} options={options} />
}
