export default function StatCard({ title, value, icon, color = 'primary', subtitle, onClick }) {
  const colorClasses = {
    primary: 'text-primary-500 bg-primary-50',
    danger: 'text-danger-500 bg-danger-50',
    warning: 'text-yellow-500 bg-yellow-50',
    success: 'text-green-500 bg-green-50',
  }

  return (
    <div
      className="stat-card cursor-pointer"
      onClick={onClick}
    >
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-500 font-medium">{title}</p>
          <p className="text-3xl font-bold text-gray-800 mt-2">{value}</p>
          {subtitle && (
            <p className="text-xs text-gray-400 mt-1">{subtitle}</p>
          )}
        </div>
        <div className={`w-12 h-12 rounded-lg ${colorClasses[color] || colorClasses.primary} flex items-center justify-center text-xl`}>
          {icon}
        </div>
      </div>
    </div>
  )
}
