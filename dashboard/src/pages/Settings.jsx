import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from 'react-query'
import { roomsApi, whitelistApi } from '../api/examshield'

export default function Settings() {
  const queryClient = useQueryClient()
  const [showAddRoom, setShowAddRoom] = useState(false)
  const [newRoom, setNewRoom] = useState({ name: '', room_number: '', institution: '' })

  const { data: rooms } = useQuery('rooms', roomsApi.getAll)

  const createRoomMutation = useMutation(roomsApi.create, {
    onSuccess: () => {
      queryClient.invalidateQueries('rooms')
      setShowAddRoom(false)
      setNewRoom({ name: '', room_number: '', institution: '' })
    },
  })

  const deleteRoomMutation = useMutation(roomsApi.delete, {
    onSuccess: () => queryClient.invalidateQueries('rooms'),
  })

  const handleAddRoom = (e) => {
    e.preventDefault()
    createRoomMutation.mutate(newRoom)
  }

  const handleDeleteRoom = (roomId) => {
    if (window.confirm('Delete this room and all associated data?')) {
      deleteRoomMutation.mutate(roomId)
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-800">Settings</h1>

      <div className="card p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-gray-800">Institution Profile</h2>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Institution Name</label>
            <input type="text" className="input-field" placeholder="Enter institution name" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Location</label>
            <input type="text" className="input-field" placeholder="City, State" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Contact Email</label>
            <input type="email" className="input-field" placeholder="admin@institution.edu" />
          </div>
        </div>
      </div>

      <div className="card p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-gray-800">Manage Rooms</h2>
          <button onClick={() => setShowAddRoom(!showAddRoom)} className="btn-primary text-sm">
            {showAddRoom ? 'Cancel' : '+ Add Room'}
          </button>
        </div>

        {showAddRoom && (
          <form onSubmit={handleAddRoom} className="mb-6 p-4 bg-gray-50 rounded-lg space-y-3">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <input
                type="text"
                value={newRoom.name}
                onChange={(e) => setNewRoom({ ...newRoom, name: e.target.value })}
                className="input-field"
                placeholder="Hall name"
                required
              />
              <input
                type="text"
                value={newRoom.room_number}
                onChange={(e) => setNewRoom({ ...newRoom, room_number: e.target.value })}
                className="input-field"
                placeholder="Room number"
                required
              />
              <input
                type="text"
                value={newRoom.institution}
                onChange={(e) => setNewRoom({ ...newRoom, institution: e.target.value })}
                className="input-field"
                placeholder="Institution"
                required
              />
            </div>
            <button type="submit" className="btn-primary">
              Create Room
            </button>
          </form>
        )}

        <div className="space-y-2">
          {rooms?.rooms?.map((room) => (
            <div key={room.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
              <div>
                <p className="font-medium text-gray-700">{room.name}</p>
                <p className="text-sm text-gray-500">Room {room.room_number} - {room.institution}</p>
              </div>
              <button
                onClick={() => handleDeleteRoom(room.id)}
                className="text-sm text-danger-500 hover:text-danger-600"
              >
                Delete
              </button>
            </div>
          ))}
          {(!rooms?.rooms || rooms.rooms.length === 0) && (
            <p className="text-gray-400 text-center py-4">No rooms configured</p>
          )}
        </div>
      </div>

      <div className="card p-6">
        <h2 className="text-lg font-semibold text-gray-800 mb-4">Alert Thresholds</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">RSSI Alert Threshold</label>
            <input type="number" className="input-field" defaultValue="-85" />
            <p className="text-xs text-gray-400 mt-1">dBm threshold for triggering alerts</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Risk Level for Notification</label>
            <select className="input-field" defaultValue="medium">
              <option value="high">High Only</option>
              <option value="medium">Medium & High</option>
              <option value="low">All Levels</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Auto-Resolve Time</label>
            <select className="input-field" defaultValue="24">
              <option value="1">1 hour</option>
              <option value="6">6 hours</option>
              <option value="24">24 hours</option>
              <option value="72">72 hours</option>
              <option value="0">Never</option>
            </select>
          </div>
        </div>
      </div>

      <div className="card p-6">
        <h2 className="text-lg font-semibold text-gray-800 mb-4">Report Configuration</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Default Report Template</label>
            <select className="input-field" defaultValue="standard">
              <option value="standard">Standard</option>
              <option value="detailed">Detailed</option>
              <option value="summary">Summary Only</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Include in Reports</label>
            <div className="space-y-2 mt-2">
              <label className="flex items-center space-x-2">
                <input type="checkbox" defaultChecked className="rounded" />
                <span className="text-sm">Device MAC hashes</span>
              </label>
              <label className="flex items-center space-x-2">
                <input type="checkbox" defaultChecked className="rounded" />
                <span className="text-sm">RSSI values</span>
              </label>
              <label className="flex items-center space-x-2">
                <input type="checkbox" defaultChecked className="rounded" />
                <span className="text-sm">Invigilator notes</span>
              </label>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
