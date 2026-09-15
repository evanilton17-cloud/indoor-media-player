import mongoose from 'mongoose';

const scheduleSchema = new mongoose.Schema(
  {
    ownerId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', index: true },
    deviceIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Device' }],
    playlistId: { type: mongoose.Schema.Types.ObjectId, ref: 'Playlist' },
    name: { type: String, required: true },
    rules: {
      daysOfWeek: [Number],
      startTime: String,
      endTime: String,
      startDate: Date,
      endDate: Date
    },
    priority: { type: Number, default: 1 },
    active: { type: Boolean, default: true }
  },
  { timestamps: true }
);

export default mongoose.model('Schedule', scheduleSchema);