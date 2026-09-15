import mongoose from 'mongoose';

const deviceSchema = new mongoose.Schema(
  {
    ownerId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', index: true },
    paired: { type: Boolean, default: false },
    pairingCode: { type: String, index: true },
    pairingCodeExpires: Date,
    name: { type: String, default: 'Nova TV' },
    uniqueId: { type: String, unique: true, index: true },
    model: String,
    androidVersion: String,
    appVersion: String,
    ipAddress: String,
    location: String,
    status: {
      online: { type: Boolean, default: false },
      lastSeen: Date,
      currentContent: String,
      currentZone: String,
      itemCount: { type: Number, default: 0 },
      volume: { type: Number, default: 100 },
      power: { type: Boolean, default: true }
    },
    settings: {
      layoutId: String,
      rebootOnError: { type: Boolean, default: true },
      screenOn: { type: Boolean, default: true },
      orientation: { type: String, enum: ['landscape', 'portrait'], default: 'landscape' },
      cacheSizeMb: { type: Number, default: 1000 }
    },
    lastSyncAt: Date
  },
  { timestamps: true }
);

export default mongoose.model('Device', deviceSchema);