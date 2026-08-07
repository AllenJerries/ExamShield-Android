import { Line } from 'react-chartjs-2'

export default function DetectionTrend({ data = [] }) {
  const chartData = {
    labels: data.map((d) => d.date || ''),
    datasets: [
      {
        label: 'Detections',
        data: data.map((d) => d.count || 0),
        borderColor: '#1a73e8',
        backgroundColor: 'rgba(26, 115, 232, 0.1)',
        fill: true,
        tension: 0.4,
        pointBackgroundColor: '#1a73e8',
        pointRadius: 4,
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

  return <Line data={chartData} options={options} />
}
